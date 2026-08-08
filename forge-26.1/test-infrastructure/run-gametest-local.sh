#!/usr/bin/env bash
# Run the Forge game test server locally with fail-fast output.
# The game test server runs every test registered in the enabled namespace;
# there is no per-test CLI filter (it is a JavaExec task, not a Test task).
#
# Usage: ./test-infrastructure/run-gametest-local.sh [namespace]
#   namespace defaults to everlastingskins (see build.gradle runs.gameTestServer)

set -uo pipefail

BRANCH_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$BRANCH_DIR"

NAMESPACE="${1:-everlastingskins}"
LOG_FILE="${TMPDIR:-/tmp}/gametest-output.log"

echo "=== Running game test server (namespace: $NAMESPACE) ==="
echo "=== Java: $(java -version 2>&1 | head -1) ==="

./gradlew :forge-26.1:runGameTestServer \
    -PgametestNamespace="$NAMESPACE" \
    --console=plain \
    --stacktrace \
    2>&1 | tee "$LOG_FILE"

EXIT_CODE=${PIPESTATUS[0]}

if [ $EXIT_CODE -ne 0 ]; then
    echo ""
    echo "=== GameTest FAILED (exit $EXIT_CODE) ==="
    echo "--- Failed tests / last log lines ---"
    grep -E "FAILED|failed|ERROR|Missing test structure|No test functions|GameTestAssert" "$LOG_FILE" | tail -30
    echo "--- Tail of output ---"
    tail -60 "$LOG_FILE"
    exit $EXIT_CODE
fi

echo ""
echo "=== GameTest PASSED ==="
