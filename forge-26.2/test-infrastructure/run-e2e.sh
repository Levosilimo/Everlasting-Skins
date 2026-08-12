#!/usr/bin/env bash
# forge-26.2/test-infrastructure/run-e2e.sh — thin lane wrapper for the
# real-client boot-smoke E2E (master plan slice 4; shared logic lives in
# scripts/e2e/drivers/modern-smoke.sh).
#
# Era notes (26.2, launch args bytecode-verified 2026-08-12):
#   - server: production forge installer 65.0.9 --installServer, launched
#     via the installer-generated @user_jvm_args.txt @unix_args.txt nogui.
#   - client: the FG 7 dev client (runClient from the root build) under
#     xvfb-run + Mesa llvmpipe. The unobfuscated 26.2 client Main was
#     verified to join via the quick play arg --quickPlayMultiplayer <addr>
#     (GameConfig$QuickPlayMultiplayerData -> QuickPlay.connect ->
#     joinMultiplayerWorld -> ConnectScreen.startConnecting).
#   - Java 25 for build + server + dev client (unobfuscated MC line).
#
# Usage: JAVA_HOME=<jdk25> ./run-e2e.sh
# Exit codes (master-plan contract): 0 all green | 1 assertion failed |
# 2 retryable infra | 3 build failure.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LANE_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(cd "$LANE_DIR/.." && pwd)"

# ---------------------------------------------------------------------------
# Java 25 (hard requirement: the 26.x line builds and runs on Java 25). The
# sdkman `current` symlink may point at another major (e.g. 8), so a JAVA_HOME
# is only accepted when it actually reports 25.
# ---------------------------------------------------------------------------
E2E_JAVA25_HOME=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] && \
    "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"25\.'; then
    E2E_JAVA25_HOME="$JAVA_HOME"
elif [ -d "$HOME/.sdkman/candidates/java/25.0.2-tem" ]; then
    E2E_JAVA25_HOME="$HOME/.sdkman/candidates/java/25.0.2-tem"
else
    echo "[e2e:26.2][fail] Java 25 not found (set JAVA_HOME to a JDK 25)" >&2
    exit 3
fi
E2E_JAVA="$E2E_JAVA25_HOME/bin/java"
echo "[e2e:26.2] Java 25: $E2E_JAVA"

# ---------------------------------------------------------------------------
# Build the lane (root build: Gradle 9.x on Java 25, FG 7.0.17)
# ---------------------------------------------------------------------------
echo "[e2e:26.2] building lane..."
cd "$REPO_ROOT"
if ! JAVA_HOME="$E2E_JAVA25_HOME" ./gradlew :forge-26.2:build --no-daemon --console=plain; then
    echo "[e2e:26.2][fail] lane build failed (exit 3)" >&2
    exit 3
fi

MOD_JAR="$(ls "$LANE_DIR"/build/libs/everlastingskins-*.jar 2>/dev/null | grep -v -- '-sources.jar' | head -1 || true)"
if [ -z "$MOD_JAR" ]; then
    echo "[e2e:26.2][fail] no mod jar in build/libs/" >&2
    exit 3
fi
echo "[e2e:26.2] mod jar: $MOD_JAR"

# ---------------------------------------------------------------------------
# Invoke the shared orchestrator (modern-smoke era)
# ---------------------------------------------------------------------------
export E2E_LANE="26.2"
export E2E_ERA="modern-smoke"
export E2E_MOD_JAR="$MOD_JAR"
export E2E_DRIVER_SCRIPT="$REPO_ROOT/scripts/e2e/drivers/modern-smoke.sh"
export E2E_JAVA
export E2E_FORGE_VER="26.2-65.0.9"
export E2E_INSTALLER_SHA1="2c27fc58bc955fb248829bb271b0844b607ad313"
export E2E_JOIN_ARGS="--quickPlayMultiplayer 127.0.0.1:25565"
export E2E_SERVER_BOOT_MODE="unix-args"
export E2E_CLIENT_GRADLE="$REPO_ROOT/gradlew"
export E2E_CLIENT_WORKDIR="$REPO_ROOT"
export E2E_CLIENT_TASK=":forge-26.2:runClient"
export E2E_GRADLE_JAVA_HOME="$E2E_JAVA25_HOME"
export E2E_CLIENT_RUNDIR="$LANE_DIR/run"
export E2E_SERVER_PORT="${E2E_SERVER_PORT:-25565}"

exec bash "$REPO_ROOT/scripts/e2e/e2e-common.sh"
