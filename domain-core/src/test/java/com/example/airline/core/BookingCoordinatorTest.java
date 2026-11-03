package com.example.airline.core;

import com.example.airline.model.AirlineInfo;
import com.example.airline.model.BookingRequest;
import com.example.airline.model.FlightOffer;
import com.example.airline.model.HoldRequest;
import com.example.airline.model.HoldResponse;
import com.example.airline.model.Passenger;
import com.example.airline.model.Receipt;
import com.example.airline.model.ReservationDetails;
import com.example.airline.model.ReservationStatus;
import com.example.airline.model.SeatPreference;
import com.example.airline.model.TripOffer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingCoordinatorTest {

    private final LocalDateTime base = LocalDateTime.of(2026, 11, 15, 8, 0);
    private final AirlineInfo airlineA = new AirlineInfo("A", "楚天航空", 10, "http://a");
    private final AirlineInfo airlineB = new AirlineInfo("B", "东湖航空", 8, "http://b");

    @Test
    void validatesAuthoritativeFlightsAndCreatesReceipt() {
        FlightOffer first = flight("A1", "A", "WUH", "PVG", base, base.plusHours(1), "400");
        FlightOffer second = flight("B2", "B", "PVG", "PEK", base.plusHours(2), base.plusHours(4), "600");
        StubGateway gateway = new StubGateway(List.of(first, second));
        BookingCoordinator coordinator = new BookingCoordinator(gateway);

        Receipt receipt = coordinator.coordinate(
                request(List.of(first, second), 2), List.of(airlineA, airlineB), Map.of("PVG", 60));

        assertEquals(2, receipt.legs().size());
        assertEquals(new BigDecimal("2000.00"), receipt.totalPrice());
        assertEquals(2, receipt.passengers().size());
        assertEquals(2, gateway.holdCalls);
        assertEquals(2, gateway.confirmCalls);
    }

    @Test
    void rejectsPassengerCountMismatchBeforeCallingAirline() {
        FlightOffer first = flight("A1", "A", "WUH", "PVG", base, base.plusHours(1), "400");
        StubGateway gateway = new StubGateway(List.of(first));
        BookingCoordinator coordinator = new BookingCoordinator(gateway);
        BookingRequest onePassengerTrip = request(List.of(first), 1);
        BookingRequest twoPassengers = new BookingRequest(
                onePassengerTrip.trip(),
                List.of(
                        new Passenger("甲", "E10000001", SeatPreference.AISLE),
                        new Passenger("乙", "E10000002", SeatPreference.WINDOW)
                ),
                "test-key"
        );

        assertThrows(BookingException.class, () -> coordinator.coordinate(
                twoPassengers, List.of(airlineA), Map.of("PVG", 60)));
        assertEquals(0, gateway.holdCalls);
    }

    @Test
    void rejectsNonContinuousItinerary() {
        FlightOffer first = flight("A1", "A", "WUH", "PVG", base, base.plusHours(1), "400");
        FlightOffer second = flight("B2", "B", "CTU", "PEK", base.plusHours(2), base.plusHours(4), "600");
        StubGateway gateway = new StubGateway(List.of(first, second));
        BookingCoordinator coordinator = new BookingCoordinator(gateway);

        assertThrows(BookingException.class, () -> coordinator.coordinate(
                request(List.of(first, second), 1), List.of(airlineA, airlineB), Map.of("PVG", 60)));
        assertEquals(0, gateway.holdCalls);
    }

    @Test
    void rejectsConnectionShorterThanAirportMinimum() {
        FlightOffer first = flight("A1", "A", "WUH", "PVG", base, base.plusHours(1), "400");
        FlightOffer second = flight("B2", "B", "PVG", "PEK", base.plusMinutes(90), base.plusHours(4), "600");
        StubGateway gateway = new StubGateway(List.of(first, second));
        BookingCoordinator coordinator = new BookingCoordinator(gateway);

        assertThrows(BookingException.class, () -> coordinator.coordinate(
                request(List.of(first, second), 1), List.of(airlineA, airlineB), Map.of("PVG", 60)));
        assertEquals(0, gateway.holdCalls);
    }

    private BookingRequest request(List<FlightOffer> flights, int passengers) {
        TripOffer trip = new TripOffer(
                flights,
                new BigDecimal("2000.00"),
                new BigDecimal("1000.00"),
                passengers,
                240,
                false,
                "跨航司行程，不应用 OwnDiscount"
        );
        List<Passenger> people = java.util.stream.IntStream.range(0, passengers)
                .mapToObj(index -> new Passenger("乘客" + index, "E1000000" + index, SeatPreference.AISLE))
                .toList();
        return new BookingRequest(trip, people, "test-key");
    }

    private FlightOffer flight(
            String id,
            String airline,
            String origin,
            String destination,
            LocalDateTime departure,
            LocalDateTime arrival,
            String fare
    ) {
        return new FlightOffer(id, airline, "航司" + airline, airline + "100",
                origin, destination, departure, arrival, new BigDecimal(fare), 4, 10);
    }

    private static final class StubGateway implements AirlineGateway {

        private final Map<String, FlightOffer> flights;
        private int holdCalls;
        private int confirmCalls;

        private StubGateway(List<FlightOffer> flights) {
            this.flights = flights.stream().collect(java.util.stream.Collectors.toMap(FlightOffer::flightInstanceId, f -> f));
        }

        @Override
        public List<FlightOffer> search(AirlineInfo airline, String origin, String destination,
                                        java.time.LocalDate date, int passengers) {
            return List.of();
        }

        @Override
        public FlightOffer flight(AirlineInfo airline, String flightInstanceId) {
            return flights.get(flightInstanceId);
        }

        @Override
        public HoldResponse hold(AirlineInfo airline, HoldRequest request) {
            holdCalls++;
            return new HoldResponse("HOLD-" + request.flightInstanceId(), "HELD",
                    new BigDecimal("500.00"), request.passengers());
        }

        @Override
        public HoldResponse confirm(AirlineInfo airline, String holdId) {
            confirmCalls++;
            return new HoldResponse(holdId, "CONFIRMED", new BigDecimal("1000.00"), 1);
        }

        @Override
        public void release(AirlineInfo airline, String holdId) {
        }

        @Override
        public ReservationStatus reservation(AirlineInfo airline, String bookingReference) {
            return new ReservationStatus(bookingReference, airline.code(), "CONFIRMED");
        }

        @Override
        public ReservationDetails reservationDetails(AirlineInfo airline, String bookingReference) {
            return new ReservationDetails(bookingReference, airline.code(), "CONFIRMED", List.of());
        }

        @Override
        public void cancel(AirlineInfo airline, String bookingReference) {
        }
    }
}
