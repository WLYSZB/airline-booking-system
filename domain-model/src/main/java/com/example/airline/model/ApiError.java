package com.example.airline.model;

import java.time.Instant;

public record ApiError(
        String code,
        String message,
        String requestId,
        Instant timestamp
) {
}
