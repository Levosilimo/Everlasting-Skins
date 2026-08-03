#!/usr/bin/env bash
# EverlastingSkins - Local E2E Test Runner
# Usage: ./run-e2e.sh [branch]
#   branch: 1.21 (default) or mc1.12.2
#
# The vanilla 1.12.2 client has no console (stdin/stdout), so HeadlessMC
# SEND/ENDS_WITH/CONTAINS scenario steps cannot drive it. This runner is a
# server-log smoke test: start the Forge server with the mod, launch a
# headless client, and assert on the server log that the server booted,
# the mod loaded, and the client joined.

set -euo pipefail

BRANCH="${1:-1.21}"
HMC_VERSION="2.10.0"
HMC_DIR_NAME="HeadlessMC"

# Determine branch directory
if [ "$BRANCH" = "mc1.12.2" ]; then
    BRANCH_DIR="mc1.12.2"
    FORGE_VERSION="14.23.5.2847"
    MC_VERSION="1.12.2"
else
    BRANCH_DIR="1.21"
    FORGE_VERSION="51.0.24"
    MC_VERSION="1.21"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== EverlastingSkins E2E Test ==="
echo "Branch:    $BRANCH (MC $MC_VERSION, Forge $FORGE_VERSION)"
echo

# 1. Build the mod
echo "[1/6] Building mod..."
cd "$PROJECT_DIR/$BRANCH_DIR"
if [ ! -x gradlew ]; then
    chmod +x gradlew
fi
./gradlew build --no-daemon

# 2. Prepare server directory
echo "[2/6] Setting up test server..."
SERVER_DIR="$(mktemp -d)"
trap 'if [ -n "${SERVER_PID:-}" ]; then kill "$SERVER_PID" 2>/dev/null || true; fi; rm -rf "$SERVER_DIR"; echo "Cleaned up temp directory"' EXIT

mkdir -p "$SERVER_DIR/mods"
mkdir -p "$SERVER_DIR/logs"

