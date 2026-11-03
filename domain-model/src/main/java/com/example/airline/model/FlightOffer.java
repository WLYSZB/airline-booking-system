package com.example.airline.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

public record FlightOffer(
        String flightInstanceId,
        String airlineCode,
        String airlineName,
        String flightNumber,
        String origin,
        String destination,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        BigDecimal fare,
        int availableSeats,
        int ownDiscountPercent
) {
    public long durationMinutes() {
        return Duration.between(departureTime, arrivalTime).toMinutes();
    }
}
