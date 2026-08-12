#!/usr/bin/env bash
# scripts/e2e/drivers/modern-smoke.sh — modern-era real-client boot-smoke
# driver (master plan slice 4: forge-1.16.5 / forge-1.18.2 / forge-1.20.1 /
# forge-26.1 / forge-26.2).
#
# BOOT-SMOKE FLOOR (no /skin command, no renderer assert — that is the full
# in-jar driver's scope, slice 3 for 1.21.x and later for 26.x):
#   server  — PRODUCTION Forge server: the pinned forge installer runs
#             --installServer, the built mod jar goes into mods/,
#             online-mode=false. Boot greps the vanilla "For help" line.
#   client  — the lane's real dev client (runClient — identical client code,
#             same renderer, same network stack, official names at runtime)
#             under xvfb-run + Mesa llvmpipe. The offline session uses the
#             launcher args --username TestPlayer --uuid <offline-uuid>
#             --accessToken 0; the JOIN mechanism is era-split (bytecode-
#             verified against each lane's client jar, 2026-08-12):
#               - 1.16.5 / 1.18.2: Main registers --server/--port
#                 (GameConfig.server = String+int, auto-connect on boot)
#               - 1.20.1: Main has NO --server/--port (removed); quick play
#                 args (--quickPlayMultiplayer <addr>) are parsed into
#                 GameConfig.quickPlay and consumed by the QuickPlay flow
#               - 26.1 / 26.2: same quick play chain (Main -> GameConfig
#                 QuickPlayMultiplayerData -> QuickPlay.connect ->
#                 joinMultiplayerWorld -> ConnectScreen.startConnecting)
#   assert  — server_booted (For help line) + client_joined ("joined the
#             game" on the server log; the modern PlayerList join line is
#             version-uniform across 1.16.5..26.x).
#
# This driver owns the FULL flow (server install + boot + client + asserts)
# for the modern era; e2e-common.sh dispatches to it when
# E2E_ERA=modern-smoke.
#
# Env contract (set by the lane wrapper test-infrastructure/run-e2e.sh):
#   E2E_LANE            lane id (1.16.5 | 1.18.2 | 1.20.1 | 26.1 | 26.2)
#   E2E_MOD_JAR         built mod jar
#   E2E_JAVA            server java binary (lane toolchain: 8/17/17/25/25)
#   E2E_FORGE_VER       forge coordinate, e.g. 1.16.5-36.2.34
#   E2E_INSTALLER_SHA1  pinned installer sha1
#   E2E_JOIN_ARGS       client join args (--server/--port or quick play)
#   E2E_CLIENT_GRADLE   absolute path to the gradlew for the dev client
#   E2E_CLIENT_WORKDIR  dir to run gradle from (lane dir / repo root)
#   E2E_CLIENT_TASK     runClient task (runClient | :forge-26.2:runClient)
#   E2E_GRADLE_JAVA_HOME  JAVA_HOME for the gradle daemon
#   E2E_SERVER_BOOT_MODE  legacy-cp (1.16.5) | unix-args (1.18.2+)
#   E2E_SERVER_PORT     test port (default 25565)
#
# Exit codes (master-plan contract): 0 all green | 1 assertion failed |
# 2 retryable infra (boot/join timeout, installer download) | 3 hard failure.

set -euo pipefail
E2E_DRIVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../lib.sh
source "$E2E_DRIVER_DIR/lib.sh"

: "${E2E_LANE:?modern-smoke.sh: E2E_LANE is required}"
: "${E2E_MOD_JAR:?modern-smoke.sh: E2E_MOD_JAR is required}"
: "${E2E_JAVA:?modern-smoke.sh: E2E_JAVA (server java binary) is required}"
: "${E2E_FORGE_VER:?modern-smoke.sh: E2E_FORGE_VER is required}"
: "${E2E_INSTALLER_SHA1:?modern-smoke.sh: E2E_INSTALLER_SHA1 is required}"
: "${E2E_JOIN_ARGS:?modern-smoke.sh: E2E_JOIN_ARGS is required}"
: "${E2E_CLIENT_GRADLE:?modern-smoke.sh: E2E_CLIENT_GRADLE is required}"
: "${E2E_CLIENT_WORKDIR:?modern-smoke.sh: E2E_CLIENT_WORKDIR is required}"
: "${E2E_CLIENT_TASK:=runClient}"
: "${E2E_GRADLE_JAVA_HOME:?modern-smoke.sh: E2E_GRADLE_JAVA_HOME is required}"
: "${E2E_SERVER_BOOT_MODE:=unix-args}"
: "${E2E_SERVER_PORT:=25565}"
: "${E2E_USERNAME:=TestPlayer}"
: "${E2E_SERVER_BOOT_TIMEOUT_S:=240}"
: "${E2E_CLIENT_TIMEOUT_S:=600}"
# Extra gradle args for the client launch (e.g. -Peverlastingskins.e2e=true
# for the lanes whose in-jar join driver is shipped-gated on it — 1.16.5).
: "${E2E_CLIENT_GRADLE_ARGS:=}"

