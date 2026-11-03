package com.example.airline.central;

import com.example.airline.model.AirlineInfo;
import com.example.airline.model.AirportInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RegistryController {

    private final AppProperties properties;

    public RegistryController(AppProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/airlines")
    public List<AirlineInfo> airlines() {
        return properties.airlines();
    }

    @GetMapping("/airports")
    public List<AirportInfo> airports() {
        return properties.airports();
    }
}
