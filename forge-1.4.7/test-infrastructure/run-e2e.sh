#!/usr/bin/env bash
# forge-1.4.7/test-infrastructure/run-e2e.sh — thin lane wrapper for the
# real-client E2E (master plan slice 1; shared logic lives in
# scripts/e2e/e2e-common.sh + scripts/e2e/drivers/pre18-xvfb.sh).
#
# Flow: build the lane (vendored harness, Java 8) → export the lane's
# server/artifact config → invoke the shared orchestrator.
#
# MERGE MODEL (lib-8 recipe): 1.4.7/1.5.2 boot the client as the obf client
# jar with the FML universal zip MERGED in (universal's patched
# net/minecraft/client/Minecraft + MinecraftApplet + ClientBrandRetriever
# win), main net.minecraft.client.Minecraft, CWD + minecraft.applet
# TargetDirectory = gameDir, NO launchwrapper/tweaker — the driver
# auto-connects via Minecraft.setServer at @Mod.Init. The server boots as
# net.minecraft.server.MinecraftServer nogui with the universal zip FIRST on
# the classpath (its patched MinecraftServer FML-bootstraps the process).
#
# Usage: JAVA_HOME=<jdk8> ./run-e2e.sh
# Exit codes (master-plan contract): 0 all green | 1 assertion failed |
# 2 retryable infra | 3 build/hard failure.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LANE_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(cd "$LANE_DIR/.." && pwd)"

# ---------------------------------------------------------------------------
# Java 8 (hard requirement: Gradle 4.4.1 dies on 9+; the merge-model client
# boot needs Java 8 too — the client bytecode is major 49 but FML 4.7's ASM
# refuses anything newer than Java 7 class files, and :common needs 8)
# ---------------------------------------------------------------------------
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    E2E_JAVA8="$JAVA_HOME/bin/java"
elif [ -x "$HOME/.sdkman/candidates/java/8.0.472-amzn/bin/java" ]; then
    E2E_JAVA8="$HOME/.sdkman/candidates/java/8.0.472-amzn/bin/java"
elif [ -x /usr/lib/jvm/java-8-openjdk-amd64/bin/java ]; then
    E2E_JAVA8="/usr/lib/jvm/java-8-openjdk-amd64/bin/java"
else
    echo "[e2e:1.4.7][fail] Java 8 not found (set JAVA_HOME to a JDK 8)" >&2
    exit 3
fi
echo "[e2e:1.4.7] Java 8: $E2E_JAVA8"

# ---------------------------------------------------------------------------
# Build the lane
# ---------------------------------------------------------------------------
echo "[e2e:1.4.7] building lane..."
cd "$LANE_DIR"
if ! JAVA_HOME="$(dirname "$(dirname "$E2E_JAVA8")")" ./gradlew build --no-daemon; then
    echo "[e2e:1.4.7][fail] lane build failed (exit 3)" >&2
    exit 3
fi

MOD_JAR="$(ls build/libs/everlastingskins-*.jar 2>/dev/null | grep -v -- '-sources.jar' | head -1 || true)"
if [ -z "$MOD_JAR" ]; then
    echo "[e2e:1.4.7][fail] no mod jar in build/libs/" >&2
    exit 3
fi

# ---------------------------------------------------------------------------
# Vendored server jar (populated by the build's resolveVendored)
# ---------------------------------------------------------------------------
VENDORED_DIR="${E2E_VENDORED_DIR:-$HOME/.gradle/everlastingskins-vendored/1.4.7}"
SERVER_JAR="$VENDORED_DIR/minecraft_server.1.4.7.jar"
if [ ! -f "$SERVER_JAR" ]; then
    echo "[e2e:1.4.7][fail] vendored server jar missing: $SERVER_JAR" >&2
    exit 3
fi

# ---------------------------------------------------------------------------
# Invoke the shared orchestrator (merge model)
# ---------------------------------------------------------------------------
export E2E_LANE="1.4.7"
export E2E_ERA="merge"
export E2E_MOD_JAR="$MOD_JAR"
export E2E_SERVER_JAR="$SERVER_JAR"
export E2E_DRIVER_SCRIPT="$REPO_ROOT/scripts/e2e/drivers/pre18-xvfb.sh"
export E2E_JAVA8
export E2E_SENTINEL_PNG="$REPO_ROOT/common/src/test/resources/e2e/sentinel-64x32.png"

exec bash "$REPO_ROOT/scripts/e2e/e2e-common.sh"
