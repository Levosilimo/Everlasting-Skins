#!/usr/bin/env bash
# EverlastingSkins - Local E2E Test Runner
# Usage: ./run-e2e.sh [branch] [scenario]
#   branch:   1.21 (default) or mc1.12.2
#   scenario: skin-set-mojang (default), skin-clear, two-client-skin-visibility

set -euo pipefail

BRANCH="${1:-1.21}"
SCENARIO="${2:-skin-set-mojang}"
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
SCENARIO_FILE="$SCRIPT_DIR/scenarios/${SCENARIO}.json"

if [ ! -f "$SCENARIO_FILE" ]; then
    echo "ERROR: Scenario file not found: $SCENARIO_FILE"
    echo "Available scenarios:"
    ls "$SCRIPT_DIR/scenarios/"*.json 2>/dev/null || echo "  (none)"
    exit 1
fi

echo "=== EverlastingSkins E2E Test ==="
echo "Branch:    $BRANCH (MC $MC_VERSION, Forge $FORGE_VERSION)"
echo "Scenario:  $SCENARIO_FILE"
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
trap 'rm -rf "$SERVER_DIR"; echo "Cleaned up temp directory"' EXIT

mkdir -p "$SERVER_DIR/mods"
mkdir -p "$SERVER_DIR/logs"

JAR_FILE=$(ls build/libs/*.jar 2>/dev/null | head -1)
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
java -jar "$HOME/.cache/everlastingskins/$FORGE_INSTALLER" --installServer "$SERVER_DIR"

# 4. Start server
echo "[4/6] Starting Forge server..."
cd "$SERVER_DIR"
SERVER_JAR=$(ls forge-*-universal.jar forge-*-server.jar 2>/dev/null | head -1 || true)
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
    if grep -q 'For help, type "help"' "$SERVER_DIR/logs/latest.log" 2>/dev/null; then
        echo "  Server ready after ${i}s"
        break
    fi
    if [ $i -eq 60 ]; then
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
hmc.test.filename=$SCENARIO_FILE
hmc.test.leave.after=true
hmc.assets.dummy=true
hmc.always.lwjgl.flag=true
CONFIG

# 6. Run test
if [ "$BRANCH" = "1.21" ]; then
    SERVER_ARGS="--quickPlayMultiplayer 127.0.0.1:25565 --username TestPlayer"
else
    SERVER_ARGS="--server localhost --port 25565 --username TestPlayer"
fi

echo "  Server args: $SERVER_ARGS"
echo "[6/6] Running E2E test scenario: $SCENARIO (5-min fail-fast timeout)..."
cd "$PROJECT_DIR"
EXIT_CODE=0
timeout --kill-after=10 300 java -jar "$HMC_DIR/headlessmc-launcher-wrapper.jar" \
    --command launch forge:$MC_VERSION \
    --game-args "$SERVER_ARGS" || EXIT_CODE=$?

if [ $EXIT_CODE -eq 124 ]; then
    echo "=== E2E TEST TIMED OUT (no progress in 5 min) ==="
    echo "Last 100 lines of server log:"
    tail -100 "$SERVER_DIR/logs/latest.log" 2>/dev/null || true
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
    exit 124
fi

kill "$SERVER_PID" 2>/dev/null || true
wait "$SERVER_PID" 2>/dev/null || true

echo
if [ $EXIT_CODE -eq 0 ]; then
    echo "=== E2E TEST PASSED ==="
else
    echo "=== E2E TEST FAILED (exit code: $EXIT_CODE) ==="
    echo "Server logs: $SERVER_DIR/logs/latest.log"
fi
exit $EXIT_CODE
