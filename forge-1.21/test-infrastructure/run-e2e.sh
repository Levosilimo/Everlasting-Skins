#!/usr/bin/env bash
# forge-1.21/test-infrastructure/run-e2e.sh — thin lane wrapper for the
# real-client E2E (master plan slice 3, modern-injar pattern; reference
# implementation for the 26.x port).
#
# REAL SERVER + REAL CLIENT on the modern line:
#   server  — PRODUCTION Forge 1.21 server: the pinned forge installer
#             runs --installServer (headless), the built mod jar goes into
#             mods/, -Deverlastingskins.e2e=true via user_jvm_args.txt,
#             online-mode=false. Boot greps the vanilla "For help" line.
#   client  — the real 1.21 client launched the standard ForgeGradle dev
#             way (runClient is a JavaExec; identical client code — same
#             renderer, same network stack, official names at runtime).
#             xvfb-run -a + Mesa llvmpipe for GL; the offline session uses
#             the launcher args --username TestPlayer --uuid <offline-uuid>
#             --accessToken 0 (verified working in offline mode on the real
#             1.21 client). The 1.21 Main has NO --server/--port arg
#             (verified against the client jar: allowsUnrecognizedOptions
#             ignores them) and quick-play does not fire on a dev launch, so
#             the in-jar E2EDriver dials the test server itself from the
#             main menu (ConnectScreen.startConnecting; same precedent as
#             the pre-1.8 driver's Minecraft.setServer). The driver
#             (shipped-gated via -Peverlastingskins.e2e=true →
#             -Deverlastingskins.e2e=true) then runs /skin set mojang
#             TestPlayer and asserts the tab-list textures property carries
#             the sentinel marker.
#
# Production-profile client boot (installer/Prism-style BootstrapLauncher
# launch, mods/ install on the client) is a documented follow-up: the Forge
# installer's CLIENT install is GUI-only, and the dev launch runs the
# identical client code; the assertion surface (ClientPacketListener /
# PlayerInfo, official names) is the same in both.
#
# Usage: JAVA_HOME=<jdk21> ./run-e2e.sh
# Exit codes (master-plan contract): 0 all green | 1 assertion failed |
# 2 retryable infra | 3 build/hard failure.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LANE_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(cd "$LANE_DIR/.." && pwd)"

export E2E_LANE="1.21"
# shellcheck source=../../scripts/e2e/lib.sh
source "$REPO_ROOT/scripts/e2e/lib.sh"

E2E_USERNAME="TestPlayer"
# The driver dials a fixed 127.0.0.1:25565 (E2EDriver.SERVER_HOST/PORT);
# if you override E2E_SERVER_PORT here you MUST also override the driver's
# constants — keep the default otherwise.
E2E_SERVER_PORT="${E2E_SERVER_PORT:-25565}"
OFFLINE_UUID=$(python3 - <<'PY'
import hashlib, sys, uuid
name = "TestPlayer"
d = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode("utf-8")).digest())
d[6] = (d[6] & 0x0f) | 0x30
d[8] = (d[8] & 0x3f) | 0x80
print(uuid.UUID(bytes=bytes(d)))
PY
)
e2e_log "offline test player: $E2E_USERNAME / $OFFLINE_UUID"

# ---------------------------------------------------------------------------
# Java 21 (hard requirement: Forge 51 dev client + production server)
# ---------------------------------------------------------------------------
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    E2E_JAVA="$JAVA_HOME/bin/java"
elif [ -x /usr/lib/jvm/java-21-openjdk-amd64/bin/javac ]; then
    E2E_JAVA="/usr/lib/jvm/java-21-openjdk-amd64/bin/java"
elif [ -d "$HOME/.sdkman/candidates/java" ]; then
    # Any sdkman JDK 21 (first match).
    E2E_JAVA="$(ls -d "$HOME"/.sdkman/candidates/java/21* 2>/dev/null | head -1)/bin/java"
    [ -x "$E2E_JAVA" ] || E2E_JAVA=""
