-- Execute as SYSDBA inside the target PDB (for example FREEPDB1).
-- Replace CHANGE_ME before use. Keep it identical to DB_PASSWORD for the MySQL accounts.
CREATE USER airline_c IDENTIFIED BY "CHANGE_ME";
GRANT CONNECT, RESOURCE TO airline_c;
ALTER USER airline_c QUOTA UNLIMITED ON USERS;

CREATE USER airline_central IDENTIFIED BY "CHANGE_ME";
GRANT CONNECT, RESOURCE TO airline_central;
ALTER USER airline_central QUOTA UNLIMITED ON USERS;
