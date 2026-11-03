package com.example.airline.central;

import org.springframework.http.HttpStatus;

public class CentralServiceException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public CentralServiceException(HttpStatus status, String code, String message) {
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
