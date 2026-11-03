package com.example.airline.central;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Repository
public class IdempotencyRepository {

    private static final Duration STALE_AFTER = Duration.ofMinutes(2);

    private final JdbcTemplate jdbc;

    public IdempotencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ClaimResult claim(String key, String fingerprint) {
        String now = Instant.now().toString();
        try {
            jdbc.update("""
                            INSERT INTO booking_idempotency
                                (idempotency_key, request_fingerprint, state, booking_reference, created_at, updated_at)
                            VALUES (?, ?, 'IN_PROGRESS', NULL, ?, ?)
                            """,
                    key, fingerprint, now, now);
            return new ClaimResult(ClaimStatus.ACQUIRED, null);
        } catch (DataIntegrityViolationException duplicate) {
            ClaimRow existing = find(key).orElseThrow(() -> duplicate);
            if (!existing.fingerprint().equals(fingerprint)) {
                return new ClaimResult(ClaimStatus.CONFLICT, null);
            }
            if ("COMPLETED".equals(existing.state())) {
                return existing.bookingReference() == null
                        ? new ClaimResult(ClaimStatus.CONFLICT, null)
                        : new ClaimResult(ClaimStatus.COMPLETED, existing.bookingReference());
            }
            if ("IN_PROGRESS".equals(existing.state())) {
                if (!isStale(existing.updatedAt())) {
                    return new ClaimResult(ClaimStatus.BUSY, null);
                }
                int updated = jdbc.update("""
                                UPDATE booking_idempotency
                                   SET state = 'IN_PROGRESS', updated_at = ?
                                 WHERE idempotency_key = ? AND state = 'IN_PROGRESS' AND updated_at = ?
                                """,
                        now, key, existing.updatedAt());
                return updated == 1
                        ? new ClaimResult(ClaimStatus.ACQUIRED, null)
                        : new ClaimResult(ClaimStatus.BUSY, null);
            }
            int updated = jdbc.update("""
                            UPDATE booking_idempotency
                               SET state = 'IN_PROGRESS', updated_at = ?
                             WHERE idempotency_key = ? AND state = 'FAILED'
                            """,
                    now, key);
            return updated == 1
                    ? new ClaimResult(ClaimStatus.ACQUIRED, null)
                    : new ClaimResult(ClaimStatus.BUSY, null);
        }
    }

    public void complete(String key, String fingerprint, String bookingReference) {
        int updated = jdbc.update("""
                        UPDATE booking_idempotency
                           SET state = 'COMPLETED', booking_reference = ?, updated_at = ?
                         WHERE idempotency_key = ? AND request_fingerprint = ? AND state = 'IN_PROGRESS'
                        """,
                bookingReference, Instant.now().toString(), key, fingerprint);
        if (updated != 1) {
            throw new IllegalStateException("幂等占位已完成或已失效：" + key);
        }
    }

    public void fail(String key, String fingerprint) {
        jdbc.update("""
                        UPDATE booking_idempotency
                           SET state = 'FAILED', updated_at = ?
                         WHERE idempotency_key = ? AND request_fingerprint = ? AND state = 'IN_PROGRESS'
                        """,
                Instant.now().toString(), key, fingerprint);
    }

    private Optional<ClaimRow> find(String key) {
        return jdbc.query("""
                        SELECT idempotency_key, request_fingerprint, state, booking_reference, updated_at
                          FROM booking_idempotency WHERE idempotency_key = ?
                        """,
                (rs, rowNum) -> new ClaimRow(
                        rs.getString("request_fingerprint"),
                        rs.getString("state"),
                        rs.getString("booking_reference"),
                        rs.getString("updated_at")
                ),
                key
        ).stream().findFirst();
    }

    private boolean isStale(String updatedAt) {
        try {
            return Instant.parse(updatedAt).plus(STALE_AFTER).isBefore(Instant.now());
        } catch (RuntimeException exception) {
            return true;
        }
    }

    public enum ClaimStatus {
        ACQUIRED,
        COMPLETED,
        CONFLICT,
        BUSY
    }

    public record ClaimResult(ClaimStatus status, String bookingReference) {
    }

    private record ClaimRow(
            String fingerprint,
            String state,
            String bookingReference,
            String updatedAt
    ) {
    }
}
