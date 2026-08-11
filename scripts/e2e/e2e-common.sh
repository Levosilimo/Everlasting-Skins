#!/usr/bin/env bash
# scripts/e2e/e2e-common.sh — shared real-client E2E orchestrator
# (master-plan contract; every era implements the same shape).
#
# Flow: prepare server dir (mods/, sentinel, server.properties) → boot the
# real server (bg) → wait for the vanilla "For help, type \"help\"" boot
# line → launch the OBSERVER client (no commands) and wait for its join
# marker → launch the ACTOR client (era driver) → wait for BOTH result
# files → merge actor + observer documents into
# ${RUNNER_TMP}/e2e-result.json (observer_* fields additive) → assert the
# contract fields → map exit codes (0 all green | 1 assertion failed | 2
# retryable infra | 3 build/hard failure). The serialized launch (observer
# FIRST) makes the fan-out delivery deterministic: the observer is in-world
# before the actor joins, so the sentinel hook's post-connection
# re-broadcast lands the PNG on the wire after the actor is spawned on the
# observer (lib-23 gap (d) coverage).
#
# Era deltas (E2E_ERA, default "1.6.4-tweaker"):
#   - 1.6.4-tweaker: server boots as cpw.mods.fml.relauncher.ServerLaunchWrapper
#     nogui with server.jar FIRST + universal.jar (the 1.6.4 universal has no
#     patched MC classes; the tweaker model owns the boot).
#   - merge (1.4.7/1.5.2): the FML universal zip carries the PATCHED
#     MinecraftServer whose main() FML-bootstraps the process, so the
#     universal zip must come FIRST on the classpath (classpath first-match
#     wins) and the boot main is net.minecraft.server.MinecraftServer nogui.
#     The tweaker-only libs (launchwrapper/asm/jopt-simple) are dropped.
#
# Env contract (set by the lane wrapper test-infrastructure/run-e2e.sh):
#   E2E_LANE           lane id (1.6.4 / 1.5.2 / 1.4.7)
#   E2E_ERA            "1.6.4-tweaker" (default) | "merge"
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

# ---------------------------------------------------------------------------
# Era dispatch: the headlessmc era driver owns the FULL flow (server boot +
# client + assertions) — the legacy pre-1.12 forge servers boot from the
# e2e cache with per-lane lib sets, and the client is driven by HeadlessMC
# (bridge specifics for 1.7.10/1.8.9, in-jar driver for 1.10.2). The
# headlessmc lane wrappers do not export E2E_SERVER_JAR (no vendored
# vanilla server), so this dispatch runs BEFORE the shared env guards.
# ---------------------------------------------------------------------------
if [ "${E2E_ERA:-}" = "headlessmc" ]; then
    exec bash "$E2E_DRIVER_SCRIPT"
fi

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
# Pre-1.7.10 ops model: ops.txt (one username per line) — lets the offline
# test player pass the command's permission gate (verified on 1.6.4: without
# op the server replies "You do not have permission").
printf '%s\n' "$E2E_USERNAME" > "$SERVER_DIR/ops.txt"

# ---------------------------------------------------------------------------
# Era pins: forge universal artifact + server boot main
# ---------------------------------------------------------------------------
CACHE="$E2E_CACHE_DIR/$E2E_LANE"
if [ "${E2E_ERA:-1.6.4-tweaker}" = "1.6.4-tweaker" ]; then
    UNIVERSAL_NAME="forge-1.6.4-9.11.1.1345-universal.jar"
    UNIVERSAL_URL="https://maven.minecraftforge.net/net/minecraftforge/forge/1.6.4-9.11.1.1345/forge-1.6.4-9.11.1.1345-universal.jar"
    UNIVERSAL_SHA1="eb9d954c8d057fa1768acaa40a35b864ad05c58b"
    SERVER_MAIN="cpw.mods.fml.relauncher.ServerLaunchWrapper"
    SERVER_MAIN_ARGS="nogui"
else
    case "$E2E_LANE" in
        1.5.2)
            UNIVERSAL_NAME="forge-1.5.2-7.8.1.738-universal.zip"
            UNIVERSAL_URL="https://maven.minecraftforge.net/net/minecraftforge/forge/1.5.2-7.8.1.738/forge-1.5.2-7.8.1.738-universal.zip"
            UNIVERSAL_SHA1="76223709288287a6a8d22ab16b43a6ab2a284a0d"
            ;;
        1.4.7)
            UNIVERSAL_NAME="forge-1.4.7-6.6.2.534-universal.zip"
            UNIVERSAL_URL="https://maven.minecraftforge.net/net/minecraftforge/forge/1.4.7-6.6.2.534/forge-1.4.7-6.6.2.534-universal.zip"
            UNIVERSAL_SHA1="bd0f40a78c18140265ff042a96d73f01c4f60906"
            ;;
        *)
            e2e_fail "merge era: unsupported lane $E2E_LANE (expect 1.4.7 or 1.5.2)"
            ;;
    esac
    SERVER_MAIN="net.minecraft.server.MinecraftServer"
    SERVER_MAIN_ARGS="nogui"
