package com.example.airline.airline;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbc;
    private final AirlineProperties properties;

    public HealthController(JdbcTemplate jdbc, AirlineProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "airline-server",
                "airline", properties.getCode(),
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/ready")
    public Map<String, Object> ready() {
        Integer value = jdbc.queryForObject("SELECT 1", Integer.class);
        boolean ready = value != null && value == 1;
        return Map.of(
                "status", ready ? "READY" : "NOT_READY",
                "airline", properties.getCode()
        );
    }
}
