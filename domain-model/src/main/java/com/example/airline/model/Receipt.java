package com.example.airline.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Receipt(
        String bookingReference,
        BookingStatus status,
        List<BookedLeg> legs,
        List<Passenger> passengers,
        BigDecimal totalPrice,
        boolean sameAirlineDiscount,
        String pricingNote,
        Instant createdAt,
        String message
) {
    public Receipt {
        legs = legs == null ? List.of() : List.copyOf(legs);
        passengers = passengers == null ? List.of() : List.copyOf(passengers);
    }

    public Receipt withStatus(BookingStatus newStatus, String newMessage) {
        return new Receipt(
                bookingReference,
                newStatus,
                legs,
                passengers,
                totalPrice,
                sameAirlineDiscount,
                pricingNote,
                createdAt,
                newMessage
        );
    }
}
