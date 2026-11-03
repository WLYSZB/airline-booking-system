package com.example.airline.core;

import com.example.airline.model.FlightOffer;
import com.example.airline.model.SortMode;
import com.example.airline.model.TripOffer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TripComposerTest {

    private final TripComposer composer = new TripComposer();
    private final LocalDateTime base = LocalDateTime.of(2026, 11, 15, 8, 0);

    @Test
    void composesDirectAndOneStopTripsAndAppliesDiscountAfterPassengerCount() {
        FlightOffer direct = flight("A1", "A", "WUH", "PEK", base, base.plusHours(2), "1000", 4, 10);
        FlightOffer first = flight("A2", "A", "WUH", "PVG", base.plusHours(3), base.plusHours(4), "400", 4, 10);
        FlightOffer second = flight("A3", "A", "PVG", "PEK", base.plusHours(6), base.plusHours(8), "600", 4, 10);

        List<TripOffer> result = composer.compose(
                List.of(direct, first, second), "WUH", "PEK", 2, Map.of("PVG", 60), SortMode.PRICE);

        assertEquals(2, result.size());
        TripOffer directOffer = result.stream().filter(TripOffer::direct).findFirst().orElseThrow();
        assertEquals(new BigDecimal("1800.00"), directOffer.totalPrice());
        assertEquals(new BigDecimal("900.00"), directOffer.pricePerPassenger());
        assertEquals(2, directOffer.passengers());
        assertTrue(directOffer.sameAirlineDiscount());
    }

    @Test
    void rejectsConnectionBelowAirportMinimum() {
        FlightOffer first = flight("A2", "A", "WUH", "PVG", base, base.plusHours(1), "400", 4, 10);
        FlightOffer second = flight("A3", "A", "PVG", "PEK", base.plusMinutes(90), base.plusHours(3), "600", 4, 10);

        List<TripOffer> result = composer.compose(
                List.of(first, second), "WUH", "PEK", 1, Map.of("PVG", 60), SortMode.PRICE);

        assertTrue(result.isEmpty());
    }

    @Test
    void allowsConnectionExactlyAtMinimum() {
        FlightOffer first = flight("A2", "A", "WUH", "PVG", base, base.plusHours(1), "400", 4, 10);
        FlightOffer second = flight("A3", "A", "PVG", "PEK", base.plusHours(2), base.plusHours(4), "600", 4, 10);

        List<TripOffer> result = composer.compose(
                List.of(first, second), "WUH", "PEK", 1, Map.of("PVG", 60), SortMode.PRICE);

        assertEquals(1, result.size());
    }

    @Test
    void mixedAirlinesDoNotReceiveDiscountAndInsufficientSeatsAreFiltered() {
        FlightOffer first = flight("A2", "A", "WUH", "PVG", base, base.plusHours(1), "400", 4, 10);
        FlightOffer second = flight("B3", "B", "PVG", "PEK", base.plusHours(3), base.plusHours(5), "600", 3, 8);
        FlightOffer tooSmall = flight("C9", "C", "WUH", "PEK", base, base.plusHours(2), "500", 1, 12);

        List<TripOffer> result = composer.compose(
                List.of(first, second, tooSmall), "WUH", "PEK", 3, Map.of("PVG", 60), SortMode.TIME);

        assertEquals(1, result.size());
        assertFalse(result.get(0).sameAirlineDiscount());
        assertEquals(new BigDecimal("3000.00"), result.get(0).totalPrice());
    }

    @Test
    void sortsByEarliestDepartureWhenRequested() {
        FlightOffer later = flight("A1", "A", "WUH", "PEK", base.plusHours(3), base.plusHours(5), "500", 4, 10);
        FlightOffer earlier = flight("B2", "B", "WUH", "PEK", base.plusHours(1), base.plusHours(3), "800", 4, 8);

        List<TripOffer> result = composer.compose(
                List.of(later, earlier), "WUH", "PEK", 1, Map.of(), SortMode.DEPARTURE);

        assertEquals(2, result.size());
        assertEquals("B2", result.get(0).legs().get(0).flightInstanceId());
    }

    private FlightOffer flight(
            String id,
            String airline,
            String origin,
            String destination,
            LocalDateTime departure,
            LocalDateTime arrival,
            String fare,
            int seats,
            int discount
    ) {
        return new FlightOffer(id, airline, "Airline " + airline, airline + "100",
                origin, destination, departure, arrival, new BigDecimal(fare), seats, discount);
    }
}
