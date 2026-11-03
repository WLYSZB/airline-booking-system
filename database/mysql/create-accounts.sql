-- Replace CHANGE_ME before executing. Do not commit real passwords.
CREATE DATABASE IF NOT EXISTS airline_a CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS airline_b CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS airline_central CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'airline_a'@'%' IDENTIFIED BY 'CHANGE_ME_A';
CREATE USER IF NOT EXISTS 'airline_b'@'%' IDENTIFIED BY 'CHANGE_ME_B';
CREATE USER IF NOT EXISTS 'airline_central'@'%' IDENTIFIED BY 'CHANGE_ME_CENTRAL';

GRANT ALL PRIVILEGES ON airline_a.* TO 'airline_a'@'%';
GRANT ALL PRIVILEGES ON airline_b.* TO 'airline_b'@'%';
GRANT ALL PRIVILEGES ON airline_central.* TO 'airline_central'@'%';
FLUSH PRIVILEGES;
