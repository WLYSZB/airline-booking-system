package com.example.airline.model;

import java.util.List;

public record ReservationDetails(
        String bookingReference,
        String airlineCode,
        String status,
        List<ReservedSegment> segments
) {
    public ReservationDetails {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }
}
