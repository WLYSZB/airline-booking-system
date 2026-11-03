#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUNTIME="$ROOT/runtime"
LOGS="$ROOT/logs"
AIRLINE_JAR="$ROOT/airline-server/target/airline-server-1.0.0-SNAPSHOT.jar"
CENTRAL_JAR="$ROOT/central-server/target/central-server-1.0.0-SNAPSHOT.jar"

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

mkdir -p "$RUNTIME/airline-a" "$RUNTIME/airline-b" "$RUNTIME/airline-c" "$RUNTIME/central" "$LOGS"

echo "[1/3] Building project..."
mvn -q -f "$ROOT/pom.xml" package -DskipTests

echo "[2/3] Starting three airline servers..."
java -jar "$AIRLINE_JAR" --server.port=8081 --airline.code=A --airline.name=楚天航空 --airline.own-discount=10 \
  --spring.datasource.url="jdbc:h2:file:$RUNTIME/airline-a/db" \
  > "$LOGS/airline-a.log" 2>&1 &
echo "$(capture_pid "$!")" > "$RUNTIME/airline-a.pid"

java -jar "$AIRLINE_JAR" --server.port=8082 --airline.code=B --airline.name=东湖航空 --airline.own-discount=8 \
  --spring.datasource.url="jdbc:h2:file:$RUNTIME/airline-b/db" \
  > "$LOGS/airline-b.log" 2>&1 &
echo "$(capture_pid "$!")" > "$RUNTIME/airline-b.pid"

java -jar "$AIRLINE_JAR" --server.port=8083 --airline.code=C --airline.name=长江航空 --airline.own-discount=12 \
  --spring.datasource.url="jdbc:h2:file:$RUNTIME/airline-c/db" \
  > "$LOGS/airline-c.log" 2>&1 &
echo "$(capture_pid "$!")" > "$RUNTIME/airline-c.pid"

echo "[3/3] Starting central server..."
java -jar "$CENTRAL_JAR" --server.port=8080 \
  --spring.datasource.url="jdbc:h2:file:$RUNTIME/central/db" \
  > "$LOGS/central.log" 2>&1 &
echo "$(capture_pid "$!")" > "$RUNTIME/central.pid"

for port in 8081 8082 8083 8080; do
  ready=0
  for _ in $(seq 1 45); do
    if curl --noproxy '*' -fsS --max-time 1 "http://localhost:$port/health" > /dev/null 2>&1; then
      ready=1
      break
    fi
    sleep 1
  done
  if [ "$ready" -ne 1 ]; then
    echo "Service on port $port failed to start. Check $LOGS" >&2
    exit 1
  fi
done

echo
echo "All services are ready:"
echo "  Thin Web Client : http://localhost:8080"
echo "  Central Server  : http://localhost:8080/health"
echo "  Airline A       : http://localhost:8081/health"
echo "  Airline B       : http://localhost:8082/health"
echo "  Airline C       : http://localhost:8083/health"
echo
echo "Run thick client: bash scripts/run-thick-client.sh"
echo "Run smoke test  : python3 scripts/smoke_test.py"
echo "Stop all        : bash scripts/stop-all.sh"
