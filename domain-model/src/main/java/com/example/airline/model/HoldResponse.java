package com.example.airline.model;

import java.math.BigDecimal;

public record HoldResponse(
        String holdId,
        String status,
        BigDecimal fare,
        int passengers
) {
}
