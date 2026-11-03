CREATE TABLE central_booking (
    booking_reference VARCHAR2(36) PRIMARY KEY,
    idempotency_key VARCHAR2(200) NOT NULL,
    status VARCHAR2(24) NOT NULL,
    seat_preference VARCHAR2(16) NOT NULL,
    total_price NUMBER(12, 2) NOT NULL,
    same_airline_discount NUMBER(1) NOT NULL,
    pricing_note VARCHAR2(200),
    message VARCHAR2(300),
    created_at VARCHAR2(40) NOT NULL,
    updated_at VARCHAR2(40) NOT NULL,
    CONSTRAINT uq_booking_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE booking_idempotency (
    idempotency_key VARCHAR2(200) NOT NULL,
    request_fingerprint VARCHAR2(64) NOT NULL,
    state VARCHAR2(16) NOT NULL,
    booking_reference VARCHAR2(36),
    created_at VARCHAR2(40) NOT NULL,
    updated_at VARCHAR2(40) NOT NULL,
    CONSTRAINT pk_booking_idempotency PRIMARY KEY (idempotency_key)
);

CREATE TABLE booking_leg (
    booking_reference VARCHAR2(36) NOT NULL,
    leg_no NUMBER(5) NOT NULL,
    airline_code VARCHAR2(8) NOT NULL,
    flight_number VARCHAR2(20) NOT NULL,
    flight_instance_id VARCHAR2(64) NOT NULL,
    origin VARCHAR2(3) NOT NULL,
    destination VARCHAR2(3) NOT NULL,
    departure_time TIMESTAMP NOT NULL,
    arrival_time TIMESTAMP NOT NULL,
    fare NUMBER(10, 2) NOT NULL,
    hold_id VARCHAR2(36),
    status VARCHAR2(16) NOT NULL,
    CONSTRAINT pk_booking_leg PRIMARY KEY (booking_reference, leg_no),
    CONSTRAINT fk_leg_booking FOREIGN KEY (booking_reference)
        REFERENCES central_booking (booking_reference)
);

CREATE TABLE booking_passenger (
    booking_reference VARCHAR2(36) NOT NULL,
    passenger_no NUMBER(5) NOT NULL,
    passenger_name VARCHAR2(120) NOT NULL,
    passport_number VARCHAR2(60) NOT NULL,
    CONSTRAINT pk_booking_passenger PRIMARY KEY (booking_reference, passenger_no),
    CONSTRAINT fk_passenger_booking FOREIGN KEY (booking_reference)
        REFERENCES central_booking (booking_reference)
);

CREATE TABLE booking_passenger_preference (
    booking_reference VARCHAR2(36) NOT NULL,
    passenger_no NUMBER(5) NOT NULL,
    seat_preference VARCHAR2(16) NOT NULL,
    CONSTRAINT pk_booking_passenger_pref PRIMARY KEY (booking_reference, passenger_no),
    CONSTRAINT fk_passenger_pref_booking FOREIGN KEY (booking_reference)
        REFERENCES central_booking (booking_reference)
);
