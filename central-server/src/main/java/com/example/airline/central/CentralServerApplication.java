package com.example.airline.central;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class CentralServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CentralServerApplication.class, args);
    }
}