fi

fetch_artifact "$UNIVERSAL_NAME" "$UNIVERSAL_URL" "$UNIVERSAL_SHA1"

# ---------------------------------------------------------------------------
# Server library fetch (both eras; cached+verified is a no-op). The legacy
# FML relauncher pre-seed below additionally needs asm-all on the cache.
# launchwrapper-1.8.jar + jopt-simple-4.5.jar are the tweaker-model boot
# libs (SERVER_CP references both); they were missing from this fetch loop
# (only the client driver pre18-xvfb.sh fetched them), so a fresh cache
# silently dropped the classpath entries and the 1.6.4 server died with
# ClassNotFoundException: net.minecraft.launchwrapper.Launch on main's CI.
# sha1s match the client driver's pins (same artifacts).
# ---------------------------------------------------------------------------
for spec in \
    "launchwrapper-1.8.jar|https://libraries.minecraft.net/net/minecraft/launchwrapper/1.8/launchwrapper-1.8.jar|d4c0895977dd7f0b3f56281cee53a64d4c0c0322" \
    "jopt-simple-4.5.jar|https://libraries.minecraft.net/net/sf/jopt-simple/jopt-simple/4.5/jopt-simple-4.5.jar|6065cc95c661255349c1d0756657be17c29a4fd3" \
    "guava-14.0.jar|https://libraries.minecraft.net/com/google/guava/guava/14.0/guava-14.0.jar|67b7be4ee7ba48e4828a42d6d5069761186d4a53" \
    "gson-2.2.2.jar|https://libraries.minecraft.net/com/google/code/gson/gson/2.2.2/gson-2.2.2.jar|1f96456ca233dec780aa224bff076d8e8bca3908" \
    "commons-lang3-3.1.jar|https://libraries.minecraft.net/org/apache/commons/commons-lang3/3.1/commons-lang3-3.1.jar|905075e6c80f206bbe6cf1e809d2caa69f420c76" \
    "commons-io-2.4.jar|https://libraries.minecraft.net/commons-io/commons-io/2.4/commons-io-2.4.jar|b1b6ea3b7e4aa4f492509a4952029cd8e48019ad" \
    "argo-2.25_fixed.jar|https://libraries.minecraft.net/argo/argo/2.25_fixed/argo-2.25_fixed.jar|751761ce15a3e3aaf3fc75b9f013ff8f7b88a585" \
    "lzma-0.0.1.jar|https://libraries.minecraft.net/lzma/lzma/0.0.1/lzma-0.0.1.jar|521616dc7487b42bef0e803bd2fa3faf668101d7" \
    "bcprov-jdk15on-1.47.jar|https://libraries.minecraft.net/org/bouncycastle/bcprov-jdk15on/1.47/bcprov-jdk15on-1.47.jar|b6f5d9926b0afbde9f4dbe3db88c5247be7794bb" \
    "asm-all-4.1.jar|https://libraries.minecraft.net/org/ow2/asm/asm-all/4.1/asm-all-4.1.jar|054986e962b88d8660ae4566475658469595ef58" \
    "authlib-1.5.16.jar|https://libraries.minecraft.net/com/mojang/authlib/1.5.16/authlib-1.5.16.jar|ef1582b11fd0943d069cdcb72e99008ac209a283" \
    "log4j-api-2.8.1.jar|https://libraries.minecraft.net/org/apache/logging/log4j/log4j-api/2.8.1/log4j-api-2.8.1.jar|e801d13612e22cad62a3f4f3fe7fdbe6334a8e72"; do
    name="${spec%%|*}"
    url="${spec#*|}"; url="${url%%|*}"
    sha1="${spec##*|}"
    fetch_artifact "$name" "$url" "$sha1"
done

# Merge lanes: pre-seed the FMLRelauncher lib dir (dead fmllibs download
# otherwise hard-fails the boot — see seed_fml_libdir in lib.sh).
if [ "${E2E_ERA:-1.6.4-tweaker}" != "1.6.4-tweaker" ]; then
    seed_fml_libdir "$SERVER_DIR" "$E2E_LANE"
fi

