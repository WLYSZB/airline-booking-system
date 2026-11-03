-- Run as airline_c in an Oracle XE/19c+ account.
CREATE TABLE flight_instance (
    flight_instance_id VARCHAR2(64) PRIMARY KEY,
    airline_code VARCHAR2(8) NOT NULL,
    airline_name VARCHAR2(80) NOT NULL,
    flight_number VARCHAR2(20) NOT NULL,
    origin VARCHAR2(3) NOT NULL,
    destination VARCHAR2(3) NOT NULL,
    departure_time TIMESTAMP NOT NULL,
    arrival_time TIMESTAMP NOT NULL,
    fare NUMBER(10, 2) NOT NULL,
    available_seats NUMBER(5) NOT NULL,
    own_discount NUMBER(3) NOT NULL
);

CREATE INDEX idx_airline_flight_route
    ON flight_instance (origin, destination, departure_time);

CREATE TABLE flight_hold (
    hold_id VARCHAR2(36) PRIMARY KEY,
    booking_reference VARCHAR2(36) NOT NULL,
    flight_instance_id VARCHAR2(64) NOT NULL,
    passengers NUMBER(5) NOT NULL,
    status VARCHAR2(16) NOT NULL,
    seat_preference VARCHAR2(16) NOT NULL,
    idempotency_key VARCHAR2(160) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_hold_booking_flight UNIQUE (booking_reference, flight_instance_id),
    CONSTRAINT uq_hold_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_hold_flight FOREIGN KEY (flight_instance_id)
        REFERENCES flight_instance (flight_instance_id)
);
