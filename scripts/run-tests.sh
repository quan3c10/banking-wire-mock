#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

# Start WireMock
bash "$SCRIPT_DIR/start.sh"

# Run tests
echo ""
echo "Running test suite..."
cd "$PROJECT_DIR"
mvn test -f pom.xml
TEST_EXIT=$?

# Always stop WireMock after tests
bash "$SCRIPT_DIR/stop.sh"

exit $TEST_EXIT
