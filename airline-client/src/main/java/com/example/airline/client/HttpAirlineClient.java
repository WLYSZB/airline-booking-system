package com.example.airline.client;

import com.example.airline.core.AirlineGateway;
import com.example.airline.core.BookingException;
import com.example.airline.model.AirlineInfo;
import com.example.airline.model.FlightOffer;
import com.example.airline.model.HoldRequest;
import com.example.airline.model.HoldResponse;
import com.example.airline.model.ReservationDetails;
import com.example.airline.model.ReservationStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

public class HttpAirlineClient implements AirlineGateway {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public HttpAirlineClient(Duration timeout) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.timeout = timeout;
    }

    @Override
    public List<FlightOffer> search(AirlineInfo airline, String origin, String destination, LocalDate date, int passengers) {
        String path = "/api/airline/" + encode(airline.code()) + "/flights"
                + "?origin=" + encode(origin)
                + "&destination=" + encode(destination)
                + "&date=" + encode(date.toString())
                + "&passengers=" + passengers;
        return send(request(airline, path).GET().build(), new TypeReference<>() {
        });
    }

    @Override
    public HoldResponse hold(AirlineInfo airline, HoldRequest request) {
        String path = "/api/airline/" + encode(airline.code()) + "/holds";
        return send(jsonRequest(airline, path, request), HoldResponse.class);
    }

    @Override
    public HoldResponse confirm(AirlineInfo airline, String holdId) {
        String path = "/api/airline/" + encode(airline.code()) + "/holds/" + encode(holdId) + "/confirm";
        return send(request(airline, path).POST(HttpRequest.BodyPublishers.noBody()).build(), HoldResponse.class);
    }

    @Override
    public void release(AirlineInfo airline, String holdId) {
        String path = "/api/airline/" + encode(airline.code()) + "/holds/" + encode(holdId);
        sendNoContent(request(airline, path).DELETE().build());
    }

    @Override
    public ReservationStatus reservation(AirlineInfo airline, String bookingReference) {
        String path = "/api/airline/" + encode(airline.code()) + "/reservations/" + encode(bookingReference);
        return send(request(airline, path).GET().build(), ReservationStatus.class);
    }

    @Override
    public ReservationDetails reservationDetails(AirlineInfo airline, String bookingReference) {
        String path = "/api/airline/" + encode(airline.code()) + "/reservations/"
                + encode(bookingReference) + "/details";
        return send(request(airline, path).GET().build(), ReservationDetails.class);
    }

    @Override
    public void cancel(AirlineInfo airline, String bookingReference) {
        String path = "/api/airline/" + encode(airline.code()) + "/reservations/" + encode(bookingReference) + "/cancel";
        sendNoContent(request(airline, path).POST(HttpRequest.BodyPublishers.noBody()).build());
    }

    private HttpRequest jsonRequest(AirlineInfo airline, String path, Object body) {
        try {
            return request(airline, path)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        } catch (IOException exception) {
            throw new BookingException("请求序列化失败", exception);
        }
    }

    private HttpRequest.Builder request(AirlineInfo airline, String path) {
        String base = airline.baseUrl().replaceAll("/+$", "");
        return HttpRequest.newBuilder(URI.create(base + path))
                .timeout(timeout)
                .header("Accept", "application/json");
    }

    private <T> T send(HttpRequest request, Class<T> type) {
        String body = sendRaw(request);
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException exception) {
            throw new BookingException("航司响应解析失败：" + request.uri(), exception);
        }
    }

    private <T> T send(HttpRequest request, TypeReference<T> type) {
        String body = sendRaw(request);
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException exception) {
            throw new BookingException("航司响应解析失败：" + request.uri(), exception);
        }
    }

    private void sendNoContent(HttpRequest request) {
        sendRaw(request);
    }

    private String sendRaw(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BookingException("航司服务返回 " + response.statusCode() + "：" + response.body());
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BookingException("航司请求被中断", exception);
        } catch (IOException exception) {
            throw new BookingException("无法连接航司服务：" + request.uri(), exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