fi
if [ -z "${E2E_JAVA:-}" ]; then
    e2e_fail "Java 21 not found (set JAVA_HOME to a JDK 21)"
fi
JAVA21_HOME="$(dirname "$(dirname "$E2E_JAVA")")"
e2e_log "Java 21: $E2E_JAVA"

# ---------------------------------------------------------------------------
# Build the lane (produces the mod jar for the server's mods/ + validates
# the driver compiles; also warms the userdev cache for the dev client).
# ---------------------------------------------------------------------------
cd "$LANE_DIR"
if ! JAVA_HOME="$JAVA21_HOME" "$REPO_ROOT/gradlew" :forge-1.21:build --no-daemon --console=plain; then
    e2e_fail "lane build failed"
fi
MOD_JAR="$(ls build/libs/everlastingskins-*.jar 2>/dev/null | grep -v -- '-sources.jar' | head -1 || true)"
if [ -z "$MOD_JAR" ]; then
    e2e_fail "no mod jar in build/libs/"
fi
e2e_log "mod jar: $MOD_JAR"

# ---------------------------------------------------------------------------
# Production server: pinned forge installer → --installServer → mods/
# ---------------------------------------------------------------------------
INSTALLER_NAME="forge-1.21-51.0.8-installer.jar"
INSTALLER_URL="https://maven.minecraftforge.net/net/minecraftforge/forge/1.21-51.0.8/forge-1.21-51.0.8-installer.jar"
INSTALLER_SHA1="4e4fa11f5e04fd968bc6ee1021f312ad4b233170"
fetch_artifact "$INSTALLER_NAME" "$INSTALLER_URL" "$INSTALLER_SHA1"

SERVER_DIR="$RUNNER_TMP/server-$E2E_LANE"
rm -rf "$SERVER_DIR"
mkdir -p "$SERVER_DIR"
e2e_log "installing server ($INSTALLER_NAME)..."
if ! ( cd "$SERVER_DIR" && "$E2E_JAVA" -jar "$E2E_CACHE_DIR/$E2E_LANE/$INSTALLER_NAME" --installServer ); then
    e2e_infra_fail "forge installer --installServer failed"
fi

echo "eula=true" > "$SERVER_DIR/eula.txt"
cat > "$SERVER_DIR/server.properties" <<EOF
online-mode=false
server-port=$E2E_SERVER_PORT
level-type=flat
motd=EverlastingSkins E2E 1.21
max-tick-time=-1
EOF
echo "-Deverlastingskins.e2e=true" >> "$SERVER_DIR/user_jvm_args.txt"
mkdir -p "$SERVER_DIR/mods"
cp "$MOD_JAR" "$SERVER_DIR/mods/"

