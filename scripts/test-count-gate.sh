#!/usr/bin/env bash
# Test-count gate: mirrors ci.yml "Verify test count meets minimum".
# Counts @Test / @ParameterizedTest annotations in common/src and
# forge-1.21/src; exits 1 if below the CI floor of 150, 0 otherwise.
#
# Usage: bash scripts/test-count-gate.sh

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

COUNT=$(grep -rE "@(Test|ParameterizedTest)\b" --include="*.java" common/src forge-1.21/src 2>/dev/null | wc -l)
echo "Test count: $COUNT"

if [ "$COUNT" -lt 150 ]; then
  echo "ERROR: Test count dropped below 150 (got $COUNT)" >&2
  exit 1
fi
