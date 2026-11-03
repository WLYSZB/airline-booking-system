package com.example.airline.airline;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

@ConfigurationProperties(prefix = "airline")
public class AirlineProperties {
    private String code = "A";
    private String name = "楚天航空";
    private int ownDiscount = 10;
    private int holdMinutes = 15;
    private LocalDate seedDate = LocalDate.of(2026, 11, 15);
    private long expiryScanMs = 30_000;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getOwnDiscount() {
        return ownDiscount;
    }

    public void setOwnDiscount(int ownDiscount) {
        this.ownDiscount = ownDiscount;
    }

    public int getHoldMinutes() {
        return holdMinutes;
    }

    public void setHoldMinutes(int holdMinutes) {
        this.holdMinutes = holdMinutes;
    }

    public LocalDate getSeedDate() {
        return seedDate;
    }

    public void setSeedDate(LocalDate seedDate) {
        this.seedDate = seedDate;
    }

    public long getExpiryScanMs() {
        return expiryScanMs;
    }

    public void setExpiryScanMs(long expiryScanMs) {
        this.expiryScanMs = expiryScanMs;
    }
}
