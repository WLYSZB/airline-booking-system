package com.example.airline.model;

import java.util.List;

public record BookingRequest(
        TripOffer trip,
        List<Passenger> passengers,
        String idempotencyKey
) {
    public BookingRequest {
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
    }
}
