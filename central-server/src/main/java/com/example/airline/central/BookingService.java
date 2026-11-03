package com.example.airline.central;

import com.example.airline.core.AirlineGateway;
import com.example.airline.core.BookingCoordinator;
import com.example.airline.core.BookingException;
import com.example.airline.model.AirlineInfo;
import com.example.airline.model.BookedLeg;
import com.example.airline.model.BookingRequest;
import com.example.airline.model.BookingStatus;
import com.example.airline.model.Receipt;
import com.example.airline.model.RegisterBookingRequest;
import com.example.airline.model.ReservationDetails;
import com.example.airline.model.ReservationStatus;
import com.example.airline.model.ReservedSegment;
import com.example.airline.model.SeatPreference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private final AppProperties properties;
    private final BookingCoordinator coordinator;
    private final BookingRepository repository;
    private final AirlineGateway gateway;

    public BookingService(
            AppProperties properties,
            BookingCoordinator coordinator,
            BookingRepository repository,
            AirlineGateway gateway
    ) {
        this.properties = properties;
        this.coordinator = coordinator;
        this.repository = repository;
        this.gateway = gateway;
    }

    public Receipt book(BookingRequest request) {
        requireIdempotency(request == null ? null : request.idempotencyKey());
        return repository.findByIdempotencyKey(request.idempotencyKey())
                .orElseGet(() -> {
                    Receipt receipt;
                    try {
                        receipt = coordinator.coordinate(request, properties.airlines());
                    } catch (BookingException exception) {
                        throw new CentralServiceException(HttpStatus.CONFLICT, "BOOKING_FAILED", exception.getMessage());
                    }
                    try {
                        repository.save(receipt, request.idempotencyKey());
                        return receipt;
                    } catch (RuntimeException exception) {
                        coordinator.cancel(receipt.bookingReference(), receipt.legs(), properties.airlines());
                        throw new CentralServiceException(HttpStatus.INTERNAL_SERVER_ERROR,
                                "RECEIPT_PERSIST_FAILED", "收据保存失败，已尝试释放航段");
                    }
                });
    }

    public Receipt get(String bookingReference) {
        return repository.find(bookingReference).orElseThrow(() -> new CentralServiceException(
                HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "预订不存在：" + bookingReference));
    }

    public Receipt cancel(String bookingReference) {
        Receipt existing = get(bookingReference);
        if (existing.status() == BookingStatus.CANCELLED) {
            return existing.withStatus(BookingStatus.CANCELLED, "预订已取消");
        }
        List<String> failures = coordinator.cancel(bookingReference, existing.legs(), properties.airlines());
        if (!failures.isEmpty()) {
            repository.updateStatus(bookingReference, BookingStatus.CANCEL_PENDING,
                    "部分航段取消失败：" + String.join("; ", failures));
            throw new CentralServiceException(HttpStatus.BAD_GATEWAY, "PARTIAL_CANCELLATION",
                    "部分航段取消失败，已标记为 CANCEL_PENDING：" + String.join("; ", failures));
        }
        repository.updateStatus(bookingReference, BookingStatus.CANCELLED, "预订已取消");
        return get(bookingReference);
    }

    public Receipt registerFromThickClient(RegisterBookingRequest request) {
        if (request == null || blank(request.bookingReference())) {
            throw new CentralServiceException(HttpStatus.BAD_REQUEST, "INVALID_BOOKING", "bookingReference 不能为空");
        }
        if (request.passengers().isEmpty() || request.passengers().stream()
                .anyMatch(p -> p.seatPreference() == null || p.seatPreference() == SeatPreference.MIXED)) {
            throw new CentralServiceException(HttpStatus.BAD_REQUEST, "INVALID_BOOKING", "乘客和各自座位偏好不能为空");
        }
        String idempotencyKey = "external:" + request.bookingReference();
        return repository.findByIdempotencyKey(idempotencyKey).orElseGet(() -> {
            List<BookedLeg> authoritativeLegs = new ArrayList<>();
            int passengerCount = request.passengers().size();

            for (AirlineInfo airline : properties.airlines()) {
                ReservationDetails details = gateway.reservationDetails(airline, request.bookingReference());
                if ("NOT_FOUND".equals(details.status())) {
                    continue;
                }
                if (!"CONFIRMED".equals(details.status())) {
                    throw new CentralServiceException(HttpStatus.CONFLICT, "AIRLINE_NOT_CONFIRMED",
                            airline.code() + " 的预订状态不是 CONFIRMED：" + details.status());
                }
                for (ReservedSegment segment : details.segments()) {
                    if (!"CONFIRMED".equals(segment.status())) {
                        throw new CentralServiceException(HttpStatus.CONFLICT, "AIRLINE_SEGMENT_NOT_CONFIRMED",
                                airline.code() + " 的航段未确认：" + segment.flightNumber());
                    }
                    if (segment.passengers() != passengerCount) {
                        throw new CentralServiceException(HttpStatus.CONFLICT, "PASSENGER_COUNT_MISMATCH",
                                "航司记录的乘客数与请求不一致：" + segment.flightNumber());
                    }
                    authoritativeLegs.add(new BookedLeg(
                            segment.airlineCode(),
                            segment.flightNumber(),
                            segment.flightInstanceId(),
                            segment.origin(),
                            segment.destination(),
                            segment.departureTime(),
                            segment.arrivalTime(),
                            segment.fare(),
                            segment.holdId(),
                            segment.status()
                    ));
                }
            }

            if (authoritativeLegs.isEmpty()) {
                throw new CentralServiceException(HttpStatus.NOT_FOUND, "AIRLINE_BOOKING_NOT_FOUND",
                        "航司侧没有找到该预订，不能登记收据");
            }
            authoritativeLegs.sort(Comparator.comparing(BookedLeg::departureTime));

            boolean sameAirline = authoritativeLegs.stream().map(BookedLeg::airlineCode).distinct().count() == 1;
            int discount = sameAirline
                    ? properties.airlines().stream()
                    .filter(a -> a.code().equals(authoritativeLegs.get(0).airlineCode()))
                    .mapToInt(AirlineInfo::ownDiscountPercent)
                    .findFirst().orElse(0)
                    : 0;
            BigDecimal pricePerPassenger = authoritativeLegs.stream().map(BookedLeg::fare)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .multiply(BigDecimal.valueOf(100L - discount))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal total = pricePerPassenger.multiply(BigDecimal.valueOf(passengerCount))
                    .setScale(2, RoundingMode.HALF_UP);
            Receipt receipt = new Receipt(
                    request.bookingReference(),
                    BookingStatus.CONFIRMED,
                    authoritativeLegs,
                    request.passengers(),
                    total,
                    sameAirline,
                    sameAirline ? "同一航司 OwnDiscount " + discount + "%" : "跨航司行程，不应用 OwnDiscount",
                    Instant.now(),
                    "厚客户端预订已由航司权威明细登记"
            );
            repository.save(receipt, idempotencyKey);
            return receipt;
        });
    }

    public Receipt markCancelledByThickClient(String bookingReference) {
        Receipt existing = get(bookingReference);
        for (String airlineCode : distinctAirlines(existing.legs())) {
            AirlineInfo airline = airline(airlineCode);
            ReservationStatus status = gateway.reservation(airline, bookingReference);
            if (!"CANCELLED".equals(status.status()) && !"NOT_FOUND".equals(status.status())) {
                throw new CentralServiceException(HttpStatus.CONFLICT, "AIRLINE_NOT_CANCELLED",
                        airlineCode + " 仍未取消，当前状态：" + status.status());
            }
        }
        repository.updateStatus(bookingReference, BookingStatus.CANCELLED, "厚客户端取消已登记");
        return get(bookingReference);
    }

    private Set<String> distinctAirlines(List<BookedLeg> legs) {
        return legs.stream().map(BookedLeg::airlineCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private AirlineInfo airline(String code) {
        return properties.airlines().stream()
                .filter(a -> a.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new CentralServiceException(
                        HttpStatus.BAD_REQUEST, "AIRLINE_NOT_CONFIGURED", "未配置航司：" + code));
    }

    private void requireIdempotency(String key) {
        if (blank(key)) {
            throw new CentralServiceException(HttpStatus.BAD_REQUEST, "MISSING_IDEMPOTENCY_KEY",
                    "请求必须提供 idempotencyKey");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
