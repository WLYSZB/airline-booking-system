package com.example.airline.central;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "central-booking-server",
                "coordination", "CENTRAL_SERVER",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/ready")
    public Map<String, Object> ready() {
        Integer value = jdbc.queryForObject("SELECT 1", Integer.class);
        return Map.of("status", value != null && value == 1 ? "READY" : "NOT_READY");
    }
}
