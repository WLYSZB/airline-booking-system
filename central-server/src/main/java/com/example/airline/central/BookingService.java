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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private final AppProperties properties;
    private final BookingCoordinator coordinator;
    private final BookingRepository repository;
    private final AirlineGateway gateway;
    private final IdempotencyRepository idempotencyRepository;
    private final BookingPersistenceService persistenceService;

    public BookingService(
            AppProperties properties,
            BookingCoordinator coordinator,
            BookingRepository repository,
            AirlineGateway gateway,
            IdempotencyRepository idempotencyRepository,
            BookingPersistenceService persistenceService
    ) {
        this.properties = properties;
        this.coordinator = coordinator;
        this.repository = repository;
        this.gateway = gateway;
        this.idempotencyRepository = idempotencyRepository;
        this.persistenceService = persistenceService;
    }

    public Receipt book(BookingRequest request) {
        requireIdempotency(request == null ? null : request.idempotencyKey());
        if (request.trip() == null || request.trip().legs() == null || request.trip().legs().isEmpty()
                || request.passengers() == null) {
            throw new CentralServiceException(HttpStatus.BAD_REQUEST, "INVALID_BOOKING", "行程和乘客不能为空");
        }
        String key = request.idempotencyKey();
        String fingerprint = fingerprint(request);
        IdempotencyRepository.ClaimResult claim = idempotencyRepository.claim(key, fingerprint);
        if (claim.status() == IdempotencyRepository.ClaimStatus.COMPLETED) {
            return repository.find(claim.bookingReference()).orElseThrow(() -> new CentralServiceException(
                    HttpStatus.CONFLICT, "IDEMPOTENCY_INCONSISTENT", "幂等请求已完成，但收据不存在"));
        }
        if (claim.status() == IdempotencyRepository.ClaimStatus.CONFLICT) {
            throw new CentralServiceException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "同一 idempotencyKey 对应了不同的预订内容");
        }
        if (claim.status() == IdempotencyRepository.ClaimStatus.BUSY) {
            throw new CentralServiceException(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS",
                    "相同请求正在处理中，请稍后重试");
        }

        try {
            Optional<Receipt> legacy = repository.findByIdempotencyKey(key);
            if (legacy.isPresent()) {
                Receipt existing = legacy.get();
                idempotencyRepository.complete(key, fingerprint, existing.bookingReference());
                return existing;
            }

            Receipt receipt;
            try {
                receipt = coordinator.coordinate(
                        request,
                        properties.airlines(),
                        connectionMinutes(),
                        idempotencyBookingReference(key)
                );
            } catch (BookingException exception) {
                throw new CentralServiceException(HttpStatus.CONFLICT, "BOOKING_FAILED", exception.getMessage());
            }
            try {
                return persistenceService.persist(receipt, key, fingerprint);
            } catch (RuntimeException exception) {
                coordinator.cancel(receipt.bookingReference(), receipt.legs(), properties.airlines());
                throw new CentralServiceException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "RECEIPT_PERSIST_FAILED", "收据保存失败，已尝试释放航段");
            }
        } catch (RuntimeException exception) {
            idempotencyRepository.fail(key, fingerprint);
            throw exception;
        }
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
                repository.updateStatus(bookingReference, BookingStatus.CANCEL_PENDING,
                        "厚客户端取消未完成，等待后台重试：" + airlineCode);
                throw new CentralServiceException(HttpStatus.CONFLICT, "AIRLINE_NOT_CANCELLED",
                        airlineCode + " 仍未取消，当前状态：" + status.status());
            }
        }
        repository.updateStatus(bookingReference, BookingStatus.CANCELLED, "厚客户端取消已登记");
        return get(bookingReference);
    }

    public int retryPendingCancellations() {
        int recovered = 0;
        for (String bookingReference : repository.findReferencesByStatus(BookingStatus.CANCEL_PENDING)) {
            Receipt existing = get(bookingReference);
            List<String> failures = coordinator.cancel(
                    bookingReference, existing.legs(), properties.airlines());
            if (failures.isEmpty()) {
                repository.updateStatus(bookingReference, BookingStatus.CANCELLED,
                        "后台重试取消成功");
                recovered++;
            } else {
                repository.updateStatus(bookingReference, BookingStatus.CANCEL_PENDING,
                        "后台重试仍失败：" + String.join("; ", failures));
            }
        }
        return recovered;
    }

    private Map<String, Integer> connectionMinutes() {
        return properties.airports().stream()
                .collect(Collectors.toMap(airport -> airport.code(), airport -> airport.minConnectionMinutes()));
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

    private String idempotencyBookingReference(String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            return "BK" + HexFormat.of().formatHex(hash).substring(0, 10).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String fingerprint(BookingRequest request) {
        StringBuilder canonical = new StringBuilder();
        for (var leg : request.trip().legs()) {
            canonical.append(leg.flightInstanceId()).append('|')
                    .append(leg.airlineCode()).append('|')
                    .append(leg.origin()).append('|')
                    .append(leg.destination()).append('|')
                    .append(leg.departureTime()).append('|')
                    .append(leg.arrivalTime()).append('|')
                    .append(leg.fare()).append('\n');
        }
        for (var passenger : request.passengers()) {
            canonical.append(passenger.name()).append('|')
                    .append(passenger.passportNumber()).append('|')
                    .append(passenger.seatPreference()).append('\n');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
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
