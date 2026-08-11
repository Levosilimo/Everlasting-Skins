#!/usr/bin/env bash
# scripts/e2e/e2e-common.sh — shared real-client E2E orchestrator
# (master-plan contract; every era implements the same shape).
#
# Flow: prepare server dir (mods/, sentinel, server.properties) → boot the
# real server (bg) → wait for the vanilla "For help, type \"help\"" boot
# line → invoke the era client driver script → merge the driver's
# gameDir result into ${RUNNER_TMP}/e2e-result.json → assert the contract
# fields → map exit codes (0 all green | 1 assertion failed | 2 retryable
# infra | 3 build/hard failure).
#
# Env contract (set by the lane wrapper test-infrastructure/run-e2e.sh):
#   E2E_LANE           lane id (1.6.4)
#   E2E_MOD_JAR        built mod jar
#   E2E_SERVER_JAR     vendored vanilla server jar
#   E2E_DRIVER_SCRIPT  era client driver (scripts/e2e/drivers/pre18-xvfb.sh)
#   E2E_SERVER_CP_EXTRA extra server classpath entries (optional)
#   E2E_JAVA8          Java 8 binary (server + pre-1.8 client)
#   E2E_SERVER_PORT    test port (default 25565)

set -euo pipefail
E2E_COMMON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$E2E_COMMON_DIR/lib.sh"

: "${E2E_MOD_JAR:?e2e-common.sh: E2E_MOD_JAR is required}"
: "${E2E_SERVER_JAR:?e2e-common.sh: E2E_SERVER_JAR is required}"
: "${E2E_DRIVER_SCRIPT:?e2e-common.sh: E2E_DRIVER_SCRIPT is required}"
: "${E2E_JAVA8:?e2e-common.sh: E2E_JAVA8 (Java 8) is required}"
: "${E2E_SERVER_PORT:=25565}"
: "${E2E_SERVER_CP_EXTRA:=}"
: "${E2E_SERVER_BOOT_TIMEOUT_S:=240}"
: "${E2E_SENTINEL_PNG:=$REPO_ROOT/common/src/test/resources/e2e/sentinel-64x32.png}"

JAVA8_BIN="$E2E_JAVA8"
[ -x "$JAVA8_BIN" ] || JAVA8_BIN="$E2E_JAVA8/bin/java"

# ---------------------------------------------------------------------------
# Server dir + sentinel
# ---------------------------------------------------------------------------
SERVER_DIR="$RUNNER_TMP/server"
rm -rf "$SERVER_DIR"
mkdir -p "$SERVER_DIR/mods" "$SERVER_DIR/logs"
cp "$E2E_MOD_JAR" "$SERVER_DIR/mods/"
cp "$E2E_SENTINEL_PNG" "$SERVER_DIR/e2e-sentinel.png"
cat > "$SERVER_DIR/server.properties" <<EOF
online-mode=false
server-port=$E2E_SERVER_PORT
level-name=world
gamemode=0
motd=EverlastingSkins E2E $E2E_LANE
max-tick-time=-1
EOF
# 1.6.4 ops model: ops.txt (one username per line) — lets the offline test
# player pass the command's permission gate (verified: without op the server
# replies "You do not have permission").
printf '%s\n' "$E2E_USERNAME" > "$SERVER_DIR/ops.txt"

# ---------------------------------------------------------------------------
# Server classpath: vanilla server + forge universal + mod + shared libs
# (the 1.6.4 launcher library set minus the client-only lwjgl/paulscode legs;
# plus launchwrapper/asm/jopt-simple for the tweaker model and
# authlib/log4j-api for :common at runtime).
# ---------------------------------------------------------------------------
CACHE="$E2E_CACHE_DIR/$E2E_LANE"
fetch_artifact "forge-1.6.4-9.11.1.1345-universal.jar" \
    "https://maven.minecraftforge.net/net/minecraftforge/forge/1.6.4-9.11.1.1345/forge-1.6.4-9.11.1.1345-universal.jar" \
    "eb9d954c8d057fa1768acaa40a35b864ad05c58b"

# The mod jar is deliberately NOT on the classpath — FML discovers it in
    # <serverDir>/mods/ (classpath + mods/ duplicates the mod: "Found a
    # duplicate mod everlastingskins").
    SERVER_CP="$E2E_SERVER_JAR:$CACHE/forge-1.6.4-9.11.1.1345-universal.jar"
