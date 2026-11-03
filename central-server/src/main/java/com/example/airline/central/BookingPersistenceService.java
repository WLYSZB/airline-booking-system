package com.example.airline.central;

import com.example.airline.model.Receipt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingPersistenceService {

    private final BookingRepository bookingRepository;
    private final IdempotencyRepository idempotencyRepository;

    public BookingPersistenceService(
            BookingRepository bookingRepository,
            IdempotencyRepository idempotencyRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.idempotencyRepository = idempotencyRepository;
    }

    @Transactional
    public Receipt persist(Receipt receipt, String idempotencyKey, String fingerprint) {
        bookingRepository.save(receipt, idempotencyKey);
        idempotencyRepository.complete(idempotencyKey, fingerprint, receipt.bookingReference());
        return receipt;
    }
}
