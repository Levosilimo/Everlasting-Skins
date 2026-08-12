#!/usr/bin/env bash
# forge-1.16.5/test-infrastructure/run-e2e.sh — thin lane wrapper for the
# real-client FULL in-jar E2E (master plan slice 3, modern-injar era;
# shared logic lives in scripts/e2e/drivers/modern-injar.sh).
#
# Era notes (1.16.5, all live-verified 2026-08-12):
#   - server: production forge installer 36.2.34 --installServer. The 1.16.5
#     installer does NOT generate unix_args.txt — the legacy ServerMain
#     classpath launch is used (asm-9.1 + log4j-2.15 first, universal jar
#     excluded; see modern-injar.sh), with -Deverlastingskins.e2e=true on
#     the JVM command line (no user_jvm_args.txt on this era).
#   - client: the FG 5.1 dev client (runClient) under xvfb-run + Mesa
#     llvmpipe; the in-jar E2EDriver dials the test server from the title
#     screen (the 1.16.5 Main registers --server/--port, but the vanilla
#     deferred connect is gated on the authlib privileges request, which
#     401s for an offline token — the client sits at the title screen
#     otherwise) and runs the /skin set mojang TestPlayer round-trip.
#   - Java 8 for both (lane toolchain).
#
# Usage: JAVA_HOME=<jdk8> ./run-e2e.sh
# Exit codes (master-plan contract): 0 all green | 1 assertion failed |
# 2 retryable infra | 3 build failure.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LANE_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(cd "$LANE_DIR/.." && pwd)"

# ---------------------------------------------------------------------------
# Java 8 (hard requirement: FG 5.1.77 + Gradle 7.6.4 + the 1.16.5 client all
# run on Java 8)
# ---------------------------------------------------------------------------
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    E2E_JAVA="$JAVA_HOME/bin/java"
elif [ -x "$HOME/.sdkman/candidates/java/8.0.472-amzn/bin/java" ]; then
    E2E_JAVA="$HOME/.sdkman/candidates/java/8.0.472-amzn/bin/java"
elif [ -x /usr/lib/jvm/java-8-openjdk-amd64/bin/java ]; then
    E2E_JAVA="/usr/lib/jvm/java-8-openjdk-amd64/bin/java"
else
    echo "[e2e:1.16.5][fail] Java 8 not found (set JAVA_HOME to a JDK 8)" >&2
    exit 3
fi
E2E_JAVA_HOME="$(dirname "$(dirname "$E2E_JAVA")")"
echo "[e2e:1.16.5] Java 8: $E2E_JAVA"

# ---------------------------------------------------------------------------
# Build the lane (own wrapper: Gradle 7.6.4 on Java 8, FG 5.1.77)
# ---------------------------------------------------------------------------
echo "[e2e:1.16.5] building lane..."
cd "$LANE_DIR"
if ! JAVA_HOME="$E2E_JAVA_HOME" ./gradlew build --no-daemon --console=plain; then
    echo "[e2e:1.16.5][fail] lane build failed (exit 3)" >&2
    exit 3
fi

MOD_JAR="$(ls build/libs/everlastingskins-*.jar 2>/dev/null | grep -v -- '-sources.jar' | head -1 || true)"
if [ -z "$MOD_JAR" ]; then
    echo "[e2e:1.16.5][fail] no mod jar in build/libs/" >&2
    exit 3
fi
echo "[e2e:1.16.5] mod jar: $MOD_JAR"

# ---------------------------------------------------------------------------
# Invoke the shared orchestrator (modern-injar era)
# ---------------------------------------------------------------------------
export E2E_LANE="1.16.5"
export E2E_ERA="modern-injar"
export E2E_MOD_JAR="$MOD_JAR"
export E2E_DRIVER_SCRIPT="$REPO_ROOT/scripts/e2e/drivers/modern-injar.sh"
export E2E_JAVA
export E2E_FORGE_VER="1.16.5-36.2.34"
export E2E_INSTALLER_SHA1="ab306b654c44d659ce69da5d4f87590b66dc91e8"
export E2E_JOIN_ARGS="--server 127.0.0.1 --port 25565"
export E2E_SERVER_BOOT_MODE="legacy-cp"
export E2E_CLIENT_GRADLE="$LANE_DIR/gradlew"
export E2E_CLIENT_WORKDIR="$LANE_DIR"
export E2E_CLIENT_TASK="runClient"
export E2E_CLIENT_GRADLE_ARGS="-Peverlastingskins.e2e=true"
export E2E_GRADLE_JAVA_HOME="$E2E_JAVA_HOME"
export E2E_SERVER_PORT="${E2E_SERVER_PORT:-25565}"

exec bash "$REPO_ROOT/scripts/e2e/e2e-common.sh"