# ---------------------------------------------------------------------------
# Server classpath: vanilla server + forge universal + mod + shared libs
# (the launcher library set minus the client-only lwjgl/paulscode legs;
# authlib/log4j-api for :common at runtime). The mod jar is deliberately NOT
# on the classpath — FML discovers it in <serverDir>/mods/.
# ---------------------------------------------------------------------------
if [ "${E2E_ERA:-1.6.4-tweaker}" = "1.6.4-tweaker" ]; then
    SERVER_CP="$E2E_SERVER_JAR:$CACHE/$UNIVERSAL_NAME"
    for lib in launchwrapper-1.8.jar asm-all-4.1.jar jopt-simple-4.5.jar guava-14.0.jar \
        gson-2.2.2.jar commons-lang3-3.1.jar commons-io-2.4.jar argo-2.25_fixed.jar \
        lzma-0.0.1.jar \
        bcprov-jdk15on-1.47.jar authlib-1.5.16.jar log4j-api-2.8.1.jar; do
        SERVER_CP="$SERVER_CP:$CACHE/$lib"
    done
else
    # Universal FIRST: the 1.4.7/1.5.2 universal carries the patched
    # MinecraftServer (FMLRelauncher.handleServerRelaunch bootstrap); the
    # JVM loads the first class found on the classpath.
    SERVER_CP="$CACHE/$UNIVERSAL_NAME:$E2E_SERVER_JAR"
    for lib in guava-14.0.jar \
        gson-2.2.2.jar commons-lang3-3.1.jar commons-io-2.4.jar argo-2.25_fixed.jar \
        lzma-0.0.1.jar \
        bcprov-jdk15on-1.47.jar authlib-1.5.16.jar log4j-api-2.8.1.jar; do
        SERVER_CP="$SERVER_CP:$CACHE/$lib"
    done
fi
[ -z "$E2E_SERVER_CP_EXTRA" ] || SERVER_CP="$SERVER_CP:$E2E_SERVER_CP_EXTRA"

