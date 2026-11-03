package com.example.airline.model;

public record AirlineInfo(
        String code,
        String name,
        int ownDiscountPercent,
        String baseUrl
) {
}
