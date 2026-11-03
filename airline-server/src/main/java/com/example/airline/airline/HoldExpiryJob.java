package com.example.airline.airline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);
    private final AirlineRepository repository;

    public HoldExpiryJob(AirlineRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${airline.expiry-scan-ms:30000}")
    public void releaseExpiredHolds() {
        int released = repository.releaseExpired(LocalDateTime.now());
        if (released > 0) {
            log.info("Released {} expired holds", released);
        }
    }
}
