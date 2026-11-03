package com.example.airline.central;

import com.example.airline.model.BookedLeg;
import com.example.airline.model.BookingStatus;
import com.example.airline.model.Passenger;
import com.example.airline.model.Receipt;
import com.example.airline.model.SeatPreference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class BookingRepository {

    private final JdbcTemplate jdbc;

    public BookingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void save(Receipt receipt, String idempotencyKey) {
        String now = Instant.now().toString();
        jdbc.update("""
                        INSERT INTO central_booking
                            (booking_reference, idempotency_key, status, seat_preference, total_price,
                             same_airline_discount, pricing_note, message, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                receipt.bookingReference(),
                idempotencyKey,
                receipt.status().name(),
                summarizeSeatPreference(receipt.passengers()).name(),
                receipt.totalPrice(),
                receipt.sameAirlineDiscount() ? 1 : 0,
                receipt.pricingNote(),
                receipt.message(),
                receipt.createdAt().toString(),
                now);
        for (int i = 0; i < receipt.legs().size(); i++) {
            BookedLeg leg = receipt.legs().get(i);
            jdbc.update("""
                            INSERT INTO booking_leg
                                (booking_reference, leg_no, airline_code, flight_number, flight_instance_id,
                                 origin, destination, departure_time, arrival_time, fare, hold_id, status)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    receipt.bookingReference(), i + 1, leg.airlineCode(), leg.flightNumber(),
                    leg.flightInstanceId(), leg.origin(), leg.destination(), leg.departureTime(),
                    leg.arrivalTime(), leg.fare(), leg.holdId(), leg.status());
        }
        for (int i = 0; i < receipt.passengers().size(); i++) {
            Passenger passenger = receipt.passengers().get(i);
            jdbc.update("""
                            INSERT INTO booking_passenger
                                (booking_reference, passenger_no, passenger_name, passport_number)
                            VALUES (?, ?, ?, ?)
                            """,
                    receipt.bookingReference(), i + 1, passenger.name(), passenger.passportNumber());
            jdbc.update("""
                            INSERT INTO booking_passenger_preference
                                (booking_reference, passenger_no, seat_preference)
                            VALUES (?, ?, ?)
                            """,
                    receipt.bookingReference(), i + 1, passenger.seatPreference().name());
        }
    }

    private SeatPreference summarizeSeatPreference(List<Passenger> passengers) {
        SeatPreference first = passengers.get(0).seatPreference();
        boolean allSame = passengers.stream().allMatch(passenger -> passenger.seatPreference() == first);
        return allSame ? first : SeatPreference.MIXED;
    }

    public Optional<Receipt> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.query("""
                        SELECT booking_reference, status, seat_preference, total_price,
                               same_airline_discount, pricing_note, message, created_at
                          FROM central_booking WHERE idempotency_key = ?
                        """,
                this::mapReceiptHead,
                idempotencyKey
        ).stream().findFirst().map(this::attachDetails);
    }

    public Optional<Receipt> find(String bookingReference) {
        return jdbc.query("""
                        SELECT booking_reference, status, seat_preference, total_price,
                               same_airline_discount, pricing_note, message, created_at
                          FROM central_booking WHERE booking_reference = ?
                        """,
                this::mapReceiptHead,
                bookingReference
        ).stream().findFirst().map(this::attachDetails);
    }

    public List<String> findReferencesByStatus(BookingStatus status) {
        return jdbc.query(
                "SELECT booking_reference FROM central_booking WHERE status = ? ORDER BY updated_at",
                (rs, rowNum) -> rs.getString("booking_reference"),
                status.name()
        );
    }

    @Transactional
    public void updateStatus(String bookingReference, BookingStatus status, String message) {
        int updated = jdbc.update("""
                        UPDATE central_booking
                           SET status = ?, message = ?, updated_at = ?
                         WHERE booking_reference = ?
                        """,
                status.name(), message, Instant.now().toString(), bookingReference);
        if (updated != 1) {
            throw new CentralServiceException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND",
                    "预订不存在：" + bookingReference);
        }
        if (status == BookingStatus.CANCELLED) {
            jdbc.update("UPDATE booking_leg SET status = 'CANCELLED' WHERE booking_reference = ?",
                    bookingReference);
        }
    }

    private ReceiptHead mapReceiptHead(ResultSet rs, int rowNum) throws SQLException {
        return new ReceiptHead(
                rs.getString("booking_reference"),
                BookingStatus.valueOf(rs.getString("status")),
                SeatPreference.valueOf(rs.getString("seat_preference")),
                rs.getBigDecimal("total_price"),

                rs.getInt("same_airline_discount") == 1,
                rs.getString("pricing_note"),
                rs.getString("message"),
                Instant.parse(rs.getString("created_at"))
        );
    }

    private Receipt attachDetails(ReceiptHead head) {
        List<BookedLeg> legs = jdbc.query("""
                        SELECT airline_code, flight_number, flight_instance_id, origin, destination,
                               departure_time, arrival_time, fare, hold_id, status
                          FROM booking_leg WHERE booking_reference = ? ORDER BY leg_no
                        """,
                (rs, rowNum) -> new BookedLeg(
                        rs.getString("airline_code"),
                        rs.getString("flight_number"),
                        rs.getString("flight_instance_id"),
                        rs.getString("origin"),
                        rs.getString("destination"),
                        rs.getTimestamp("departure_time").toLocalDateTime(),
                        rs.getTimestamp("arrival_time").toLocalDateTime(),
                        rs.getBigDecimal("fare"),
                        rs.getString("hold_id"),
                        rs.getString("status")
                ),
                head.bookingReference());
        List<Passenger> passengers = jdbc.query("""
                        SELECT p.passenger_name, p.passport_number,
                               pref.seat_preference AS seat_preference
                          FROM booking_passenger p
                          LEFT JOIN booking_passenger_preference pref
                            ON pref.booking_reference = p.booking_reference
                           AND pref.passenger_no = p.passenger_no
                         WHERE p.booking_reference = ?
                         ORDER BY p.passenger_no
                        """,
                (rs, rowNum) -> {
                    String preference = rs.getString("seat_preference");
                    SeatPreference seatPreference = preference == null
                            ? head.legacySeatPreference()
                            : SeatPreference.valueOf(preference);
                    return new Passenger(
                            rs.getString("passenger_name"),
                            rs.getString("passport_number"),
                            seatPreference
                    );
                },
                head.bookingReference());
        return new Receipt(
                head.bookingReference(),
                head.status(),
                legs,
                passengers,
                head.totalPrice(),
                head.sameAirlineDiscount(),
                head.pricingNote(),
                head.createdAt(),
                head.message()
        );
    }

    private record ReceiptHead(
            String bookingReference,
            BookingStatus status,
            SeatPreference legacySeatPreference,
            BigDecimal totalPrice,
            boolean sameAirlineDiscount,
            String pricingNote,
            String message,
            Instant createdAt
    ) {
    }
}
