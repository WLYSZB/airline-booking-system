package com.example.airline.central;

import com.example.airline.client.HttpAirlineClient;
import com.example.airline.core.AirlineGateway;
import com.example.airline.core.BookingCoordinator;
import com.example.airline.core.TripComposer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AppConfig {

    @Bean
    public AirlineGateway airlineGateway(AppProperties properties) {
        return new HttpAirlineClient(Duration.ofMillis(properties.httpTimeoutMs()));
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService searchExecutor(AppProperties properties) {
        return Executors.newFixedThreadPool(Math.max(2, properties.airlines().size()));
    }

    @Bean
    public TripComposer tripComposer() {
        return new TripComposer();
    }

    @Bean
    public BookingCoordinator bookingCoordinator(AirlineGateway airlineGateway) {
        return new BookingCoordinator(airlineGateway);
    }
}