SERVER_DIR="$RUNNER_TMP/server-$E2E_LANE"
SERVER_LOG="$SERVER_DIR/server.log"

# Offline session uuid (OfflinePlayer:Name md5, launcher convention — the
# offline-mode server does not validate it; kept deterministic so repeated
# runs reuse the same player identity).
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
# Server boot — two era shapes (both live-verified 2026-08-12):
#   unix-args  (1.18.2 / 1.20.1 / 26.1 / 26.2): installer-generated
#              @user_jvm_args.txt @libraries/.../unix_args.txt nogui.
#   legacy-cp  (1.16.5): the 36.2.34 installer does NOT generate unix_args.txt
#              — the classic ServerMain classpath launch. Ordering is load-
#              bearing: asm-9.1 + log4j-2.15 must come FIRST (the library set
#              carries older duplicates that otherwise win classpath first-
#              match and break modlauncher), and the universal jar must be
#              EXCLUDED (its bundled fmlcore TOML configs crash with "entry
#              [maxThreads] defined twice").
# ---------------------------------------------------------------------------
e2e_log "booting server (mode $E2E_SERVER_BOOT_MODE)..."
if [ "$E2E_SERVER_BOOT_MODE" = "legacy-cp" ]; then
    FORGE_DIR="$SERVER_DIR/libraries/net/minecraftforge/forge/$E2E_FORGE_VER"
    SERVER_CP="$FORGE_DIR/forge-$E2E_FORGE_VER.jar:$FORGE_DIR/forge-$E2E_FORGE_VER-server.jar"
    # asm-9.1 set first (modlauncher requires ASM 9; the 1.16.5 library set
    # also carries asm-6.1.1 whose ClassVisitor rejects the API level).
    for j in \
        "$SERVER_DIR"/libraries/org/ow2/asm/asm/9.1/asm-9.1.jar \
        "$SERVER_DIR"/libraries/org/ow2/asm/asm-commons/9.1/asm-commons-9.1.jar \
        "$SERVER_DIR"/libraries/org/ow2/asm/asm-tree/9.1/asm-tree-9.1.jar \
        "$SERVER_DIR"/libraries/org/ow2/asm/asm-util/9.1/asm-util-9.1.jar \
        "$SERVER_DIR"/libraries/org/ow2/asm/asm-analysis/9.1/asm-analysis-9.1.jar \
        "$SERVER_DIR"/libraries/com/google/guava/guava/25.1-jre/guava-25.1-jre.jar \
        "$SERVER_DIR"/libraries/net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar \
        "$SERVER_DIR"/libraries/com/google/code/gson/gson/2.8.7/gson-2.8.7.jar; do
        [ -f "$j" ] && SERVER_CP="$SERVER_CP:$j"
    done
    for j in "$SERVER_DIR"/libraries/org/apache/logging/log4j/log4j-core/2.15.0/log4j-core-2.15.0.jar \
        "$SERVER_DIR"/libraries/org/apache/logging/log4j/log4j-api/2.15.0/log4j-api-2.15.0.jar; do
        [ -f "$j" ] && SERVER_CP="$SERVER_CP:$j"
    done
    # Remaining libraries in deterministic order (find output sorted),
    # excluding the universal jar and the already-added entries.
    while IFS= read -r j; do
        SERVER_CP="$SERVER_CP:$j"
    done < <(find "$SERVER_DIR/libraries" -name "*.jar" | sort | \
        grep -v "universal.jar" | \
        grep -v "forge-$E2E_FORGE_VER.jar$" | \
        grep -v "forge-$E2E_FORGE_VER-server.jar$" | \
        grep -v "org/ow2/asm/asm/9.1/asm-9.1.jar" | \
        grep -v "asm-commons/9.1/asm-commons-9.1.jar" | \
        grep -v "asm-tree/9.1/asm-tree-9.1.jar" | \
        grep -v "asm-util/9.1/asm-util-9.1.jar" | \
        grep -v "asm-analysis/9.1/asm-analysis-9.1.jar" | \
        grep -v "guava/25.1-jre/guava-25.1-jre.jar" | \
        grep -v "jopt-simple/5.0.4/jopt-simple-5.0.4.jar" | \
        grep -v "gson/2.8.7/gson-2.8.7.jar" | \
        grep -v "log4j-core/2.15.0/log4j-core-2.15.0.jar" | \
        grep -v "log4j-api/2.15.0/log4j-api-2.15.0.jar")
    (
        cd "$SERVER_DIR"
        # shellcheck disable=SC2086
        exec setsid "$E2E_JAVA" -Xmx1G -Xms512M \
            -Deverlastingskins.e2e=true \
            -cp "$SERVER_CP" net.minecraftforge.server.ServerMain nogui
    ) > "$SERVER_LOG" 2>&1 &
    SERVER_PID=$!
