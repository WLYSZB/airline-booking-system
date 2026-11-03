package com.example.airline.airline;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
public class FlightSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final AirlineProperties properties;

    public FlightSeeder(JdbcTemplate jdbc, AirlineProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM flight_instance", Integer.class);
        if (count != null && count > 0) {
            jdbc.update("""
                            UPDATE flight_instance
                               SET airline_name = ?, own_discount = ?
                             WHERE airline_code = ?
                            """,
                    properties.getName(),
                    properties.getOwnDiscount(),
                    properties.getCode());
            return;
        }
        LocalDate date = properties.getSeedDate();
        List<SeedFlight> flights = switch (properties.getCode()) {
            case "A" -> List.of(
                    flight("A1101", "WUH", "PEK", "08:00", "10:10", "680", 6),
                    flight("A1102", "WUH", "PVG", "11:00", "12:30", "420", 6),
                    flight("A1103", "PVG", "PEK", "14:00", "16:15", "500", 6),
                    flight("A1104", "CAN", "PEK", "14:00", "17:00", "460", 6),
                    flight("A1105", "WUH", "CAN", "09:00", "10:50", "390", 6)
            );
            case "B" -> List.of(
                    flight("B2101", "WUH", "PEK", "09:30", "11:35", "720", 4),
                    flight("B2102", "WUH", "CAN", "10:00", "11:50", "390", 4),
                    flight("B2103", "CAN", "PEK", "13:30", "16:20", "460", 4),
                    flight("B2105", "WUH", "PVG", "07:30", "09:00", "410", 4)
            );
            case "C" -> List.of(
                    flight("C3101", "WUH", "PEK", "18:30", "20:35", "650", 3),
                    flight("C3102", "WUH", "CTU", "07:30", "09:20", "300", 3),
                    flight("C3103", "CTU", "PEK", "11:00", "13:30", "520", 3),
                    flight("C3104", "PVG", "PEK", "13:00", "15:10", "510", 5)
            );
            default -> List.of();
        };
        for (SeedFlight flight : flights) {
            LocalDateTime departure = LocalDateTime.of(date, LocalTime.parse(flight.departure()));
            LocalDateTime arrival = LocalDateTime.of(date, LocalTime.parse(flight.arrival()));
            jdbc.update("""
                            INSERT INTO flight_instance
                                (flight_instance_id, airline_code, airline_name, flight_number,
                                 origin, destination, departure_time, arrival_time, fare,
                                 available_seats, own_discount)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    properties.getCode() + "-" + date + "-" + flight.number(),
                    properties.getCode(),
                    properties.getName(),
                    flight.number(),
                    flight.origin(),
                    flight.destination(),
                    departure,
                    arrival,
                    new BigDecimal(flight.fare()),
                    flight.seats(),
                    properties.getOwnDiscount()
            );
        }
    }

    private SeedFlight flight(
            String number,
            String origin,
            String destination,
            String departure,
            String arrival,
            String fare,
            int seats
    ) {
        return new SeedFlight(number, origin, destination, departure, arrival, fare, seats);
    }

    private record SeedFlight(
            String number,
            String origin,
            String destination,
            String departure,
            String arrival,
            String fare,
            int seats
    ) {
    }
}
