-- Replace CHANGE_ME before executing. Do not commit real passwords.
-- start-real-db.sh accepts one DB_PASSWORD, so all four accounts use the same password.
CREATE DATABASE IF NOT EXISTS airline_a CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS airline_b CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS airline_central CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'airline_a'@'%' IDENTIFIED BY 'CHANGE_ME';
CREATE USER IF NOT EXISTS 'airline_b'@'%' IDENTIFIED BY 'CHANGE_ME';
CREATE USER IF NOT EXISTS 'airline_central'@'%' IDENTIFIED BY 'CHANGE_ME';

GRANT ALL PRIVILEGES ON airline_a.* TO 'airline_a'@'%';
GRANT ALL PRIVILEGES ON airline_b.* TO 'airline_b'@'%';
GRANT ALL PRIVILEGES ON airline_central.* TO 'airline_central'@'%';
FLUSH PRIVILEGES;
