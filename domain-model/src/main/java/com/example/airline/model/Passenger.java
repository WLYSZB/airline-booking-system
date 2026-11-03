package com.example.airline.model;

public record Passenger(
        String name,
        String passportNumber,
        SeatPreference seatPreference
) {
}
