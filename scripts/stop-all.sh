#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
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

for name in central airline-a airline-b airline-c real-central real-airline-a real-airline-b real-airline-c; do
  pid_file="$RUNTIME/$name.pid"
  if [ -f "$pid_file" ]; then
    pid="$(cat "$pid_file")"
    if is_alive "$pid"; then
      stop_pid "$pid"
      echo "Stopped $name (PID $pid)"
    else
      echo "Removed stale PID file for $name (PID $pid)"
    fi
    rm -f "$pid_file"
  fi
done
