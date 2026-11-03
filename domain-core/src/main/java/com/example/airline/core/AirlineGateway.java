package com.example.airline.core;

import com.example.airline.model.AirlineInfo;
import com.example.airline.model.FlightOffer;
import com.example.airline.model.HoldRequest;
import com.example.airline.model.HoldResponse;
import com.example.airline.model.ReservationDetails;
import com.example.airline.model.ReservationStatus;

import java.time.LocalDate;
import java.util.List;

public interface AirlineGateway {
    List<FlightOffer> search(AirlineInfo airline, String origin, String destination, LocalDate date, int passengers);

    FlightOffer flight(AirlineInfo airline, String flightInstanceId);

    HoldResponse hold(AirlineInfo airline, HoldRequest request);

    HoldResponse confirm(AirlineInfo airline, String holdId);

    void release(AirlineInfo airline, String holdId);

    ReservationStatus reservation(AirlineInfo airline, String bookingReference);

    ReservationDetails reservationDetails(AirlineInfo airline, String bookingReference);

    void cancel(AirlineInfo airline, String bookingReference);
}
