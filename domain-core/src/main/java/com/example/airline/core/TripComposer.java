package com.example.airline.core;

import com.example.airline.model.FlightOffer;
import com.example.airline.model.SortMode;
import com.example.airline.model.TripOffer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pure business rules shared by the thin server and the thick client.
 */
public class TripComposer {

    public List<TripOffer> compose(
            List<FlightOffer> flights,
            String origin,
            String destination,
            int passengers,
            Map<String, Integer> connectionMinutes,
            SortMode sortMode
    ) {
        if (passengers < 1) {
            return List.of();
        }
        List<FlightOffer> eligible = flights.stream()
                .filter(Objects::nonNull)
                .filter(f -> f.availableSeats() >= passengers)
                .collect(Collectors.toMap(FlightOffer::flightInstanceId, Function.identity(), (a, b) -> a, LinkedHashMap::new))
                .values().stream().toList();

        List<FlightOffer> departures = eligible.stream()
                .filter(f -> origin.equals(f.origin()))
                .toList();
        List<FlightOffer> arrivals = eligible.stream()
                .filter(f -> destination.equals(f.destination()))
                .toList();

        Map<String, TripOffer> trips = new LinkedHashMap<>();
        for (FlightOffer flight : departures) {
            if (destination.equals(flight.destination())) {
                TripOffer offer = toTrip(List.of(flight), passengers);
                trips.putIfAbsent(key(offer), offer);
            }
        }

        for (FlightOffer first : departures) {
            for (FlightOffer second : arrivals) {
                if (first.flightInstanceId().equals(second.flightInstanceId())) {
                    continue;
                }
                if (!first.destination().equals(second.origin())) {
                    continue;
                }
                int required = connectionMinutes.getOrDefault(first.destination(), 90);
                long available = Duration.between(first.arrivalTime(), second.departureTime()).toMinutes();
                if (available < required) {
                    continue;
                }
                TripOffer offer = toTrip(List.of(first, second), passengers);
                trips.putIfAbsent(key(offer), offer);
            }
        }

        Comparator<TripOffer> comparator;
        if (sortMode == SortMode.TIME) {
            comparator = Comparator.comparingInt(TripOffer::totalDurationMinutes)
                    .thenComparing(TripOffer::totalPrice);
        } else if (sortMode == SortMode.DEPARTURE) {
            comparator = Comparator.comparing((TripOffer trip) -> trip.legs().get(0).departureTime())
                    .thenComparing(TripOffer::totalPrice);
        } else {
            comparator = Comparator.comparing(TripOffer::totalPrice)
                    .thenComparingInt(TripOffer::totalDurationMinutes);
        }
        List<TripOffer> result = new ArrayList<>(trips.values());
        result.sort(comparator);
        return result;
    }

    private TripOffer toTrip(List<FlightOffer> legs, int passengers) {
        BigDecimal basePerPassenger = legs.stream()
                .map(FlightOffer::fare)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean sameAirline = legs.stream()
                .map(FlightOffer::airlineCode)
                .distinct()
                .count() == 1;
        int discount = sameAirline ? legs.get(0).ownDiscountPercent() : 0;
        BigDecimal pricePerPassenger = basePerPassenger.multiply(BigDecimal.valueOf(100L - discount))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = pricePerPassenger.multiply(BigDecimal.valueOf(passengers))
                .setScale(2, RoundingMode.HALF_UP);
        int duration = Math.toIntExact(Duration.between(
                legs.get(0).departureTime(),
                legs.get(legs.size() - 1).arrivalTime()
        ).toMinutes());
        String note = sameAirline
                ? "同一航司 OwnDiscount " + discount + "%"
                : "跨航司行程，不应用 OwnDiscount";
        return new TripOffer(legs, total, pricePerPassenger, passengers, duration, sameAirline, note);
    }

    private String key(TripOffer offer) {
        return offer.legs().stream()
                .map(FlightOffer::flightInstanceId)
                .collect(Collectors.joining(">"));
    }
}
