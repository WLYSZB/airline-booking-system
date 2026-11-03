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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Coordinates holds and confirmations. The thin server invokes it in-process;
 * the thick client invokes the same rules from the desktop side.
 */
public class BookingCoordinator {

    private static final int MAX_PASSENGERS = 6;
    private static final int MAX_LEGS = 2;

    private final AirlineGateway gateway;

    public BookingCoordinator(AirlineGateway gateway) {
        this.gateway = gateway;
    }

    public Receipt coordinate(
            BookingRequest request,
            List<AirlineInfo> airlines,
            Map<String, Integer> connectionMinutes
    ) {
        return coordinate(request, airlines, connectionMinutes, newBookingReference());
    }

    public Receipt coordinate(
            BookingRequest request,
            List<AirlineInfo> airlines,
            Map<String, Integer> connectionMinutes,
            String bookingReference
    ) {
        validate(request);
        if (blank(bookingReference)) {
            throw new BookingException("预订编号不能为空");
        }
        Map<String, AirlineInfo> registry = airlines.stream()
                .collect(Collectors.toMap(AirlineInfo::code, Function.identity()));
        List<FlightOffer> flights = authoritativeFlights(request, registry);
        validateItinerary(flights, request.passengers().size(), connectionMinutes);
        List<BookedLeg> bookedLegs = new ArrayList<>();
        List<HoldContext> createdHolds = new ArrayList<>();

        try {
            for (int index = 0; index < flights.size(); index++) {
                FlightOffer flight = flights.get(index);
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
            List<String> compensationFailures = compensate(createdHolds);
            String suffix = compensationFailures.isEmpty()
                    ? "已释放本次已预占航段"
                    : "以下预占释放失败，需要人工或后台补偿：" + String.join("; ", compensationFailures);
            throw new BookingException("预订失败，" + suffix + "；原因：" + exception.getMessage(), exception);
        }

        boolean sameAirline = bookedLegs.stream().map(BookedLeg::airlineCode).distinct().count() == 1;
        int discount = sameAirline ? flights.get(0).ownDiscountPercent() : 0;
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
        if (request.trip().legs().size() > MAX_LEGS) {
            throw new BookingException("一次预订最多允许 " + MAX_LEGS + " 个航段");
        }
        if (request.passengers() == null || request.passengers().isEmpty()
                || request.passengers().size() > MAX_PASSENGERS) {
            throw new BookingException("乘客人数必须在 1 到 " + MAX_PASSENGERS + " 人之间");
        }
        if (request.trip().passengers() != request.passengers().size()) {
            throw new BookingException("行程人数与乘客列表不一致，请重新查询");
        }
        boolean invalidPassenger = request.passengers().stream()
                .anyMatch(p -> p == null || blank(p.name()) || blank(p.passportNumber())
                        || p.seatPreference() == null || p.seatPreference() == SeatPreference.MIXED);
        if (invalidPassenger) {
            throw new BookingException("乘客姓名、护照号和各自座位偏好不能为空");
        }
    }

    private List<FlightOffer> authoritativeFlights(
            BookingRequest request,
            Map<String, AirlineInfo> registry
    ) {
        List<FlightOffer> flights = new ArrayList<>();
        Set<String> flightIds = new HashSet<>();
        for (FlightOffer requested : request.trip().legs()) {
            if (requested == null || blank(requested.flightInstanceId())) {
                throw new BookingException("航段缺少航班实例编号");
            }
            if (!flightIds.add(requested.flightInstanceId())) {
                throw new BookingException("同一航班不能重复预订");
            }
            AirlineInfo airline = requireAirline(registry, requested.airlineCode());
            FlightOffer actual = gateway.flight(airline, requested.flightInstanceId());
            if (!Objects.equals(actual.airlineCode(), requested.airlineCode())) {
                throw new BookingException("航班不属于指定航司：" + requested.flightInstanceId());
            }
            if (!Objects.equals(actual.origin(), requested.origin())
                    || !Objects.equals(actual.destination(), requested.destination())
                    || !Objects.equals(actual.departureTime(), requested.departureTime())
                    || !Objects.equals(actual.arrivalTime(), requested.arrivalTime())
                    || actual.fare().compareTo(requested.fare()) != 0) {
                throw new BookingException("航班信息已更新，请重新查询后预订：" + requested.flightInstanceId());
            }
            if (actual.availableSeats() < request.passengers().size()) {
                throw new BookingException("余座不足：" + actual.flightNumber());
            }
            flights.add(actual);
        }
        return flights;
    }

    private void validateItinerary(
            List<FlightOffer> flights,
            int passengers,
            Map<String, Integer> connectionMinutes
    ) {
        for (FlightOffer flight : flights) {
            if (!flight.arrivalTime().isAfter(flight.departureTime())) {
                throw new BookingException("航班到达时间必须晚于起飞时间：" + flight.flightNumber());
            }
            if (flight.availableSeats() < passengers) {
                throw new BookingException("余座不足：" + flight.flightNumber());
            }
        }
        if (flights.size() != 2) {
            return;
        }
        FlightOffer first = flights.get(0);
        FlightOffer second = flights.get(1);
        if (!first.destination().equals(second.origin())) {
            throw new BookingException("联程航段不连续：" + first.destination() + " / " + second.origin());
        }
        if (!second.departureTime().isAfter(first.arrivalTime())) {
            throw new BookingException("后段起飞时间必须晚于前段到达时间");
        }
        long connection = Duration.between(first.arrivalTime(), second.departureTime()).toMinutes();
        Integer minimum = connectionMinutes == null ? null : connectionMinutes.get(first.destination());
        if (minimum == null) {
            throw new BookingException("未配置中转机场最短衔接时间：" + first.destination());
        }
        if (connection < minimum) {
            throw new BookingException("中转时间不足：需要 " + minimum + " 分钟，当前 " + connection + " 分钟");
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

    private List<String> compensate(List<HoldContext> holds) {
        List<String> failures = new ArrayList<>();
        for (int i = holds.size() - 1; i >= 0; i--) {
            HoldContext context = holds.get(i);
            RuntimeException last = null;
            boolean released = false;
            for (int attempt = 0; attempt < 3 && !released; attempt++) {
                try {
                    gateway.release(context.airline(), context.holdId());
                    released = true;
                } catch (RuntimeException exception) {
                    last = exception;
                    if (attempt < 2) {
                        try {
                            Thread.sleep(50L);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            if (!released) {
                failures.add(context.airline().code() + " / " + context.holdId()
                        + (last == null ? "" : "：" + last.getMessage()));
            }
        }
        return failures;
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
