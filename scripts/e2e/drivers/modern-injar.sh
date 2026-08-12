#!/usr/bin/env bash
# scripts/e2e/drivers/modern-injar.sh — modern-era real-client FULL in-jar
# driver (master plan slice 3, modern-injar pattern; 26.x port of the
# forge-1.21 flow — reference: forge-1.21/test-infrastructure/run-e2e.sh).
#
# FULL-DRIVER FLOW (vs the slice-4 boot-smoke floor in modern-smoke.sh):
#   server  — PRODUCTION Forge server: the pinned forge installer runs
#             --installServer, the built mod jar goes into mods/,
#             online-mode=false, -Deverlastingskins.e2e=true via
#             user_jvm_args.txt. Boot greps the vanilla "For help" line,
#             then the sentinel-seed line (ES_E2E_SENTINEL=seeded).
#   client  — the lane's real dev client (runClient — identical client code,
#             same renderer, same network stack, official names at runtime)
#             under xvfb-run + Mesa llvmpipe, shipped-gated via
#             -Peverlastingskins.e2e=true → -Deverlastingskins.e2e=true.
#             The offline session uses the launcher args --username TestPlayer
#             --uuid <offline-uuid> --accessToken 0; the JOIN mechanism is the
#             quick-play arg (--quickPlayMultiplayer 127.0.0.1:25565,
#             bytecode-verified on the unobfuscated 26.x client jars).
#   assert  — the in-jar E2EDriver (shipped in the mod jar, gated) runs
#             /skin set mojang TestPlayer and asserts the tab-list textures
#             property carries the server-seeded sentinel marker (base64-
#             decoded check); it writes e2e-result.json into the client
#             gameDir and System.exits. This driver waits for that result
#             file, merges server_booted, maps the exit code and runs the
#             contract assert gates.
#
# This driver owns the FULL flow for the modern in-jar lanes (26.1/26.2);
# e2e-common.sh dispatches to it when E2E_ERA=modern-injar.
#
# Env contract (set by the lane wrapper test-infrastructure/run-e2e.sh):
#   E2E_LANE            lane id (26.1 | 26.2)
#   E2E_MOD_JAR         built mod jar
#   E2E_JAVA            server java binary (Java 25 on the 26.x line)
#   E2E_FORGE_VER       forge coordinate, e.g. 26.2-65.0.9
#   E2E_INSTALLER_SHA1  pinned installer sha1
#   E2E_JOIN_ARGS       client join args (--quickPlayMultiplayer 127.0.0.1:25565)
#   E2E_CLIENT_GRADLE   absolute path to the gradlew for the dev client
#   E2E_CLIENT_WORKDIR  dir to run gradle from (repo root)
#   E2E_CLIENT_TASK     runClient task (:forge-26.2:runClient)
#   E2E_GRADLE_JAVA_HOME  JAVA_HOME for the gradle daemon
#   E2E_SERVER_BOOT_MODE  unix-args (26.x)
#   E2E_SERVER_PORT     test port (default 25565)
#   E2E_CLIENT_GRADLE_ARGS  extra gradle args, e.g. -Peverlastingskins.e2e=true
#
# Exit codes (master-plan contract): 0 all green | 1 assertion failed |
# 2 retryable infra (boot/join timeout, installer download) | 3 hard failure.

set -euo pipefail
E2E_DRIVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../lib.sh
source "$E2E_DRIVER_DIR/lib.sh"

