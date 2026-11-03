package com.example.airline.model;

public record ReservationStatus(
        String bookingReference,
        String airlineCode,
        String status
) {
}
