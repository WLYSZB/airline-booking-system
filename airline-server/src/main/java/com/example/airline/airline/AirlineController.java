package com.example.airline.airline;

import com.example.airline.model.FlightOffer;
import com.example.airline.model.HoldRequest;
import com.example.airline.model.HoldResponse;
import com.example.airline.model.ReservationDetails;
import com.example.airline.model.ReservationStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/airline/{airlineCode}")
public class AirlineController {

    private final AirlineRepository repository;
    private final AirlineProperties properties;

    public AirlineController(AirlineRepository repository, AirlineProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @GetMapping("/flights")
    public List<FlightOffer> search(
            @PathVariable String airlineCode,
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam LocalDate date,
            @RequestParam(defaultValue = "1") int passengers
    ) {
        requireAirline(airlineCode);
        if (passengers < 1) {
            throw new AirlineServiceException(HttpStatus.BAD_REQUEST, "INVALID_PASSENGERS", "乘客人数必须大于 0");
        }
        return repository.search(origin.toUpperCase(), destination.toUpperCase(), date, passengers);
    }

    @PostMapping("/holds")
    public HoldResponse hold(@PathVariable String airlineCode, @RequestBody HoldRequest request) {
        requireAirline(airlineCode);
        return repository.hold(request, LocalDateTime.now());
    }

    @PostMapping("/holds/{holdId}/confirm")
    public HoldResponse confirm(@PathVariable String airlineCode, @PathVariable String holdId) {
        requireAirline(airlineCode);
        return repository.confirm(holdId);
    }

    @DeleteMapping("/holds/{holdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable String airlineCode, @PathVariable String holdId) {
        requireAirline(airlineCode);
        repository.release(holdId);
    }

    @GetMapping("/reservations/{bookingReference}")
    public ReservationStatus reservation(
            @PathVariable String airlineCode,
            @PathVariable String bookingReference
    ) {
        requireAirline(airlineCode);
        return repository.reservationStatus(bookingReference);
    }

    @GetMapping("/reservations/{bookingReference}/details")
    public ReservationDetails reservationDetails(
            @PathVariable String airlineCode,
            @PathVariable String bookingReference
    ) {
        requireAirline(airlineCode);
        return repository.reservationDetails(bookingReference);
    }

    @PostMapping("/reservations/{bookingReference}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String airlineCode, @PathVariable String bookingReference) {
        requireAirline(airlineCode);
        repository.cancelBooking(bookingReference);
    }

    private void requireAirline(String airlineCode) {
        if (!properties.getCode().equalsIgnoreCase(airlineCode)) {
            throw new AirlineServiceException(HttpStatus.NOT_FOUND, "AIRLINE_NOT_FOUND",
                    "本实例只服务航司 " + properties.getCode());
        }
    }
}
