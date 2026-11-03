package com.example.airline.model;

public record AirportInfo(
        String code,
        String city,
        String country,
        int minConnectionMinutes
) {
}
