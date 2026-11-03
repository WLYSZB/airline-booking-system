#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT_MIXED="$(cygpath -m "$ROOT" 2>/dev/null || printf '%s' "$ROOT")"
RUNTIME="$ROOT/runtime"

is_alive() {
  local pid="$1"
  if [ "${OS:-}" = "Windows_NT" ] && command -v tasklist >/dev/null 2>&1; then
    tasklist //FI "PID eq $pid" //NH 2>/dev/null | grep -q "[[:space:]]$pid[[:space:]]"
  else
    kill -0 "$pid" 2>/dev/null
  fi
}

stop_pid() {
  local pid="$1"
  if [ "${OS:-}" = "Windows_NT" ] && command -v taskkill >/dev/null 2>&1; then
    taskkill //PID "$pid" //F >/dev/null 2>&1 || true
  else
    kill "$pid" 2>/dev/null || true
  fi
}

capture_pid() {
  local shell_pid="$1"
  if [ "${OS:-}" = "Windows_NT" ] && [ -x /usr/bin/ps ]; then
    local winpid
    winpid="$(/usr/bin/ps -W 2>/dev/null | awk -v p="$shell_pid" '$1 == p {print $4; exit}')"
    if [ -n "$winpid" ]; then
      printf '%s' "$winpid"
      return
    fi
  fi
  printf '%s' "$shell_pid"
}
LOGS="$ROOT/logs"
AIRLINE_JAR="$ROOT/airline-server/target/airline-server-1.0.0-SNAPSHOT.jar"
CENTRAL_JAR="$ROOT/central-server/target/central-server-1.0.0-SNAPSHOT.jar"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3308}"
ORACLE_HOST="${ORACLE_HOST:-localhost}"
ORACLE_PORT="${ORACLE_PORT:-1521}"
ORACLE_SERVICE="${ORACLE_SERVICE:-FREEPDB1}"
AIRLINE_PORT_A="${AIRLINE_PORT_A:-18081}"
AIRLINE_PORT_B="${AIRLINE_PORT_B:-18082}"
AIRLINE_PORT_C="${AIRLINE_PORT_C:-18083}"
CENTRAL_PORT="${CENTRAL_PORT:-8080}"

if [ -z "${DB_PASSWORD:-}" ]; then
  read -r -s -p "Database password (input hidden): " DB_PASSWORD
  echo
fi
if [ -z "$DB_PASSWORD" ]; then
  echo "DB_PASSWORD is required" >&2
  exit 1
fi

export AIRLINE_DB_PASSWORD="$DB_PASSWORD"
export CENTRAL_DB_PASSWORD="$DB_PASSWORD"

mkdir -p "$RUNTIME" "$LOGS"

check_pid_file() {
  local file="$1"
  if [ -f "$file" ]; then
    local pid
    pid="$(cat "$file")"
    if is_alive "$pid"; then
      echo "Existing process still running: $file (PID $pid). Run stop-all.sh first." >&2
      exit 1
    fi
    rm -f "$file"
  fi
}

check_pid_file "$RUNTIME/real-airline-a.pid"
check_pid_file "$RUNTIME/real-airline-b.pid"
check_pid_file "$RUNTIME/real-airline-c.pid"
check_pid_file "$RUNTIME/real-central.pid"

cleanup_on_exit() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    trap - EXIT
    for name in real-airline-a real-airline-b real-airline-c real-central; do
      pid_file="$RUNTIME/$name.pid"
      if [ -f "$pid_file" ]; then
        pid="$(cat "$pid_file")"
        if is_alive "$pid"; then
          stop_pid "$pid"
        fi
        rm -f "$pid_file"
      fi
    done
  fi
}
trap cleanup_on_exit EXIT

MYSQL_PARAMS="useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
ORACLE_URL="jdbc:oracle:thin:@//${ORACLE_HOST}:${ORACLE_PORT}/${ORACLE_SERVICE}"

echo "[1/4] Starting airline A on MySQL ${MYSQL_HOST}:${MYSQL_PORT}..."
java -jar "$AIRLINE_JAR" \
  --server.port="$AIRLINE_PORT_A" --airline.code=A --airline.name=楚天航空 --airline.own-discount=10 \
  --spring.profiles.active=mysql \
  --spring.datasource.url="jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/airline_a?${MYSQL_PARAMS}" \
  --spring.datasource.username=airline_a \
  --spring.sql.init.mode=always \
  --spring.sql.init.schema-locations="file:${ROOT_MIXED}/database/mysql/schema-airline-mysql.sql" \
  > "$LOGS/real-airline-a.log" 2>&1 &
