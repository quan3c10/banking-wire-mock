#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "Starting WireMock Banking POC..."
docker-compose up -d

echo "Waiting for WireMock to be ready..."
for i in $(seq 1 30); do
  if curl -sf "http://localhost:${WIREMOCK_PORT:-8080}/__admin/health" > /dev/null 2>&1; then
    echo "WireMock is ready at http://localhost:${WIREMOCK_PORT:-8080}"
    echo "Admin UI: http://localhost:${WIREMOCK_PORT:-8080}/__admin/mappings"
    exit 0
  fi
  sleep 1
done

echo "ERROR: WireMock did not become ready in 30 seconds"
docker-compose logs wiremock
exit 1