JAR_FILE=$(ls build/libs/*.jar 2>/dev/null | grep -v -- '-sources.jar' | grep -v -- '-javadoc.jar' | head -1)
if [ -z "$JAR_FILE" ]; then
    echo "ERROR: No mod JAR found in build/libs/"
    exit 1
fi
cp "$JAR_FILE" "$SERVER_DIR/mods/"
echo "  Copied mod: $(basename "$JAR_FILE")"

cp "$SCRIPT_DIR/server/server.properties" "$SERVER_DIR/"
cp "$SCRIPT_DIR/server/eula.txt" "$SERVER_DIR/"

# 3. Download Forge server if not cached
echo "[3/6] Installing Forge server..."
FORGE_INSTALLER="forge-$MC_VERSION-$FORGE_VERSION-installer.jar"
if [ ! -f "$HOME/.cache/everlastingskins/$FORGE_INSTALLER" ]; then
    mkdir -p "$HOME/.cache/everlastingskins"
    FORGE_URL="https://maven.minecraftforge.net/net/minecraftforge/forge/$MC_VERSION-$FORGE_VERSION/forge-$MC_VERSION-$FORGE_VERSION-installer.jar"
    echo "  Downloading $FORGE_URL..."
    curl -L -o "$HOME/.cache/everlastingskins/$FORGE_INSTALLER" "$FORGE_URL"
fi
# The 2847 installer ignores a --installServer target arg and installs
# into the current directory, so cd into the server dir first.
(
    cd "$SERVER_DIR"
    java -jar "$HOME/.cache/everlastingskins/$FORGE_INSTALLER" --installServer
)

# HMCLite's forgecli crashes on 1.12.2 installers (NoSuchMethodError on
# ClientInstall.run); pre-install the client Forge profile matching the
# server so the launcher skips its own forge install.
# The 14.23.5.2847 installer has no --installClient CLI; the 14.23.5.2860
# rebuild does, so the client profile is installed with it (server stays
# 2847, the canonical build version).
if [ "$BRANCH" = "mc1.12.2" ]; then
    echo "  Pre-installing client Forge 14.23.5.2860 (bypasses forgecli)..."
    CLIENT_INSTALLER="forge-1.12.2-14.23.5.2860-installer.jar"
    if [ ! -f "$HOME/.cache/everlastingskins/$CLIENT_INSTALLER" ]; then
        mkdir -p "$HOME/.cache/everlastingskins"
        curl -L -o "$HOME/.cache/everlastingskins/$CLIENT_INSTALLER" \
            "https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2860/forge-1.12.2-14.23.5.2860-installer.jar"
    fi
    mkdir -p "$HOME/.minecraft"
    echo '{"profiles":{}}' > "$HOME/.minecraft/launcher_profiles.json"
    java -jar "$HOME/.cache/everlastingskins/$CLIENT_INSTALLER" --installClient "$HOME/.minecraft"
fi

# 4. Start server
echo "[4/6] Starting Forge server..."
cd "$SERVER_DIR"
SERVER_JAR=$(ls forge-*-universal.jar forge-*-server.jar forge-*.jar 2>/dev/null | grep -v -- '-installer.jar' | head -1 || true)
if [ -z "$SERVER_JAR" ]; then
    SERVER_JAR="$(ls minecraft_server*.jar 2>/dev/null | head -1)"
fi
if [ -z "$SERVER_JAR" ]; then
    echo "ERROR: No server JAR found in $SERVER_DIR"
    ls -la "$SERVER_DIR"
    exit 1
fi
java -Xmx1G -Xms512M -jar "$SERVER_JAR" nogui &
SERVER_PID=$!
echo "  Server PID: $SERVER_PID"

echo "  Waiting for server to start..."
for i in $(seq 1 60); do
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "ERROR: server process exited during startup"
        cat "$SERVER_DIR/logs/latest.log" 2>/dev/null || true
        exit 1
    fi
    if grep -q 'For help, type "help"' "$SERVER_DIR/logs/latest.log" 2>/dev/null; then
        echo "  Server ready after ${i}s"
        break
    fi
    if [ "$i" -eq 60 ]; then
        echo "ERROR: Server did not start within 5 minutes"
        cat "$SERVER_DIR/logs/latest.log" 2>/dev/null || true
        kill "$SERVER_PID" 2>/dev/null || true
        exit 1
    fi
    sleep 5
done

# 5. Download HeadlessMC if needed
echo "[5/6] Setting up HeadlessMC..."
HMC_DIR="$SCRIPT_DIR/headlessmc"
mkdir -p "$HMC_DIR"
if [ ! -f "$HMC_DIR/headlessmc-launcher-wrapper.jar" ]; then
    echo "  Downloading HeadlessMC $HMC_VERSION..."
    curl -L -o "$HMC_DIR/headlessmc-launcher-wrapper.jar" \
        "https://github.com/headlesshq/headlessmc/releases/download/$HMC_VERSION/headlessmc-launcher-wrapper-$HMC_VERSION.jar"
fi

# Configure HeadlessMC for test
HMC_CONFIG_DIR="$PROJECT_DIR/$HMC_DIR_NAME"
mkdir -p "$HMC_CONFIG_DIR"
cat > "$HMC_CONFIG_DIR/config.properties" << CONFIG
hmc.java.versions=$JAVA_HOME/bin/java
hmc.gamedir=$PROJECT_DIR/headlessmc-run
hmc.mcdir=$HOME/.minecraft
hmc.offline=true
hmc.offline.username=TestPlayer
hmc.rethrow.launch.exceptions=true
hmc.exit.on.failed.command=true
hmc.assets.dummy=true
hmc.always.lwjgl.flag=true
hmc.jline.enabled=false
hmc.auto.download.specifics=false
hmc.crash.report.watcher=true
CONFIG

# 6. Launch client and assert on the server log
echo "[6/6] Launching client and asserting server log..."
cd "$PROJECT_DIR"
# Client profile was installed with the 2860 installer, so the uid must
# match that profile even though the server runs the canonical 2847 build.
CLIENT_UID="14.23.5.2860"
timeout --kill-after=10 300 xvfb-run -a java -jar "$HMC_DIR/headlessmc-launcher-wrapper.jar" \
    --command "launch forge:$MC_VERSION -lwjgl -offline --uid $CLIENT_UID --jvm -Djava.awt.headless=true" \
    --game-args "--server=127.0.0.1 --port=25565 --username TestPlayer" > "$HMC_DIR/client.log" 2>&1 &
CLIENT_PID=$!

JOINED=0
for i in $(seq 1 90); do
    if grep -q "TestPlayer joined the game" "$SERVER_DIR/logs/latest.log" 2>/dev/null; then
        JOINED=1
        break
    fi
    if ! kill -0 "$CLIENT_PID" 2>/dev/null; then
        echo "ERROR: client process exited early"
        break
    fi
    sleep 2
done

kill "$CLIENT_PID" 2>/dev/null || true
wait "$CLIENT_PID" 2>/dev/null || true

echo "=== E2E Assertions ==="
FAILED=0
if grep -q 'For help, type "help"' "$SERVER_DIR/logs/latest.log"; then
    echo "PASS: server booted"
else
    echo "FAIL: server did not boot"
    FAILED=1
fi
# Forge 1.12.2 names the mod id at INFO only in the FML mod-list handshake
# lines written when the client joins; assert on that signal after the join.
if grep -Eq 'Attempting connection with missing mods \[[^]]*everlastingskins' "$SERVER_DIR/logs/latest.log" \
    || grep -Eq 'Client attempting to join with [0-9]+ mods : [^ ]*everlastingskins' "$SERVER_DIR/logs/latest.log"; then
    echo "PASS: mod discovered (FML handshake mod-list line)"
else
    echo "FAIL: mod id not found in FML handshake mod-list line"
    FAILED=1
fi
if [ "$JOINED" -eq 1 ]; then
    echo "PASS: client connected"
else
    echo "FAIL: client did not connect"
    FAILED=1
fi

if [ "$FAILED" -eq 0 ]; then
    echo "=== E2E PASSED ==="
else
    echo "=== E2E FAILED ==="
    echo "Server logs: $SERVER_DIR/logs/latest.log"
    tail -50 "$SERVER_DIR/logs/latest.log" 2>/dev/null || true
    tail -20 "$HMC_DIR/client.log" 2>/dev/null || true
fi
exit $FAILED