# ---------------------------------------------------------------------------
# Boot the real server (bg, own session). FML resolves mods/ relative to the
# server's home (CWD on the merge lanes; the tweaker lane's launcher dir is
# the CWD too), so the server MUST run from the server dir (the wrapper runs
# from the lane dir).
# ---------------------------------------------------------------------------
SERVER_LOG="$SERVER_DIR/server.log"
SERVER_PID=""
cleanup() {
    if [ -n "$SERVER_PID" ]; then
        kill_tree "$SERVER_PID" 2>/dev/null || true
    fi
    # The observer script's own client is reaped by its 420s timeout if this
    # run fails early (no pkill by project policy).
    if [ -n "${OBSERVER_SCRIPT_PID:-}" ]; then
        kill "$OBSERVER_SCRIPT_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

e2e_log "booting server (pid tracking via session)..."
(
    cd "$SERVER_DIR"
    # setsid: own session/PGID so kill_tree (group kill) never hits the
    # wrapper's own process group.
    # shellcheck disable=SC2086
    exec setsid "$JAVA8_BIN" -Xmx1G -Xms512M \
        -Deverlastingskins.e2e=true \
        -cp "$SERVER_CP" "$SERVER_MAIN" $SERVER_MAIN_ARGS
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
# Observer client FIRST (serialized launch, lib-23 gap (d)): the observer
# must be in-world BEFORE the actor joins, so the sentinel hook's delayed
# re-broadcast (10s after the actor's connection) lands the PNG on the wire
# after the actor is spawned in the observer's world. The observer runs NO
# commands; its join marker (ES_E2E_JOIN in its client log) gates the actor
# launch.
# ---------------------------------------------------------------------------
OBSERVER_DIR="$RUNNER_TMP/client-$E2E_LANE-observer"
OBSERVER_LOG="$OBSERVER_DIR/client.log"
OBSERVER_RESULT="$RUNNER_TMP/e2e-result-observer.json"
OBSERVER_SCRIPT_PID=""
# The observer's process budget is longer than the actor's: its assert phase
# polls the TDI until the actor's re-broadcast injection lands.
set +e
E2E_CLIENT_DIR="$OBSERVER_DIR" \
E2E_ROLE=observer \
E2E_CLIENT_TIMEOUT_S=420 \
E2E_MOD_JAR="$E2E_MOD_JAR" \
E2E_SENTINEL_PNG="$E2E_SENTINEL_PNG" \
E2E_SERVER_HOST="127.0.0.1" \
E2E_SERVER_PORT="$E2E_SERVER_PORT" \
E2E_JAVA8="$JAVA8_BIN" \
bash "$E2E_DRIVER_SCRIPT" &
OBSERVER_SCRIPT_PID=$!
set -e

if ! wait_for_log "$OBSERVER_LOG" 'ES_E2E_JOIN' 240; then
    e2e_warn "observer join timeout (tail of observer client.log follows)"
    tail -n 40 "$OBSERVER_LOG" >&2 || true
    exit 2
fi
e2e_log "observer joined (ES_E2E_JOIN)"

# ---------------------------------------------------------------------------
# Actor client (era-specific) — launched only after the observer is in-world.
# ---------------------------------------------------------------------------
# Drop any stale result doc from a previous run: a driver timeout must never
# be masked by an old driver's result file (observed live: a failed 1.6.4
# run's doc was asserted as a 1.5.2 pass candidate).
rm -f "$RESULT_JSON"
set +e
E2E_CLIENT_DIR="$RUNNER_TMP/client-$E2E_LANE" \
E2E_ROLE=actor \
E2E_MOD_JAR="$E2E_MOD_JAR" \
E2E_SENTINEL_PNG="$E2E_SENTINEL_PNG" \
E2E_SERVER_HOST="127.0.0.1" \
E2E_SERVER_PORT="$E2E_SERVER_PORT" \
E2E_JAVA8="$JAVA8_BIN" \
bash "$E2E_DRIVER_SCRIPT"
DRIVER_CODE=$?
set -e

# ---------------------------------------------------------------------------
# Wait for the observer's result file (the observer script runs in the
# background; its own wait loop caps at E2E_CLIENT_TIMEOUT_S).
# ---------------------------------------------------------------------------
i=0
while [ "$i" -lt 240 ] && [ ! -f "$OBSERVER_RESULT" ]; do
    if ! kill -0 "$OBSERVER_SCRIPT_PID" 2>/dev/null && [ ! -f "$OBSERVER_RESULT" ]; then
        # Observer script exited without producing a result — boot failure.
        break
    fi
    sleep 2
    i=$((i + 2))
done

# ---------------------------------------------------------------------------
# Assemble the final contract doc + assertions
# ---------------------------------------------------------------------------
if [ ! -f "$RESULT_JSON" ]; then
    e2e_warn "no driver result (driver exit $DRIVER_CODE)"
    exit 2
fi
if [ ! -f "$OBSERVER_RESULT" ]; then
    e2e_warn "no observer result (observer script exit $OBSERVER_SCRIPT_PID)"
    tail -n 40 "$OBSERVER_LOG" >&2 || true
    exit 2
fi

# Kill the server before asserting: assertion failures exit 1 and must not
# leak the server (the cleanup trap covers every other path).
kill_tree "$SERVER_PID" 2>/dev/null || true
SERVER_PID=""

assemble_result "true" "$RESULT_JSON" "$OBSERVER_RESULT"

# Driver exit code FIRST (audit lib-20): the driver's exit_code is the
# authoritative mid-flow outcome and must be interpreted BEFORE the hard
# assert gates. Otherwise a join/broadcast timeout (exit 2 — the CI backoff
# target) collapses to exit 1 under the first failing gate, making retries
# impossible. Contract (master plan): 0 all green | 1 assertion failed |
# 2 retryable infra | 3 build failure (script-side, e2e_fail).
DRIVER_FINAL_CODE=$(result_exit_code)
OBSERVER_FINAL_CODE=$(observer_exit_code)
if [ "$DRIVER_FINAL_CODE" -eq 2 ] || [ "$OBSERVER_FINAL_CODE" -eq 2 ]; then
    e2e_warn "RETRYABLE: driver/observer reported infra failure (exit 2)"
    exit 2
fi
if [ "$DRIVER_FINAL_CODE" -ne 0 ] || [ "$OBSERVER_FINAL_CODE" -ne 0 ]; then
    e2e_warn "FAIL: driver exit code $DRIVER_FINAL_CODE, observer exit code $OBSERVER_FINAL_CODE"
    exit 1
fi

# exit_code == 0: only now run the hard assert gates (missing/invalid
# fields on a passing driver still exit 1).
assert_result "server_booted" "True"
assert_result "client_joined" "True"
assert_result "command_executed" "True"
assert_result "renderer_state" "sentinel"
assert_result "renderer_verified" "True"
assert_result "observer_joined" "True"
assert_result "observer_renderer_state" "sentinel"
assert_result "observer_renderer_verified" "True"

e2e_log "e2e complete: exit_code=0 duration_ms=$(python3 -c "import json; print(json.load(open('$RESULT_JSON')).get('duration_ms'))")"
e2e_log "PASS: real-client E2E ($E2E_LANE) all green"
exit 0