: "${E2E_LANE:?modern-injar.sh: E2E_LANE is required}"
: "${E2E_MOD_JAR:?modern-injar.sh: E2E_MOD_JAR is required}"
: "${E2E_JAVA:?modern-injar.sh: E2E_JAVA (server java binary) is required}"
: "${E2E_FORGE_VER:?modern-injar.sh: E2E_FORGE_VER is required}"
: "${E2E_INSTALLER_SHA1:?modern-injar.sh: E2E_INSTALLER_SHA1 is required}"
: "${E2E_JOIN_ARGS:?modern-injar.sh: E2E_JOIN_ARGS is required}"
: "${E2E_CLIENT_GRADLE:?modern-injar.sh: E2E_CLIENT_GRADLE is required}"
: "${E2E_CLIENT_WORKDIR:?modern-injar.sh: E2E_CLIENT_WORKDIR is required}"
: "${E2E_CLIENT_TASK:=runClient}"
: "${E2E_GRADLE_JAVA_HOME:?modern-injar.sh: E2E_GRADLE_JAVA_HOME is required}"
: "${E2E_SERVER_BOOT_MODE:=unix-args}"
: "${E2E_SERVER_PORT:=25565}"
: "${E2E_USERNAME:=TestPlayer}"
: "${E2E_SERVER_BOOT_TIMEOUT_S:=240}"
: "${E2E_SENTINEL_TIMEOUT_S:=60}"
: "${E2E_CLIENT_TIMEOUT_S:=600}"
# Extra gradle args for the client launch; the lane wrapper MUST pass
# -Peverlastingskins.e2e=true (the convention's runClient run definition
# forwards it to the forked client JVM as -Deverlastingskins.e2e=true).
: "${E2E_CLIENT_GRADLE_ARGS:=}"

SERVER_DIR="$RUNNER_TMP/server-$E2E_LANE"
SERVER_LOG="$SERVER_DIR/server.log"

# Offline session uuid (OfflinePlayer:Name md5, launcher convention — the
# offline-mode server does not validate it; must match the in-jar driver's
# E2E.offlineUuid("TestPlayer") so seed, session and assert key on one UUID).
OFFLINE_UUID=$(python3 - <<'PY'
import hashlib, uuid
name = "TestPlayer"
d = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode("utf-8")).digest())
d[6] = (d[6] & 0x0f) | 0x30
d[8] = (d[8] & 0x3f) | 0x80
print(uuid.UUID(bytes=bytes(d)))
PY
)
e2e_log "offline test player: $E2E_USERNAME / $OFFLINE_UUID"

# ---------------------------------------------------------------------------
# Production server: pinned forge installer -> --installServer -> mods/
# ---------------------------------------------------------------------------
INSTALLER_NAME="forge-$E2E_FORGE_VER-installer.jar"
INSTALLER_URL="https://maven.minecraftforge.net/net/minecraftforge/forge/$E2E_FORGE_VER/forge-$E2E_FORGE_VER-installer.jar"
fetch_artifact "$INSTALLER_NAME" "$INSTALLER_URL" "$E2E_INSTALLER_SHA1"

rm -rf "$SERVER_DIR"
mkdir -p "$SERVER_DIR"
e2e_log "installing server ($INSTALLER_NAME)..."
if ! ( cd "$SERVER_DIR" && "$E2E_JAVA" -jar "$E2E_CACHE_DIR/$E2E_LANE/$INSTALLER_NAME" --installServer ) > "$RUNNER_TMP/install-$E2E_LANE.log" 2>&1; then
    e2e_warn "forge installer --installServer failed (log tail follows)"
    tail -n 20 "$RUNNER_TMP/install-$E2E_LANE.log" >&2 || true
    e2e_infra_fail "forge installer --installServer failed"
fi

echo "eula=true" > "$SERVER_DIR/eula.txt"
cat > "$SERVER_DIR/server.properties" <<EOF
online-mode=false
server-port=$E2E_SERVER_PORT
level-type=flat
motd=EverlastingSkins E2E $E2E_LANE
max-tick-time=-1
EOF
echo "-Deverlastingskins.e2e=true" >> "$SERVER_DIR/user_jvm_args.txt"
mkdir -p "$SERVER_DIR/mods"
cp "$E2E_MOD_JAR" "$SERVER_DIR/mods/"

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

