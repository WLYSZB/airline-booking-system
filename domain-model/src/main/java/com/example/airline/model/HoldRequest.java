package com.example.airline.model;

public record HoldRequest(
        String bookingReference,
        String flightInstanceId,
        int passengers,
        String idempotencyKey,
        SeatPreference seatPreference
) {
}