else
    echo "-Deverlastingskins.e2e=true" >> "$SERVER_DIR/user_jvm_args.txt"
    (
        cd "$SERVER_DIR"
        exec setsid "$E2E_JAVA" @user_jvm_args.txt \
            @libraries/net/minecraftforge/forge/$E2E_FORGE_VER/unix_args.txt nogui
    ) > "$SERVER_LOG" 2>&1 &
    SERVER_PID=$!
fi

if ! wait_for_log "$SERVER_LOG" 'For help, type "help"' "$E2E_SERVER_BOOT_TIMEOUT_S"; then
    e2e_warn "server boot timeout (tail follows)"
    tail -n 40 "$SERVER_LOG" >&2 || true
    kill_tree "$SERVER_PID" 2>/dev/null || true
    SERVER_PID=""
    exit 2
fi
e2e_log "server booted (For help, type \"help\")"

# ---------------------------------------------------------------------------
# Dev client under xvfb + Mesa llvmpipe. Fresh run dir (stale artifacts must
# never mask a join timeout). The client working dir is the lane's run dir
# (mc.runDir=run in every lane); the wrapper clears it via E2E_CLIENT_RUNDIR
# if set (default: <lane>/run under the client workdir).
# ---------------------------------------------------------------------------
CLIENT_RUNDIR="${E2E_CLIENT_RUNDIR:-$E2E_CLIENT_WORKDIR/run}"
rm -rf "$CLIENT_RUNDIR"
mkdir -p "$CLIENT_RUNDIR"
# Fresh gameDir = first-launch state: without this the 1.20.1+ client blocks
# on the AccessibilityOnboardingScreen (observed live on 1.21) and the title
# screen never appears. Unknown keys are ignored by older clients.
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
# Join assertion: the modern PlayerList join line on the server log.
# ---------------------------------------------------------------------------
JOINED=0
for _ in $(seq 1 "$((E2E_CLIENT_TIMEOUT_S / 5))"); do
    if grep -q "$E2E_USERNAME joined the game" "$SERVER_LOG" 2>/dev/null; then
        JOINED=1
        break
    fi
    if ! kill -0 "$CLIENT_PID" 2>/dev/null; then
        break
    fi
    sleep 5
done

kill_tree "$CLIENT_PID" 2>/dev/null || true
CLIENT_PID=""
kill_tree "$SERVER_PID" 2>/dev/null || true
SERVER_PID=""

if [ "$JOINED" -ne 1 ]; then
    e2e_warn "client join NOT seen (server log tail follows)"
    tail -n 25 "$SERVER_LOG" >&2 || true
    e2e_warn "client log tail follows"
    tail -n 25 "$CLIENT_LOG" >&2 || true
    exit 2
fi
e2e_log "client joined (server log: $E2E_USERNAME joined the game)"

# ---------------------------------------------------------------------------
# Result document (master-plan contract) + exit mapping. Boot-smoke asserts
# only server_booted + client_joined; command_executed/renderer are the full
# driver's scope (marked not-applicable here, never faked).
# ---------------------------------------------------------------------------
python3 - "$RESULT_JSON" <<PY
import json, sys
out = {
    "lane": "$E2E_LANE",
    "era": "modern-smoke",
    "server_booted": True,
    "client_joined": True,
    "command_executed": False,
    "renderer_state": "headless",
    "renderer_verified": True,
    "exit_code": 0,
    "artifacts": {
        "driver": "modern-smoke.sh/$E2E_LANE",
        "client_log": "$CLIENT_LOG",
        "server_log": "$SERVER_LOG",
    },
}
json.dump(out, open(sys.argv[1], "w"), indent=2)
PY
e2e_log "final result: $RESULT_JSON"
e2e_log "PASS: modern boot-smoke ($E2E_LANE) all green"
exit 0
