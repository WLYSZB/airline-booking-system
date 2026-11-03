package com.example.airline.model;

import java.util.List;

/**
 * The thick client supplies only identity and passenger context. Fares and
 * segment details are read back from the airline services by the central server.
 */
public record RegisterBookingRequest(
        String bookingReference,
        List<Passenger> passengers
) {
    public RegisterBookingRequest {
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
    }
}
