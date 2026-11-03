package com.example.airline.airline;

import com.example.airline.model.FlightOffer;
import com.example.airline.model.HoldRequest;
import com.example.airline.model.HoldResponse;
import com.example.airline.model.ReservationDetails;
import com.example.airline.model.ReservationStatus;
import com.example.airline.model.ReservedSegment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AirlineRepository {

    private final JdbcTemplate jdbc;
    private final AirlineProperties properties;

    public AirlineRepository(JdbcTemplate jdbc, AirlineProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public List<FlightOffer> search(String origin, String destination, LocalDate date, int passengers) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return jdbc.query("""
                        SELECT flight_instance_id, airline_code, airline_name, flight_number,
                               origin, destination, departure_time, arrival_time, fare,
                               available_seats, own_discount
                          FROM flight_instance
                         WHERE (origin = ? OR destination = ?)
                           AND departure_time >= ?
                           AND departure_time < ?
                           AND available_seats >= ?
                         ORDER BY departure_time, fare
                        """,
                this::mapFlight,
                origin, destination, start, end, passengers);
    }

    @Transactional
    public HoldResponse hold(HoldRequest request, LocalDateTime now) {
        if (request.passengers() < 1) {
            throw new AirlineServiceException(HttpStatus.BAD_REQUEST, "INVALID_PASSENGERS", "乘客人数必须大于 0");
        }
        Optional<HoldRow> existing = findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            HoldRow row = existing.get();
            if (!row.bookingReference().equals(request.bookingReference())) {
                throw new AirlineServiceException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                        "幂等键已被其他预订使用");
            }
            return new HoldResponse(row.holdId(), row.status(), row.fare(), row.passengers());
        }

        Optional<BigDecimal> fare = jdbc.query(
                "SELECT fare FROM flight_instance WHERE flight_instance_id = ?",
                (rs, rowNum) -> rs.getBigDecimal("fare"),
                request.flightInstanceId()
        ).stream().findFirst();
        if (fare.isEmpty()) {
            throw new AirlineServiceException(HttpStatus.NOT_FOUND, "FLIGHT_NOT_FOUND",
                    "航班不存在：" + request.flightInstanceId());
        }

        int updated = jdbc.update("""
                        UPDATE flight_instance
                           SET available_seats = available_seats - ?
                         WHERE flight_instance_id = ?
                           AND available_seats >= ?
                        """,
                request.passengers(), request.flightInstanceId(), request.passengers());
        if (updated != 1) {
            throw new AirlineServiceException(HttpStatus.CONFLICT, "SEATS_UNAVAILABLE",
                    "余座不足，无法预占 " + request.passengers() + " 个座位");
        }

        String holdId = UUID.randomUUID().toString();
        jdbc.update("""
                        INSERT INTO flight_hold
                            (hold_id, booking_reference, flight_instance_id, passengers, status,
                             seat_preference, idempotency_key, created_at, expires_at)
                        VALUES (?, ?, ?, ?, 'HELD', ?, ?, ?, ?)
                        """,
                holdId,
                request.bookingReference(),
                request.flightInstanceId(),
                request.passengers(),
                request.seatPreference().name(),
                request.idempotencyKey(),
                now,
                now.plusMinutes(properties.getHoldMinutes())
        );
        return new HoldResponse(holdId, "HELD", fare.get(), request.passengers());
    }

    @Transactional
    public HoldResponse confirm(String holdId) {
        HoldRow row = requireHold(holdId);
        if ("CONFIRMED".equals(row.status())) {
            return new HoldResponse(row.holdId(), row.status(), row.fare(), row.passengers());
        }
        if ("CANCELLED".equals(row.status())) {
            throw new AirlineServiceException(HttpStatus.CONFLICT, "HOLD_CANCELLED", "该预占已取消");
        }
        int updated = jdbc.update(
                "UPDATE flight_hold SET status = 'CONFIRMED' WHERE hold_id = ? AND status = 'HELD'",
                holdId);
        if (updated != 1) {
            throw new AirlineServiceException(HttpStatus.CONFLICT, "HOLD_STATE_CHANGED", "预占状态已变化");
        }
        return new HoldResponse(row.holdId(), "CONFIRMED", row.fare(), row.passengers());
    }

    @Transactional
    public void release(String holdId) {
        HoldRow row = requireHold(holdId);
        releaseInternal(row);
    }

    @Transactional
    public void cancelBooking(String bookingReference) {
        List<HoldRow> rows = jdbc.query("""
                        SELECT h.hold_id, h.booking_reference, h.flight_instance_id, h.passengers,
                               h.status, f.fare
                          FROM flight_hold h
                          JOIN flight_instance f ON f.flight_instance_id = h.flight_instance_id
                         WHERE h.booking_reference = ?
                           AND h.status IN ('HELD', 'CONFIRMED')
                        """,
                this::mapHold,
                bookingReference);
        rows.forEach(this::releaseInternal);
    }

    public ReservationStatus reservationStatus(String bookingReference) {
        List<String> statuses = jdbc.query(
                "SELECT status FROM flight_hold WHERE booking_reference = ?",
                (rs, rowNum) -> rs.getString("status"),
                bookingReference);
        if (statuses.isEmpty()) {
            return new ReservationStatus(bookingReference, properties.getCode(), "NOT_FOUND");
        }
        String aggregate;
        if (statuses.stream().anyMatch("CONFIRMED"::equals)) {
            aggregate = "CONFIRMED";
        } else if (statuses.stream().anyMatch("HELD"::equals)) {
            aggregate = "HELD";
        } else {
            aggregate = "CANCELLED";
        }
        return new ReservationStatus(bookingReference, properties.getCode(), aggregate);
    }

    public ReservationDetails reservationDetails(String bookingReference) {
        List<ReservedSegment> segments = jdbc.query("""
                        SELECT h.hold_id, h.flight_instance_id, h.status AS hold_status, h.passengers,
                               f.airline_code, f.flight_number, f.origin, f.destination,
                               f.departure_time, f.arrival_time, f.fare
                          FROM flight_hold h
                          JOIN flight_instance f ON f.flight_instance_id = h.flight_instance_id
                         WHERE h.booking_reference = ?
                         ORDER BY f.departure_time
                        """,
                (rs, rowNum) -> new ReservedSegment(
                        rs.getString("hold_id"),
                        rs.getString("flight_instance_id"),
                        rs.getString("airline_code"),
                        rs.getString("flight_number"),
                        rs.getString("origin"),
                        rs.getString("destination"),
                        rs.getTimestamp("departure_time").toLocalDateTime(),
                        rs.getTimestamp("arrival_time").toLocalDateTime(),
                        rs.getBigDecimal("fare"),
                        rs.getInt("passengers"),
                        rs.getString("hold_status")
                ),
                bookingReference);
        String status = segments.isEmpty()
                ? "NOT_FOUND"
                : segments.stream().allMatch(segment -> "CANCELLED".equals(segment.status()))
                ? "CANCELLED"
                : segments.stream().allMatch(segment -> "CONFIRMED".equals(segment.status()))
                ? "CONFIRMED"
                : "HELD";
        return new ReservationDetails(bookingReference, properties.getCode(), status, segments);
    }

    @Transactional
    public int releaseExpired(LocalDateTime now) {
        List<HoldRow> expired = jdbc.query("""
                        SELECT h.hold_id, h.booking_reference, h.flight_instance_id, h.passengers,
                               h.status, f.fare
                          FROM flight_hold h
                          JOIN flight_instance f ON f.flight_instance_id = h.flight_instance_id
                         WHERE h.status = 'HELD' AND h.expires_at < ?
                        """,
                this::mapHold,
                now);
        expired.forEach(this::releaseInternal);
        return expired.size();
    }

    private void releaseInternal(HoldRow row) {
        if ("CANCELLED".equals(row.status())) {
            return;
        }
        int updated;
        if ("CONFIRMED".equals(row.status())) {
            updated = jdbc.update(
                    "UPDATE flight_hold SET status = 'CANCELLED' WHERE hold_id = ? AND status = 'CONFIRMED'",
                    row.holdId());
        } else {
            updated = jdbc.update(
                    "UPDATE flight_hold SET status = 'CANCELLED' WHERE hold_id = ? AND status = 'HELD'",
                    row.holdId());
        }
        if (updated == 1) {
            jdbc.update(
                    "UPDATE flight_instance SET available_seats = available_seats + ? WHERE flight_instance_id = ?",
                    row.passengers(), row.flightInstanceId());
        }
    }

    private HoldRow requireHold(String holdId) {
        return jdbc.query("""
                        SELECT h.hold_id, h.booking_reference, h.flight_instance_id, h.passengers,
                               h.status, f.fare
                          FROM flight_hold h
                          JOIN flight_instance f ON f.flight_instance_id = h.flight_instance_id
                         WHERE h.hold_id = ?
                        """,
                this::mapHold,
                holdId
        ).stream().findFirst().orElseThrow(() -> new AirlineServiceException(
                HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND", "预占不存在：" + holdId));
    }

    private Optional<HoldRow> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query("""
                        SELECT h.hold_id, h.booking_reference, h.flight_instance_id, h.passengers,
                               h.status, f.fare
                          FROM flight_hold h
                          JOIN flight_instance f ON f.flight_instance_id = h.flight_instance_id
                         WHERE h.idempotency_key = ?
                        """,
                this::mapHold,
                idempotencyKey
        ).stream().findFirst();
    }

    private FlightOffer mapFlight(ResultSet rs, int rowNum) throws SQLException {
        return new FlightOffer(
                rs.getString("flight_instance_id"),
                rs.getString("airline_code"),
                rs.getString("airline_name"),
                rs.getString("flight_number"),
                rs.getString("origin"),
                rs.getString("destination"),
                rs.getTimestamp("departure_time").toLocalDateTime(),
                rs.getTimestamp("arrival_time").toLocalDateTime(),
                rs.getBigDecimal("fare"),
                rs.getInt("available_seats"),
                rs.getInt("own_discount")
        );
    }

    private HoldRow mapHold(ResultSet rs, int rowNum) throws SQLException {
        return new HoldRow(
                rs.getString("hold_id"),
                rs.getString("booking_reference"),
                rs.getString("flight_instance_id"),
                rs.getInt("passengers"),
                rs.getString("status"),
                rs.getBigDecimal("fare")
        );
    }

    private record HoldRow(
            String holdId,
            String bookingReference,
            String flightInstanceId,
            int passengers,
            String status,
            BigDecimal fare
    ) {
    }
}
