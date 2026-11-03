package com.example.airline.airline;

import org.springframework.http.HttpStatus;

public class AirlineServiceException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public AirlineServiceException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
