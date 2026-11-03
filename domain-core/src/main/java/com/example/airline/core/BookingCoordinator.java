package com.example.airline.core;

import com.example.airline.model.AirlineInfo;
import com.example.airline.model.BookedLeg;
import com.example.airline.model.BookingRequest;
import com.example.airline.model.BookingStatus;
import com.example.airline.model.FlightOffer;
import com.example.airline.model.HoldRequest;
import com.example.airline.model.HoldResponse;
import com.example.airline.model.Passenger;
import com.example.airline.model.Receipt;
import com.example.airline.model.SeatPreference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Coordinates holds and confirmations. The thin server invokes it in-process;
 * the thick client invokes the same rules from the desktop side.
 */
public class BookingCoordinator {

    private final AirlineGateway gateway;

    public BookingCoordinator(AirlineGateway gateway) {
        this.gateway = gateway;
    }

    public Receipt coordinate(BookingRequest request, List<AirlineInfo> airlines) {
        validate(request);
        Map<String, AirlineInfo> registry = airlines.stream()
                .collect(Collectors.toMap(AirlineInfo::code, Function.identity()));
        String bookingReference = newBookingReference();
        List<BookedLeg> bookedLegs = new ArrayList<>();
        List<HoldContext> createdHolds = new ArrayList<>();

        try {
            for (int index = 0; index < request.trip().legs().size(); index++) {
                FlightOffer flight = request.trip().legs().get(index);
                AirlineInfo airline = requireAirline(registry, flight.airlineCode());
                String legKey = request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                        ? UUID.randomUUID().toString()
                        : request.idempotencyKey() + "-" + index;
                HoldResponse hold = gateway.hold(airline, new HoldRequest(
                        bookingReference,
                        flight.flightInstanceId(),
                        request.passengers().size(),
                        legKey,
                        summarizeSeatPreference(request.passengers())
                ));
                createdHolds.add(new HoldContext(airline, hold.holdId()));
                bookedLegs.add(new BookedLeg(
                        flight.airlineCode(),
                        flight.flightNumber(),
                        flight.flightInstanceId(),
                        flight.origin(),
                        flight.destination(),
                        flight.departureTime(),
                        flight.arrivalTime(),
                        hold.fare(),
                        hold.holdId(),
                        "HELD"
                ));
            }

            for (int index = 0; index < createdHolds.size(); index++) {
                HoldContext context = createdHolds.get(index);
                gateway.confirm(context.airline(), context.holdId());
                BookedLeg leg = bookedLegs.get(index);
                bookedLegs.set(index, new BookedLeg(
                        leg.airlineCode(), leg.flightNumber(), leg.flightInstanceId(),
                        leg.origin(), leg.destination(), leg.departureTime(), leg.arrivalTime(),
                        leg.fare(), leg.holdId(), "CONFIRMED"
                ));
            }
        } catch (RuntimeException exception) {
            compensate(createdHolds);
            throw new BookingException("预订失败，已释放本次已预占航段：" + exception.getMessage(), exception);
        }

        boolean sameAirline = bookedLegs.stream().map(BookedLeg::airlineCode).distinct().count() == 1;
        int discount = sameAirline ? registry.get(bookedLegs.get(0).airlineCode()).ownDiscountPercent() : 0;
        BigDecimal total = calculateTotal(bookedLegs, discount, request.passengers().size());
        String note = sameAirline
                ? "同一航司 OwnDiscount " + discount + "%"
                : "跨航司行程，不应用 OwnDiscount";
        return new Receipt(
                bookingReference,
                BookingStatus.CONFIRMED,
                bookedLegs,
                request.passengers(),
                total,
                sameAirline,
                note,
                Instant.now(),
                "预订成功，收据已生成"
        );
    }

    public List<String> cancel(String bookingReference, List<BookedLeg> legs, List<AirlineInfo> airlines) {
        Map<String, AirlineInfo> registry = airlines.stream()
                .collect(Collectors.toMap(AirlineInfo::code, Function.identity()));
        Set<String> airlineCodes = legs.stream().map(BookedLeg::airlineCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> failures = new ArrayList<>();
        for (String code : airlineCodes) {
            AirlineInfo airline = registry.get(code);
            if (airline == null) {
                failures.add(code + ": 未找到航司配置");
                continue;
            }
            try {
                gateway.cancel(airline, bookingReference);
            } catch (RuntimeException exception) {
                failures.add(code + ": " + exception.getMessage());
            }
        }
        return failures;
    }

    private void validate(BookingRequest request) {
        if (request == null || request.trip() == null || request.trip().legs().isEmpty()) {
            throw new BookingException("行程不能为空");
        }
        if (request.passengers() == null || request.passengers().isEmpty()) {
            throw new BookingException("至少需要一名乘客");
        }
        boolean invalidPassenger = request.passengers().stream()
                .anyMatch(p -> p == null || blank(p.name()) || blank(p.passportNumber())
                        || p.seatPreference() == null || p.seatPreference() == SeatPreference.MIXED);
        if (invalidPassenger) {
            throw new BookingException("乘客姓名、护照号和各自座位偏好不能为空");
        }
    }

    private SeatPreference summarizeSeatPreference(List<Passenger> passengers) {
        SeatPreference first = passengers.get(0).seatPreference();
        boolean allSame = passengers.stream().allMatch(p -> p.seatPreference() == first);
        return allSame ? first : SeatPreference.MIXED;
    }

    private AirlineInfo requireAirline(Map<String, AirlineInfo> registry, String code) {
        AirlineInfo airline = registry.get(code);
        if (airline == null) {
            throw new BookingException("未找到航司配置：" + code);
        }
        return airline;
    }

    private BigDecimal calculateTotal(List<BookedLeg> legs, int discountPercent, int passengers) {
        BigDecimal pricePerPassenger = legs.stream()
                .map(BookedLeg::fare)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(100L - discountPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return pricePerPassenger.multiply(BigDecimal.valueOf(passengers)).setScale(2, RoundingMode.HALF_UP);
    }

    private void compensate(List<HoldContext> holds) {
        for (int i = holds.size() - 1; i >= 0; i--) {
            HoldContext context = holds.get(i);
            try {
                gateway.release(context.airline(), context.holdId());
            } catch (RuntimeException ignored) {
                // A failed compensation is retried by the airline hold expiry task.
            }
        }
    }

    private String newBookingReference() {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        return "BK" + id;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record HoldContext(AirlineInfo airline, String holdId) {
    }
}
