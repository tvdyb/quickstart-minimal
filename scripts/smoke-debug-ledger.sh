#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
URL="${BASE_URL%/}/debug/ledger?sampleLimit=2&eventLimit=5"

echo "Checking: $URL"
RAW="$(curl -sS "$URL")"

if command -v jq >/dev/null 2>&1; then
  echo "$RAW" | jq '{
    timestamp,
    authMode,
    health,
    packageCount: .ledger.packageCount,
    templateCount: (.templates | length),
    activeTemplates: (.activeContracts | length),
    recentEvents: (.recentEvents | length)
  }'
else
  echo "$RAW"
fi

echo "Debug endpoint smoke check completed."
