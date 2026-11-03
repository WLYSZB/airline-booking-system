#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mvn -q -f "$ROOT/pom.xml" -pl thick-client -am install -DskipTests
mvn -q -f "$ROOT/pom.xml" -pl thick-client exec:java \
  -Dexec.mainClass=com.example.airline.thick.ThickClientApplication \
  -Dcentral.url="${CENTRAL_URL:-http://localhost:8080}"
