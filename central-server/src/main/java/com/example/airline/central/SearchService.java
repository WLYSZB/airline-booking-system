package com.example.airline.central;

import com.example.airline.core.AirlineGateway;
import com.example.airline.core.TripComposer;
import com.example.airline.model.AirlineInfo;
import com.example.airline.model.FlightOffer;
import com.example.airline.model.SearchCriteria;
import com.example.airline.model.SearchResult;
import com.example.airline.model.SortMode;
import com.example.airline.model.TripOffer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final AppProperties properties;
    private final AirlineGateway gateway;
    private final TripComposer tripComposer;
    private final ExecutorService executorService;

    public SearchService(
            AppProperties properties,
            AirlineGateway gateway,
            TripComposer tripComposer,
            ExecutorService executorService
    ) {
        this.properties = properties;
        this.gateway = gateway;
        this.tripComposer = tripComposer;
        this.executorService = executorService;
    }

    public SearchResult search(SearchCriteria criteria) {
        validate(criteria);
        List<AirlineInfo> selected = selectAirlines(criteria.airlineCodes());
        List<CompletableFuture<AirlineResult>> futures = selected.stream()
                .map(airline -> CompletableFuture.supplyAsync(
                        () -> fetch(airline, criteria), executorService))
                .toList();

        List<FlightOffer> flights = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (CompletableFuture<AirlineResult> future : futures) {
            AirlineResult result = future.join();
            flights.addAll(result.flights());
            if (result.warning() != null) {
                warnings.add(result.warning());
            }
        }

        Map<String, Integer> connectionMinutes = properties.airports().stream()
                .collect(Collectors.toMap(a -> a.code().toUpperCase(), a -> a.minConnectionMinutes()));
        List<TripOffer> trips = tripComposer.compose(
                flights,
                criteria.origin().toUpperCase(),
                criteria.destination().toUpperCase(),
                criteria.passengers(),
                connectionMinutes,
                criteria.sortMode() == null ? SortMode.PRICE : criteria.sortMode()
        );
        return new SearchResult(trips, warnings, !warnings.isEmpty());
    }

    private void validate(SearchCriteria criteria) {
        if (criteria == null || blank(criteria.origin()) || blank(criteria.destination())
                || criteria.date() == null || criteria.passengers() < 1 || criteria.passengers() > 6) {
            throw new CentralServiceException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH",
                    "出发地、目的地、日期和 1-6 名乘客必须有效");
        }
        if (criteria.origin().equalsIgnoreCase(criteria.destination())) {
            throw new CentralServiceException(HttpStatus.BAD_REQUEST, "SAME_AIRPORT",
                    "出发城市和目的城市不能相同");
        }
    }

    private List<AirlineInfo> selectAirlines(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return properties.airlines();
        }
        return properties.airlines().stream()
                .filter(a -> codes.stream().anyMatch(code -> code.equalsIgnoreCase(a.code())))
                .toList();
    }

    private AirlineResult fetch(AirlineInfo airline, SearchCriteria criteria) {
        try {
            return new AirlineResult(
                    gateway.search(airline, criteria.origin(), criteria.destination(), criteria.date(), criteria.passengers()),
                    null
            );
        } catch (RuntimeException exception) {
            return new AirlineResult(List.of(), airline.code() + " " + airline.name() + " 查询失败：" + exception.getMessage());
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record AirlineResult(List<FlightOffer> flights, String warning) {
    }
}
