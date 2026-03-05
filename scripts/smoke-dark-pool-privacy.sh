#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"

check_status() {
  local path="$1"
  local expected="$2"
  local code
  code="$(curl -sS -o /tmp/qsmin-smoke-body.out -w "%{http_code}" "${BASE_URL%/}${path}")"
  if [[ "$code" != "$expected" ]]; then
    echo "FAIL ${path}: expected ${expected}, got ${code}"
    echo "Body:"
    cat /tmp/qsmin-smoke-body.out
    exit 1
  fi
  echo "OK   ${path}: ${code}"
}

echo "Checking unauthenticated privacy boundaries at ${BASE_URL%/} ..."
check_status "/orderbook" "401"
check_status "/trades/me" "401"
check_status "/positions/me" "401"

echo "Smoke check completed."
