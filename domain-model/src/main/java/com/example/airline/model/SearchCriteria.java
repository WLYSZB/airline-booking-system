package com.example.airline.model;

import java.time.LocalDate;
import java.util.List;

public record SearchCriteria(
        String origin,
        String destination,
        LocalDate date,
        int passengers,
        List<String> airlineCodes,
        SortMode sortMode
) {
    public SearchCriteria {
        airlineCodes = airlineCodes == null ? List.of() : List.copyOf(airlineCodes);
        sortMode = sortMode == null ? SortMode.PRICE : sortMode;
    }
}
