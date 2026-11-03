package com.example.airline.thick;

import com.example.airline.model.Passenger;
import com.example.airline.model.Receipt;
import com.example.airline.model.SeatPreference;
import com.example.airline.model.SortMode;
import com.example.airline.model.TripOffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless V2 verification: uses the same Java client service as the Swing UI,
 * including client-side trip composition and direct airline coordination.
 */
public class ThickClientSmoke {

    public static void main(String[] args) {
        String centralUrl = System.getProperty("central.url",
                System.getenv().getOrDefault("CENTRAL_URL", "http://localhost:8080"));
        ThickBookingService service = new ThickBookingService(new CentralClient(centralUrl));
        ThickBookingService.SearchPayload payload = service.search(
                "WUH", "PEK", "2026-11-15", 2, List.of("A", "B", "C"), SortMode.PRICE);
        TripOffer crossAirline = payload.trips().stream()
                .filter(trip -> trip.legs().stream().map(leg -> leg.airlineCode()).distinct().count() > 1)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到跨航司联程"));

        System.out.println("V2 client trip: " + crossAirline.legs().stream()
                .map(leg -> leg.airlineCode() + leg.flightNumber())
                .reduce((a, b) -> a + " -> " + b).orElse(""));
        List<Passenger> passengers = new ArrayList<>();
        passengers.add(new Passenger("王五", "E20000001", SeatPreference.WINDOW));
        passengers.add(new Passenger("赵六", "E20000002", SeatPreference.AISLE));
        Receipt receipt = service.book(crossAirline, passengers);
        if (receipt.passengers().get(0).seatPreference() != SeatPreference.WINDOW
                || receipt.passengers().get(1).seatPreference() != SeatPreference.AISLE) {
            throw new IllegalStateException("Per-passenger seat preferences were not preserved");
        }
        System.out.println("V2 registered: " + receipt.bookingReference() + " " + receipt.status()
                + " " + receipt.totalPrice() + " seats="
                + receipt.passengers().stream().map(p -> p.seatPreference().name()).toList());
        Receipt cancelled = service.cancel(receipt.bookingReference());
        System.out.println("V2 cancelled: " + cancelled.bookingReference() + " " + cancelled.status());
        if (cancelled.status() != com.example.airline.model.BookingStatus.CANCELLED) {
            throw new IllegalStateException("V2 cancellation did not reach CANCELLED");
        }
        System.out.println("THICK_CLIENT_SMOKE_PASS");
    }
}