# ---------------------------------------------------------------------------
# Boot the server (own session; unix_args.txt = the installer-generated
# BootstrapLauncher launch). CWD must be the server dir: modern Forge
# resolves mods/ relative to it.
# ---------------------------------------------------------------------------
SERVER_LOG="$SERVER_DIR/server.log"
SERVER_PID=""
cleanup() {
    if [ -n "$SERVER_PID" ]; then
        kill_tree "$SERVER_PID" 2>/dev/null || true
    fi
    if [ -n "${CLIENT_PID:-}" ]; then
        kill_tree "$CLIENT_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

e2e_log "booting server..."
(
    cd "$SERVER_DIR"
    exec setsid "$E2E_JAVA" @user_jvm_args.txt \
        @libraries/net/minecraftforge/forge/1.21-51.0.8/unix_args.txt nogui
) > "$SERVER_LOG" 2>&1 &
SERVER_PID=$!

if ! wait_for_log "$SERVER_LOG" 'For help, type "help"' 300; then
    e2e_warn "server boot timeout (tail follows)"
    tail -n 40 "$SERVER_LOG" >&2 || true
    kill_tree "$SERVER_PID" 2>/dev/null || true
    exit 2
fi
e2e_log "server booted (For help, type \"help\")"

if ! wait_for_log "$SERVER_LOG" 'ES_E2E_SENTINEL=seeded' 60; then
    e2e_warn "sentinel seed not logged (tail follows)"
    tail -n 40 "$SERVER_LOG" >&2 || true
    kill_tree "$SERVER_PID" 2>/dev/null || true
    exit 1
fi
e2e_log "sentinel seeded"

# ---------------------------------------------------------------------------
# Dev client under xvfb + Mesa llvmpipe. Fresh run dir (stale artifacts
# must never mask a driver timeout — pre-1.8 lesson). The driver writes
# e2e-result.json into the client gameDir (= the run dir).
# ---------------------------------------------------------------------------
rm -rf "$LANE_DIR/run"
mkdir -p "$LANE_DIR/run"
# Fresh gameDir = first-launch state: without this the client blocks on the
# AccessibilityOnboardingScreen (observed live) and the title screen never
# appears. The option key is the vanilla options.txt boolean (verified
# against the Options class).
cat > "$LANE_DIR/run/options.txt" <<'EOF'
onboardAccessibility:false
EOF
CLIENT_LOG="$RUNNER_TMP/client-$E2E_LANE.log"
CLIENT_PID=""

e2e_log "launching client (xvfb + llvmpipe)..."
set +e
(
    cd "$REPO_ROOT"
    exec setsid xvfb-run -a env \
        LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe \
        JAVA_HOME="$JAVA21_HOME" \
        ./gradlew :forge-1.21:runClient \
        -Peverlastingskins.e2e=true \
        --args="--username $E2E_USERNAME --uuid $OFFLINE_UUID --accessToken 0" \
        --no-daemon --console=plain
) > "$CLIENT_LOG" 2>&1 &
CLIENT_PID=$!
set -e

RESULT_FILE="$LANE_DIR/run/e2e-result.json"
i=0
while [ "$i" -lt 600 ] && [ ! -f "$RESULT_FILE" ]; do
    if ! kill -0 "$CLIENT_PID" 2>/dev/null; then
        break
    fi
    sleep 5
    i=$((i + 5))
done

if [ ! -f "$RESULT_FILE" ]; then
    e2e_warn "no driver result (client tail follows)"
    tail -n 60 "$CLIENT_LOG" >&2 || true
    kill_tree "$CLIENT_PID" 2>/dev/null || true
    kill_tree "$SERVER_PID" 2>/dev/null || true
    SERVER_PID=""
    CLIENT_PID=""
    exit 2
fi
e2e_log "driver result written: $RESULT_FILE"

# Let the gradle runClient task finish (the driver System.exits the client;
# JavaExec propagates the fork's exit code).
i=0
while [ "$i" -lt 60 ] && kill -0 "$CLIENT_PID" 2>/dev/null; do
    sleep 2
    i=$((i + 2))
done

# Kill the server before asserting: assertion failures exit 1 and must not
# leak the server (the cleanup trap covers every other path).
kill_tree "$SERVER_PID" 2>/dev/null || true
SERVER_PID=""

# ---------------------------------------------------------------------------
# Assemble the final contract doc + assertions (lib.sh helpers; server_booted
# is the one script-side fact the driver cannot know).
# ---------------------------------------------------------------------------
assemble_result "true" "$RESULT_FILE"

DRIVER_FINAL_CODE=$(result_exit_code)
if [ "$DRIVER_FINAL_CODE" -eq 2 ]; then
    e2e_warn "RETRYABLE: driver reported infra failure (exit 2)"
    exit 2
fi
if [ "$DRIVER_FINAL_CODE" -ne 0 ]; then
    e2e_warn "FAIL: driver exit code $DRIVER_FINAL_CODE"
    exit 1
fi

assert_result "server_booted" "True"
assert_result "client_joined" "True"
assert_result "command_executed" "True"
assert_result "renderer_state" "sentinel"
assert_result "renderer_verified" "True"

e2e_log "e2e complete: exit_code=0 duration_ms=$(python3 -c "import json; print(json.load(open('$RESULT_JSON')).get('duration_ms'))")"
e2e_log "PASS: real-client E2E (1.21) all green"
exit 0
