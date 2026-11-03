package com.example.airline.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookedLeg(
        String airlineCode,
        String flightNumber,
        String flightInstanceId,
        String origin,
        String destination,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        BigDecimal fare,
        String holdId,
        String status
) {
}
