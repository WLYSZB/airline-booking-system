package com.example.airline.model;

import java.util.List;

public record SearchResult(
        List<TripOffer> trips,
        List<String> warnings,
        boolean partial
) {
    public SearchResult {
        trips = trips == null ? List.of() : List.copyOf(trips);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