for lib in launchwrapper-1.8.jar asm-all-4.1.jar jopt-simple-4.5.jar guava-14.0.jar \
    gson-2.2.2.jar commons-lang3-3.1.jar commons-io-2.4.jar argo-2.25_fixed.jar \
    lzma-0.0.1.jar \
    bcprov-jdk15on-1.47.jar authlib-1.5.16.jar log4j-api-2.8.1.jar; do
    SERVER_CP="$SERVER_CP:$CACHE/$lib"
done
[ -z "$E2E_SERVER_CP_EXTRA" ] || SERVER_CP="$SERVER_CP:$E2E_SERVER_CP_EXTRA"

# ---------------------------------------------------------------------------
# Boot the real server (bg, own session). FML resolves mods/ relative to the
# process CWD, so the server MUST run from the server dir (the wrapper runs
# from the lane dir).
# ---------------------------------------------------------------------------
SERVER_LOG="$SERVER_DIR/server.log"
SERVER_PID=""
cleanup() {
    if [ -n "$SERVER_PID" ]; then
        kill_tree "$SERVER_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

e2e_log "booting server (pid tracking via session)..."
(
    cd "$SERVER_DIR"
    # setsid: own session/PGID so kill_tree (group kill) never hits the
    # wrapper's own process group.
    exec setsid "$JAVA8_BIN" -Xmx1G -Xms512M \
        -Deverlastingskins.e2e=true \
        -cp "$SERVER_CP" cpw.mods.fml.relauncher.ServerLaunchWrapper nogui
) > "$SERVER_LOG" 2>&1 &
SERVER_PID=$!

if ! wait_for_log "$SERVER_LOG" 'For help, type "help"' "$E2E_SERVER_BOOT_TIMEOUT_S"; then
    e2e_warn "server boot timeout (tail follows)"
    tail -n 40 "$SERVER_LOG" >&2 || true
    kill_tree "$SERVER_PID" 2>/dev/null || true
    exit 2
fi
e2e_log "server booted (For help, type \"help\")"

# ---------------------------------------------------------------------------
# Client driver (era-specific)
# ---------------------------------------------------------------------------
set +e
E2E_CLIENT_DIR="$RUNNER_TMP/client-$E2E_LANE" \
E2E_MOD_JAR="$E2E_MOD_JAR" \
E2E_SENTINEL_PNG="$E2E_SENTINEL_PNG" \
E2E_SERVER_HOST="127.0.0.1" \
E2E_SERVER_PORT="$E2E_SERVER_PORT" \
E2E_JAVA8="$JAVA8_BIN" \
bash "$E2E_DRIVER_SCRIPT"
DRIVER_CODE=$?
set -e

# ---------------------------------------------------------------------------
# Assemble the final contract doc + assertions
# ---------------------------------------------------------------------------
if [ ! -f "$RESULT_JSON" ]; then
    e2e_warn "no driver result (driver exit $DRIVER_CODE)"
    exit 2
fi

# Kill the server before asserting: assertion failures exit 1 and must not
# leak the server (the cleanup trap covers every other path).
kill_tree "$SERVER_PID" 2>/dev/null || true
SERVER_PID=""

assemble_result "true" "$RESULT_JSON"

# Driver exit code FIRST (audit lib-20): the driver's exit_code is the
# authoritative mid-flow outcome and must be interpreted BEFORE the hard
# assert gates. Otherwise a join/broadcast timeout (exit 2 — the CI backoff
# target) collapses to exit 1 under the first failing gate, making retries
# impossible. Contract (master plan): 0 all green | 1 assertion failed |
# 2 retryable infra | 3 build failure (script-side, e2e_fail).
DRIVER_FINAL_CODE=$(result_exit_code)
if [ "$DRIVER_FINAL_CODE" -eq 2 ]; then
    e2e_warn "RETRYABLE: driver reported infra failure (exit 2)"
    exit 2
fi
if [ "$DRIVER_FINAL_CODE" -ne 0 ]; then
    e2e_warn "FAIL: driver exit code $DRIVER_FINAL_CODE"
    exit 1
fi

# exit_code == 0: only now run the hard assert gates (missing/invalid
# fields on a passing driver still exit 1).
assert_result "server_booted" "True"
assert_result "client_joined" "True"
assert_result "command_executed" "True"
assert_result "renderer_state" "sentinel"
assert_result "renderer_verified" "True"

e2e_log "e2e complete: exit_code=0 duration_ms=$(python3 -c "import json; print(json.load(open('$RESULT_JSON')).get('duration_ms'))")"
e2e_log "PASS: real-client E2E ($E2E_LANE) all green"
exit 0
