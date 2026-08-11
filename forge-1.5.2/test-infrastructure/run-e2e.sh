#!/usr/bin/env bash
# forge-1.5.2/test-infrastructure/run-e2e.sh — thin lane wrapper for the
# real-client E2E (master plan slice 1; shared logic lives in
# scripts/e2e/e2e-common.sh + scripts/e2e/drivers/pre18-xvfb.sh).
#
# ERA-UNTESTED (slice 1, 2026-08-11): this lane is a MERGE-MODEL era.
# 1.4.7/1.5.2 boot the client as: obf client.jar + forge universal.zip
# MERGED into the client jar, main net.minecraft.client.Minecraft,
# CWD=gameDir — NOT the 1.6.4 launchwrapper tweaker model that
# scripts/e2e/drivers/pre18-xvfb.sh implements (hardcoded 1.6.4 client
# jar pins + net.minecraft.launchwrapper.Launch --tweakClass FMLTweaker).
# The merge-model client boot is NOT implemented and was NOT live-verified
# in the wiring session, so this wrapper mirrors the 1.6.4 wrapper's lane
# mechanics (Java 8 discovery, lane build, mod jar, vendored server jar)
# and then exits 3 with this documented note instead of invoking the
# 1.6.4-only orchestrator. Do NOT fake a pass: the CI matrix entry for
# this lane stays commented until a merge-model driver lands (lib-8
# recipe: .slim/deepwork/real-client-e2e-plan.md, open item: pin pre-1.8
# CLIENT jar URLs).
#
# Usage: JAVA_HOME=<jdk8> ./run-e2e.sh
# Exit codes (master-plan contract): 0 all green | 1 assertion failed |
# 2 retryable infra | 3 build/hard failure (era-untested exits 3).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LANE_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(cd "$LANE_DIR/.." && pwd)"

# ---------------------------------------------------------------------------
# Java 8 (hard requirement: Gradle 4.4.1 dies on 9+; the future merge-model
# client boot needs Java 8 too — launchwrapper-era JVM semantics)
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
# ERA-UNTESTED guard (merge-model boot not implemented)
# ---------------------------------------------------------------------------
echo "[e2e:1.5.2][era-untested] merge-model client boot NOT implemented: 1.5.2 needs obf client.jar + universal.zip MERGED into the client jar (main net.minecraft.client.Minecraft, CWD=gameDir); scripts/e2e/drivers/pre18-xvfb.sh implements only the 1.6.4 tweaker model, and the merge-model boot was not live-verified in the wiring session. Lane mechanics above (build + artifact discovery) are verified; the client/server E2E is not. Enable CI only after a merge-model driver lands per lib-8 (.slim/deepwork/real-client-e2e-plan.md). Exiting 3 — not a pass." >&2
exit 3
