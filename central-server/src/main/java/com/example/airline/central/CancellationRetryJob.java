package com.example.airline.central;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CancellationRetryJob {

    private static final Logger log = LoggerFactory.getLogger(CancellationRetryJob.class);

    private final BookingService bookingService;

    public CancellationRetryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelayString = "${central.cancellation-retry-ms:30000}")
    public void retryCancellations() {
        int recovered = bookingService.retryPendingCancellations();
        if (recovered > 0) {
            log.info("Recovered {} CANCEL_PENDING bookings", recovered);
        }
    }
}
