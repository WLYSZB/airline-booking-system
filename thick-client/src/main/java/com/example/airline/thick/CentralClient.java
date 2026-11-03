package com.example.airline.thick;

import com.example.airline.model.AirlineInfo;
import com.example.airline.model.AirportInfo;
import com.example.airline.model.Receipt;
import com.example.airline.model.RegisterBookingRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class CentralClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public CentralClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public List<AirlineInfo> airlines() {
        return get("/api/airlines", new TypeReference<>() {
        });
    }

    public List<AirportInfo> airports() {
        return get("/api/airports", new TypeReference<>() {
        });
    }

    public Receipt booking(String bookingReference) {
        return get("/api/bookings/" + bookingReference, Receipt.class);
    }

    public Receipt register(RegisterBookingRequest request) {
        return post("/api/bookings/register", request, Receipt.class);
    }

    public Receipt markCancelled(String bookingReference) {
        return post("/api/bookings/" + bookingReference + "/mark-cancelled", null, Receipt.class);
    }

    private <T> T get(String path, Class<T> type) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        return send(request, type);
    }

    private <T> T get(String path, TypeReference<T> type) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        return send(request, type);
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json");
            HttpRequest request = body == null
                    ? builder.POST(HttpRequest.BodyPublishers.noBody()).build()
                    : builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
            return send(request, type);
        } catch (IOException exception) {
            throw new IllegalStateException("中央服务请求序列化失败", exception);
        }
    }

    private <T> T send(HttpRequest request, Class<T> type) {
        return parse(sendRaw(request), type);
    }

    private <T> T send(HttpRequest request, TypeReference<T> type) {
        String body = sendRaw(request);
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException exception) {
            throw new IllegalStateException("中央服务响应解析失败", exception);
        }
    }

    private String sendRaw(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("中央服务返回 " + response.statusCode() + "：" + response.body());
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("中央服务请求被中断", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("无法连接中央服务：" + baseUrl, exception);
        }
    }

    private <T> T parse(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException exception) {
            throw new IllegalStateException("中央服务响应解析失败", exception);
        }
    }
}
