package com.example.airline.model;

import java.math.BigDecimal;
import java.util.List;

public record TripOffer(
        List<FlightOffer> legs,
        BigDecimal totalPrice,
        BigDecimal pricePerPassenger,
        int passengers,
        int totalDurationMinutes,
        boolean sameAirlineDiscount,
        String pricingNote
) {
    public TripOffer {
        legs = List.copyOf(legs);
    }

    public boolean direct() {
        return legs.size() == 1;
    }

    public String airlinesLabel() {
        return legs.stream().map(FlightOffer::airlineCode).distinct().reduce((a, b) -> a + "/" + b).orElse("");
    }
}
