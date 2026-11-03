package com.example.airline.central;

import com.example.airline.model.AirlineInfo;
import com.example.airline.model.AirportInfo;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        List<AirlineInfo> airlines,
        List<AirportInfo> airports,
        int httpTimeoutMs
) {
    public AppProperties {
        airlines = airlines == null ? List.of() : List.copyOf(airlines);
        airports = airports == null ? List.of() : List.copyOf(airports);
        httpTimeoutMs = httpTimeoutMs <= 0 ? 4000 : httpTimeoutMs;
    }
}
