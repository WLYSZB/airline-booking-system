package com.example.airline.thick;

import com.example.airline.client.HttpAirlineClient;
import com.example.airline.core.AirlineGateway;
import com.example.airline.core.BookingCoordinator;
import com.example.airline.core.TripComposer;
import com.example.airline.model.AirlineInfo;
import com.example.airline.model.AirportInfo;
import com.example.airline.model.BookingRequest;
import com.example.airline.model.BookingStatus;
import com.example.airline.model.FlightOffer;
import com.example.airline.model.Passenger;
import com.example.airline.model.Receipt;
import com.example.airline.model.RegisterBookingRequest;
import com.example.airline.model.SortMode;
import com.example.airline.model.TripOffer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ThickBookingService {

    private final CentralClient centralClient;
    private final AirlineGateway airlineGateway;
    private final TripComposer tripComposer = new TripComposer();
    private final BookingCoordinator bookingCoordinator;

    public ThickBookingService(CentralClient centralClient) {
        this.centralClient = centralClient;
        this.airlineGateway = new HttpAirlineClient(Duration.ofSeconds(5));
        this.bookingCoordinator = new BookingCoordinator(airlineGateway);
    }

    public SearchPayload search(
            String origin,
            String destination,
            String date,
            int passengers,
            List<String> airlineCodes,
            SortMode sortMode
    ) {
        List<AirlineInfo> airlines = centralClient.airlines().stream()
                .filter(a -> airlineCodes.isEmpty() || airlineCodes.contains(a.code()))
                .toList();
        Map<String, AirportInfo> airports = centralClient.airports().stream()
                .collect(Collectors.toMap(AirportInfo::code, airport -> airport));
        Map<String, Integer> connections = airports.values().stream()
                .collect(Collectors.toMap(AirportInfo::code, AirportInfo::minConnectionMinutes));
        List<String> warnings = new ArrayList<>();
        List<FlightOffer> flights = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(Math.max(2, airlines.size()));
        try {
            List<CompletableFuture<AirlineResult>> futures = airlines.stream()
                    .map(airline -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return new AirlineResult(
                                    airlineGateway.search(airline, origin, destination, java.time.LocalDate.parse(date), passengers),
                                    null
                            );
                        } catch (RuntimeException exception) {
                            return new AirlineResult(List.of(),
                                    airline.code() + " " + airline.name() + " 查询失败：" + exception.getMessage());
                        }
                    }, executor))
                    .toList();
            for (CompletableFuture<AirlineResult> future : futures) {
                AirlineResult result = future.join();
                flights.addAll(result.flights());
                if (result.warning() != null) {
                    warnings.add(result.warning());
                }
            }
        } finally {
            executor.shutdown();
        }

        List<TripOffer> trips = tripComposer.compose(
                flights, origin, destination, passengers, connections, sortMode);
        return new SearchPayload(trips, warnings, airports);
    }

    public Receipt book(TripOffer trip, List<Passenger> passengers) {
        List<AirlineInfo> airlines = centralClient.airlines();
        BookingRequest request = new BookingRequest(
                trip,
                passengers,
                "THICK-" + UUID.randomUUID()
        );
        Receipt receipt = bookingCoordinator.coordinate(request, airlines);
        RegisterBookingRequest registration = new RegisterBookingRequest(
                receipt.bookingReference(),
                receipt.passengers()
        );
        try {
            return centralClient.register(registration);
        } catch (RuntimeException exception) {
            List<String> compensationFailures = bookingCoordinator.cancel(
                    receipt.bookingReference(), receipt.legs(), airlines);
            String suffix = compensationFailures.isEmpty()
                    ? "已自动取消本次航司预订"
                    : "自动补偿未完全成功：" + String.join("; ", compensationFailures);
            throw new IllegalStateException(
                    "中央登记失败，预订编号 " + receipt.bookingReference() + "，" + suffix
                            + "；原始错误：" + exception.getMessage(),
                    exception
            );
        }
    }

    public Receipt cancel(String bookingReference) {
        Receipt existing = centralClient.booking(bookingReference);
        if (existing.status() == BookingStatus.CANCELLED) {
            return existing;
        }
        List<String> failures = bookingCoordinator.cancel(
                bookingReference, existing.legs(), centralClient.airlines());
        if (!failures.isEmpty()) {
            throw new IllegalStateException("部分航段取消失败：" + String.join("; ", failures));
        }
        return centralClient.markCancelled(bookingReference);
    }

    public record SearchPayload(
            List<TripOffer> trips,
            List<String> warnings,
            Map<String, AirportInfo> airports
    ) {
    }

    private record AirlineResult(List<FlightOffer> flights, String warning) {
    }
}
