-- Run this script against each airline account/schema:
-- airline_a and airline_b use MySQL; airline_c may use the Oracle counterpart.
CREATE TABLE IF NOT EXISTS flight_instance (
    flight_instance_id VARCHAR(64) PRIMARY KEY,
    airline_code VARCHAR(8) NOT NULL,
    airline_name VARCHAR(80) NOT NULL,
    flight_number VARCHAR(20) NOT NULL,
    origin VARCHAR(3) NOT NULL,
    destination VARCHAR(3) NOT NULL,
    departure_time DATETIME NOT NULL,
    arrival_time DATETIME NOT NULL,
    fare DECIMAL(10, 2) NOT NULL,
    available_seats INT NOT NULL,
    own_discount INT NOT NULL,
    INDEX idx_flight_route_date (origin, destination, departure_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS flight_hold (
    hold_id VARCHAR(36) PRIMARY KEY,
    booking_reference VARCHAR(36) NOT NULL,
    flight_instance_id VARCHAR(64) NOT NULL,
    passengers INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    seat_preference VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    CONSTRAINT uq_hold_booking_flight UNIQUE (booking_reference, flight_instance_id),
    CONSTRAINT uq_hold_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_hold_flight FOREIGN KEY (flight_instance_id)
        REFERENCES flight_instance (flight_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
