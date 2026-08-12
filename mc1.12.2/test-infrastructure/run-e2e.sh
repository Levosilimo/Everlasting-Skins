#!/usr/bin/env bash
# mc1.12.2/test-infrastructure/run-e2e.sh — thin lane wrapper for the
# HeadlessMC real-client E2E (master plan slice 2; shared logic lives in
# scripts/e2e/e2e-common.sh + scripts/e2e/drivers/headlessmc.sh).
#
# Command-driven upgrade (deferred from #438): the vanilla 1.12.2 client has
# NO stdin console, so the bridge needs the vendored hmc-specifics (lexforge)
# jar — its HeadlessMcMcTweaker + mixins pipe stdin through the game's chat
# surface. The scenario SENDs "/skin set mojang Notch" + the bridge-ack chat
# line through the console; the server-side ES_E2E_SKIN sentinel (mc1.12.2
# SkinCommand/SkinAction under -Deverlastingskins.e2e=true, mirrored from the
# 1.7.10 lane) is the primary assertion. The server runs the canonical
# 14.23.5.2847 build; the client profile is installed by the 14.23.5.2860
# installer (2847 has no --installClient CLI, and HMCLite's forgecli crashes
# on 1.12.2 installers).
#
# Usage: JAVA_HOME=<jdk8> ./run-e2e.sh [mc1.12.2]
#   The legacy positional arg (branch id) is accepted and ignored — CI's
#   required `E2E (mc1.12.2)` job calls `bash run-e2e.sh mc1.12.2`.
# Exit codes (master-plan contract): 0 all green | 1 assertion failed |
# 2 retryable infra | 3 build failure.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LANE_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(cd "$LANE_DIR/.." && pwd)"

# Java 8 (hard requirement: ForgeGradle 2.3.4 / Gradle 4.10.3 lane + the
# 1.12.2 client launch)
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    E2E_JAVA8="$JAVA_HOME/bin/java"
elif [ -x "$HOME/.sdkman/candidates/java/8.0.472-amzn/bin/java" ]; then
    E2E_JAVA8="$HOME/.sdkman/candidates/java/8.0.472-amzn/bin/java"
elif [ -x /usr/lib/jvm/java-8-openjdk-amd64/bin/java ]; then
    E2E_JAVA8="/usr/lib/jvm/java-8-openjdk-amd64/bin/java"
else
    echo "[e2e:mc1.12.2][fail] Java 8 not found (set JAVA_HOME to a JDK 8)" >&2
    exit 3
fi
echo "[e2e:mc1.12.2] Java 8: $E2E_JAVA8"

# Build the lane
echo "[e2e:mc1.12.2] building lane..."
cd "$LANE_DIR"
if ! JAVA_HOME="$(dirname "$(dirname "$E2E_JAVA8")")" ./gradlew build --no-daemon; then
    echo "[e2e:mc1.12.2][fail] lane build failed (exit 3)" >&2
    exit 3
fi

MOD_JAR="$(ls build/libs/*.jar 2>/dev/null | grep -v -- '-sources.jar' | grep -v -- '-javadoc.jar' | head -1 || true)"
if [ -z "$MOD_JAR" ]; then
    echo "[e2e:mc1.12.2][fail] no mod jar in build/libs/" >&2
    exit 3
fi

# Invoke the shared orchestrator (headlessmc era)
export E2E_LANE="mc1.12.2"
export E2E_ERA="headlessmc"
export E2E_MOD_JAR="$MOD_JAR"
export E2E_DRIVER_SCRIPT="$REPO_ROOT/scripts/e2e/drivers/headlessmc.sh"
export E2E_JAVA8

exec bash "$REPO_ROOT/scripts/e2e/e2e-common.sh"
