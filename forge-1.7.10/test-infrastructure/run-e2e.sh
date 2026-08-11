#!/usr/bin/env bash
# forge-1.7.10/test-infrastructure/run-e2e.sh — thin lane wrapper for the
# HeadlessMC real-client E2E (master plan slice 2; shared logic lives in
# scripts/e2e/e2e-common.sh + scripts/e2e/drivers/headlessmc.sh).
#
# Bridge lane: the client is driven by hmc-specifics (lexforge) — the
# scenario SENDs "/skin set Notch" through the console, and the server-side
# ES_E2E_SKIN sentinel is the primary assertion.
#
# Usage: JAVA_HOME=<jdk8> ./run-e2e.sh
# Exit codes (master-plan contract): 0 all green | 1 assertion failed |
# 2 retryable infra | 3 build failure.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LANE_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(cd "$LANE_DIR/.." && pwd)"

# Java 8 (hard requirement: launchwrapper + Gradle 4.4.1 die on 9+)
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    E2E_JAVA8="$JAVA_HOME/bin/java"
elif [ -x "$HOME/.sdkman/candidates/java/8.0.472-amzn/bin/java" ]; then
    E2E_JAVA8="$HOME/.sdkman/candidates/java/8.0.472-amzn/bin/java"
elif [ -x /usr/lib/jvm/java-8-openjdk-amd64/bin/java ]; then
    E2E_JAVA8="/usr/lib/jvm/java-8-openjdk-amd64/bin/java"
else
    echo "[e2e:1.7.10][fail] Java 8 not found (set JAVA_HOME to a JDK 8)" >&2
    exit 3
fi
echo "[e2e:1.7.10] Java 8: $E2E_JAVA8"

# Build the lane
echo "[e2e:1.7.10] building lane..."
cd "$LANE_DIR"
if ! JAVA_HOME="$(dirname "$(dirname "$E2E_JAVA8")")" ./gradlew build --no-daemon; then
    echo "[e2e:1.7.10][fail] lane build failed (exit 3)" >&2
    exit 3
fi

MOD_JAR="$(ls build/libs/everlastingskins-*.jar 2>/dev/null | grep -v -- '-sources.jar' | head -1 || true)"
if [ -z "$MOD_JAR" ]; then
    echo "[e2e:1.7.10][fail] no mod jar in build/libs/" >&2
    exit 3
fi

# Invoke the shared orchestrator (headlessmc era)
export E2E_LANE="1.7.10"
export E2E_ERA="headlessmc"
export E2E_MOD_JAR="$MOD_JAR"
export E2E_DRIVER_SCRIPT="$REPO_ROOT/scripts/e2e/drivers/headlessmc.sh"
export E2E_JAVA8

exec bash "$REPO_ROOT/scripts/e2e/e2e-common.sh"