echo "$(capture_pid "$!")" > "$RUNTIME/real-airline-a.pid"

echo "[2/4] Starting airline B on MySQL ${MYSQL_HOST}:${MYSQL_PORT}..."
java -jar "$AIRLINE_JAR" \
  --server.port="$AIRLINE_PORT_B" --airline.code=B --airline.name=东湖航空 --airline.own-discount=8 \
  --spring.profiles.active=mysql \
  --spring.datasource.url="jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/airline_b?${MYSQL_PARAMS}" \
  --spring.datasource.username=airline_b \
  --spring.sql.init.mode=always \
  --spring.sql.init.schema-locations="file:${ROOT_MIXED}/database/mysql/schema-airline-mysql.sql" \
  > "$LOGS/real-airline-b.log" 2>&1 &
echo "$(capture_pid "$!")" > "$RUNTIME/real-airline-b.pid"

echo "[3/4] Starting airline C on Oracle ${ORACLE_HOST}:${ORACLE_PORT}/${ORACLE_SERVICE}..."
ORACLE_INIT_ARGS=()
if [ "${ORACLE_SCHEMA_INIT:-false}" = "true" ]; then
  ORACLE_INIT_ARGS=(--spring.sql.init.mode=always
    --spring.sql.init.schema-locations="file:${ROOT_MIXED}/database/oracle/schema-airline-oracle.sql")
else
  ORACLE_INIT_ARGS=(--spring.sql.init.mode=never)
fi
java -jar "$AIRLINE_JAR" \
  --server.port="$AIRLINE_PORT_C" --airline.code=C --airline.name=长江航空 --airline.own-discount=12 \
  --spring.profiles.active=oracle \
  --spring.datasource.url="$ORACLE_URL" \
  --spring.datasource.username=airline_c \
  "${ORACLE_INIT_ARGS[@]}" \
  > "$LOGS/real-airline-c.log" 2>&1 &
echo "$(capture_pid "$!")" > "$RUNTIME/real-airline-c.pid"

echo "[4/4] Starting central server on MySQL ${MYSQL_HOST}:${MYSQL_PORT}..."
AIRLINE_A_URL="http://localhost:${AIRLINE_PORT_A}" \
AIRLINE_B_URL="http://localhost:${AIRLINE_PORT_B}" \
AIRLINE_C_URL="http://localhost:${AIRLINE_PORT_C}" \
java -jar "$CENTRAL_JAR" \
  --server.port="$CENTRAL_PORT" \
  --spring.profiles.active=mysql \
  --spring.datasource.url="jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/airline_central?${MYSQL_PARAMS}" \
  --spring.datasource.username=airline_central \
  --spring.sql.init.mode=always \
  --spring.sql.init.schema-locations="file:${ROOT_MIXED}/database/mysql/schema-central-mysql.sql" \
  > "$LOGS/real-central.log" 2>&1 &
echo "$(capture_pid "$!")" > "$RUNTIME/real-central.pid"

for port in "$AIRLINE_PORT_A" "$AIRLINE_PORT_B" "$AIRLINE_PORT_C" "$CENTRAL_PORT"; do
  ready=0
  for _ in $(seq 1 60); do
    if curl --noproxy '*' -fsS --max-time 1 "http://localhost:$port/health" > /dev/null 2>&1; then
      ready=1
      break
    fi
    sleep 1
  done
  if [ "$ready" -ne 1 ]; then
    echo "Service on port $port failed to start. Check $LOGS/real-*.log" >&2
    exit 1
  fi
done

echo
echo "All real-database services are ready:"
echo "  Thin Web Client : http://localhost:${CENTRAL_PORT}"
echo "  Airline A MySQL : http://localhost:${AIRLINE_PORT_A}/health"
echo "  Airline B MySQL : http://localhost:${AIRLINE_PORT_B}/health"
echo "  Airline C Oracle: http://localhost:${AIRLINE_PORT_C}/health"
echo "  Central MySQL   : http://localhost:${CENTRAL_PORT}/health"
