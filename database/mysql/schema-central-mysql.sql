CREATE TABLE IF NOT EXISTS central_booking (
    booking_reference VARCHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL,
    status VARCHAR(24) NOT NULL,
    seat_preference VARCHAR(16) NOT NULL,
    total_price DECIMAL(12, 2) NOT NULL,
    same_airline_discount INT NOT NULL,
    pricing_note VARCHAR(200),
    message VARCHAR(300),
    created_at VARCHAR(40) NOT NULL,
    updated_at VARCHAR(40) NOT NULL,
    CONSTRAINT uq_booking_idempotency UNIQUE (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS booking_leg (
    booking_reference VARCHAR(36) NOT NULL,
    leg_no INT NOT NULL,
    airline_code VARCHAR(8) NOT NULL,
    flight_number VARCHAR(20) NOT NULL,
    flight_instance_id VARCHAR(64) NOT NULL,
    origin VARCHAR(3) NOT NULL,
    destination VARCHAR(3) NOT NULL,
    departure_time DATETIME NOT NULL,
    arrival_time DATETIME NOT NULL,
    fare DECIMAL(10, 2) NOT NULL,
    hold_id VARCHAR(36),
    status VARCHAR(16) NOT NULL,
    PRIMARY KEY (booking_reference, leg_no),
    CONSTRAINT fk_leg_booking FOREIGN KEY (booking_reference)
        REFERENCES central_booking (booking_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS booking_passenger (
    booking_reference VARCHAR(36) NOT NULL,
    passenger_no INT NOT NULL,
    passenger_name VARCHAR(120) NOT NULL,
    passport_number VARCHAR(60) NOT NULL,
    PRIMARY KEY (booking_reference, passenger_no),
    CONSTRAINT fk_passenger_booking FOREIGN KEY (booking_reference)
        REFERENCES central_booking (booking_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS booking_passenger_preference (
    booking_reference VARCHAR(36) NOT NULL,
    passenger_no INT NOT NULL,
    seat_preference VARCHAR(16) NOT NULL,
    PRIMARY KEY (booking_reference, passenger_no),
    CONSTRAINT fk_passenger_pref_booking FOREIGN KEY (booking_reference)
        REFERENCES central_booking (booking_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