# ---------------------------------------------------------------------------
# Server boot (unix-args era: installer-generated @user_jvm_args.txt
# @libraries/.../unix_args.txt nogui — live-verified on the 26.x lanes).
# ---------------------------------------------------------------------------
e2e_log "booting server (mode $E2E_SERVER_BOOT_MODE)..."
(
    cd "$SERVER_DIR"
    exec setsid "$E2E_JAVA" @user_jvm_args.txt \
        @libraries/net/minecraftforge/forge/$E2E_FORGE_VER/unix_args.txt nogui
) > "$SERVER_LOG" 2>&1 &
SERVER_PID=$!

if ! wait_for_log "$SERVER_LOG" 'For help, type "help"' "$E2E_SERVER_BOOT_TIMEOUT_S"; then
    e2e_warn "server boot timeout (tail follows)"
    tail -n 40 "$SERVER_LOG" >&2 || true
    kill_tree "$SERVER_PID" 2>/dev/null || true
    SERVER_PID=""
    exit 2
fi
e2e_log "server booted (For help, type \"help\")"

if ! wait_for_log "$SERVER_LOG" 'ES_E2E_SENTINEL=seeded' "$E2E_SENTINEL_TIMEOUT_S"; then
    e2e_warn "sentinel seed not logged (tail follows)"
    tail -n 40 "$SERVER_LOG" >&2 || true
    kill_tree "$SERVER_PID" 2>/dev/null || true
    SERVER_PID=""
    exit 1
fi
e2e_log "sentinel seeded"

# ---------------------------------------------------------------------------
# Dev client under xvfb + Mesa llvmpipe. Fresh run dir (stale artifacts must
# never mask a driver timeout — pre-1.8 lesson). The driver writes
# e2e-result.json into the client gameDir (= the run dir).
# ---------------------------------------------------------------------------
CLIENT_RUNDIR="${E2E_CLIENT_RUNDIR:-$E2E_CLIENT_WORKDIR/run}"
rm -rf "$CLIENT_RUNDIR"
mkdir -p "$CLIENT_RUNDIR"
# Fresh gameDir = first-launch state: without this the modern client blocks
# on the AccessibilityOnboardingScreen and the title screen never appears.
cat > "$CLIENT_RUNDIR/options.txt" <<'EOF'
onboardAccessibility:false
EOF

CLIENT_LOG="$RUNNER_TMP/client-$E2E_LANE.log"
CLIENT_PID=""

e2e_log "launching client (xvfb + llvmpipe, join via $E2E_JOIN_ARGS)..."
set +e
(
    cd "$E2E_CLIENT_WORKDIR"
    exec setsid xvfb-run -a env -u WAYLAND_DISPLAY \
        XDG_SESSION_TYPE=x11 \
        LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe \
        GLFW_PLATFORM=x11 \
        JAVA_HOME="$E2E_GRADLE_JAVA_HOME" \
        timeout --kill-after=15 "$E2E_CLIENT_TIMEOUT_S" \
        "$E2E_CLIENT_GRADLE" $E2E_CLIENT_GRADLE_ARGS "$E2E_CLIENT_TASK" \
        --args="--username $E2E_USERNAME --uuid $OFFLINE_UUID --accessToken 0 $E2E_JOIN_ARGS" \
        --no-daemon --console=plain
) > "$CLIENT_LOG" 2>&1 &
CLIENT_PID=$!
set -e

# ---------------------------------------------------------------------------
# Driver result: the in-jar E2EDriver writes e2e-result.json into the client
# gameDir (the run dir) and System.exits; JavaExec propagates the fork's
# exit code, so the gradle process ends right after.
# ---------------------------------------------------------------------------
RESULT_FILE="$CLIENT_RUNDIR/e2e-result.json"
i=0
while [ "$i" -lt "$E2E_CLIENT_TIMEOUT_S" ] && [ ! -f "$RESULT_FILE" ]; do
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
e2e_log "PASS: real-client E2E ($E2E_LANE) all green"
exit 0
