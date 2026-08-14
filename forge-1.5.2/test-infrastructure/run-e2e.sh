#!/usr/bin/env bash
# forge-1.5.2/test-infrastructure/run-e2e.sh — thin lane wrapper for the
# real-client E2E (master plan slice 1; shared logic lives in
# scripts/e2e/e2e-common.sh + scripts/e2e/drivers/pre18-xvfb.sh).
#
# Flow: build the lane (vendored harness, Java 8) → export the lane's
# server/artifact config → invoke the shared orchestrator. This lane is a
# MERGE-MODEL era (E2E_ERA=merge): the server boots the obf server jar with
# the forge universal.zip FIRST on the classpath (main
# net.minecraft.server.MinecraftServer nogui), and the client boots the obf
# client jar MERGED with the universal zip (main net.minecraft.client.Minecraft,
# CWD=gameDir, -Dminecraft.applet.TargetDirectory) — see
# scripts/e2e/e2e-common.sh + scripts/e2e/drivers/pre18-xvfb.sh era deltas.
#
# Usage: JAVA_HOME=<jdk8> ./run-e2e.sh
# Exit codes (master-plan contract): 0 all green | 1 assertion failed |
# 2 retryable infra | 3 build failure.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LANE_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(cd "$LANE_DIR/.." && pwd)"

# ---------------------------------------------------------------------------
# Java 8 (hard requirement: the merge-model client/server + Gradle 4.4.1 die
# on 9+)
# ---------------------------------------------------------------------------
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    E2E_JAVA8="$JAVA_HOME/bin/java"
elif [ -x "$HOME/.sdkman/candidates/java/8.0.472-amzn/bin/java" ]; then
    E2E_JAVA8="$HOME/.sdkman/candidates/java/8.0.472-amzn/bin/java"
elif [ -x /usr/lib/jvm/java-8-openjdk-amd64/bin/java ]; then
    E2E_JAVA8="/usr/lib/jvm/java-8-openjdk-amd64/bin/java"
else
    echo "[e2e:1.5.2][fail] Java 8 not found (set JAVA_HOME to a JDK 8)" >&2
    exit 3
fi
echo "[e2e:1.5.2] Java 8: $E2E_JAVA8"

# ---------------------------------------------------------------------------
# Build the lane
# ---------------------------------------------------------------------------
echo "[e2e:1.5.2] building lane..."
cd "$LANE_DIR"
if ! JAVA_HOME="$(dirname "$(dirname "$E2E_JAVA8")")" ./gradlew build --no-daemon; then
    echo "[e2e:1.5.2][fail] lane build failed (exit 3)" >&2
    exit 3
fi

MOD_JAR="$(ls build/libs/everlastingskins-*.jar 2>/dev/null | grep -v -- '-sources.jar' | head -1 || true)"
if [ -z "$MOD_JAR" ]; then
    echo "[e2e:1.5.2][fail] no mod jar in build/libs/" >&2
    exit 3
fi

# ---------------------------------------------------------------------------
# Vendored server jar (populated by the build's resolveVendored)
# ---------------------------------------------------------------------------
VENDORED_DIR="${E2E_VENDORED_DIR:-$HOME/.gradle/everlastingskins-vendored/1.5.2}"
SERVER_JAR="$VENDORED_DIR/minecraft_server.1.5.2.jar"
if [ ! -f "$SERVER_JAR" ]; then
    echo "[e2e:1.5.2][fail] vendored server jar missing: $SERVER_JAR" >&2
    exit 3
fi

# ---------------------------------------------------------------------------
# Invoke the shared orchestrator
# ---------------------------------------------------------------------------
export E2E_LANE="1.5.2"
export E2E_ERA="merge"
export E2E_MOD_JAR="$MOD_JAR"
export E2E_SERVER_JAR="$SERVER_JAR"
export E2E_DRIVER_SCRIPT="$REPO_ROOT/scripts/e2e/drivers/pre18-xvfb.sh"
export E2E_JAVA8
export E2E_SENTINEL_PNG="$REPO_ROOT/common/src/test/resources/e2e/sentinel-64x32.png"

exec bash "$REPO_ROOT/scripts/e2e/e2e-common.sh"
