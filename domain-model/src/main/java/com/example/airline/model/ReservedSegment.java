package com.example.airline.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReservedSegment(
        String holdId,
        String flightInstanceId,
        String airlineCode,
        String flightNumber,
        String origin,
        String destination,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        BigDecimal fare,
        int passengers,
        String status
) {
}
