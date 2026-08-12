#!/usr/bin/env bash
# forge-1.18.2/test-infrastructure/run-e2e.sh — thin lane wrapper for the
# real-client boot-smoke E2E (master plan slice 4; shared logic lives in
# scripts/e2e/drivers/modern-smoke.sh).
#
# Era notes (1.18.2, launch args bytecode-verified 2026-08-12):
#   - server: production forge installer 40.3.0 --installServer, launched
#     via the installer-generated @user_jvm_args.txt @unix_args.txt nogui.
#   - client: the FG 6.0 dev client (runClient) under xvfb-run + Mesa
#     llvmpipe; the 1.18.2 Main registers --server/--port (GameConfig
#     server = String+int) so the launcher-arg direct connect auto-joins.
#   - Gradle 8.14 runs on Java 21; the Java 17 toolchain (foojay) serves
#     both the build and the dev client fork.
#
# Usage: JAVA_HOME=<jdk21> ./run-e2e.sh
# Exit codes (master-plan contract): 0 all green | 1 assertion failed |
# 2 retryable infra | 3 build failure.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LANE_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(cd "$LANE_DIR/.." && pwd)"

# ---------------------------------------------------------------------------
# Java 21 (Gradle 8.14 daemon; the Java 17 toolchain is resolved by foojay
# during the build — no system JDK 17 required)
# ---------------------------------------------------------------------------
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    E2E_JAVA21_HOME="$JAVA_HOME"
elif [ -d "$HOME/.sdkman/candidates/java/21.0.2-tem" ]; then
    E2E_JAVA21_HOME="$HOME/.sdkman/candidates/java/21.0.2-tem"
elif [ -x /usr/lib/jvm/java-21-openjdk-amd64/bin/javac ]; then
    E2E_JAVA21_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
else
    echo "[e2e:1.18.2][fail] Java 21 not found (set JAVA_HOME to a JDK 21)" >&2
    exit 3
fi
echo "[e2e:1.18.2] Java 21: $E2E_JAVA21_HOME/bin/java"

# ---------------------------------------------------------------------------
# Build the lane (own wrapper: Gradle 8.14 on Java 21, FG 6.0.54)
# ---------------------------------------------------------------------------
echo "[e2e:1.18.2] building lane..."
cd "$LANE_DIR"
if ! JAVA_HOME="$E2E_JAVA21_HOME" ./gradlew build --no-daemon --console=plain; then
    echo "[e2e:1.18.2][fail] lane build failed (exit 3)" >&2
    exit 3
fi

MOD_JAR="$(ls build/libs/everlastingskins-*.jar 2>/dev/null | grep -v -- '-sources.jar' | head -1 || true)"
if [ -z "$MOD_JAR" ]; then
    echo "[e2e:1.18.2][fail] no mod jar in build/libs/" >&2
    exit 3
fi
echo "[e2e:1.18.2] mod jar: $MOD_JAR"

# Server java: the lane's Java 17 toolchain (production Forge 40 requires
# 17). Discovery runs AFTER the lane build: foojay resolves the 17 toolchain
# into ~/.gradle/jdks during `./gradlew build`, so a fresh runner (or a
# cache that carried no jdks) finds it here. Prefer the foojay-resolved JDK
# 17; fall back to a system JDK 17 if present.
E2E_JAVA=""
for cand in "$HOME/.gradle/jdks"/eclipse_adoptium-17-amd64-linux*/bin/java; do
    [ -x "$cand" ] && E2E_JAVA="$cand" && break
done
if [ -z "$E2E_JAVA" ] && [ -x /usr/lib/jvm/java-17-openjdk-amd64/bin/java ]; then
    E2E_JAVA="/usr/lib/jvm/java-17-openjdk-amd64/bin/java"
fi
if [ -z "$E2E_JAVA" ]; then
    echo "[e2e:1.18.2][fail] Java 17 not found for the production server" >&2
    exit 3
fi
echo "[e2e:1.18.2] server Java 17: $E2E_JAVA"

# ---------------------------------------------------------------------------
# Invoke the shared orchestrator (modern-smoke era)
# ---------------------------------------------------------------------------
export E2E_LANE="1.18.2"
export E2E_ERA="modern-smoke"
export E2E_MOD_JAR="$MOD_JAR"
export E2E_DRIVER_SCRIPT="$REPO_ROOT/scripts/e2e/drivers/modern-smoke.sh"
export E2E_JAVA
export E2E_FORGE_VER="1.18.2-40.3.0"
export E2E_INSTALLER_SHA1="d267e3dbd2df4e6849bdb2a81aa0b920c354efa6"
export E2E_JOIN_ARGS="--server 127.0.0.1 --port 25565"
export E2E_SERVER_BOOT_MODE="unix-args"
export E2E_CLIENT_GRADLE="$LANE_DIR/gradlew"
export E2E_CLIENT_WORKDIR="$LANE_DIR"
export E2E_CLIENT_TASK="runClient"
export E2E_GRADLE_JAVA_HOME="$E2E_JAVA21_HOME"
export E2E_SERVER_PORT="${E2E_SERVER_PORT:-25565}"

exec bash "$REPO_ROOT/scripts/e2e/e2e-common.sh"
