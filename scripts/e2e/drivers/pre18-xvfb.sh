#!/usr/bin/env bash
# scripts/e2e/drivers/pre18-xvfb.sh — pre-1.8 real-client E2E driver
# (forge-1.6.4 slice 1).
#
# Boots a REAL 1.6.4 client under xvfb + Mesa software GL via the vanilla
# tweaker model (lib-8 boot mechanics, empirically re-verified in slice 1):
# the pristine vanilla client jar stays untouched; forge-universal.jar +
# launchwrapper-1.8.jar sit on the classpath as libraries; main =
# net.minecraft.launchwrapper.Launch with
# --tweakClass cpw.mods.fml.common.launcher.FMLTweaker. The mod jar
# (reobf'd, obf-named) goes into <gameDir>/mods/. The in-jar E2EDriver
# (shipped-gated by -Deverlastingskins.e2e=true) runs the phase machine and
# writes e2e-result.json into the gameDir.
#
# The client jar and every library are fetched into the shared e2e cache
# with pinned sha1s (client jar sha1 is what makes the driver's obf-domain
# reflection names stable). Java 8 is a HARD requirement (launchwrapper
# dies on 9+).
#
# Env contract (set by e2e-common.sh / lib.sh):
#   E2E_CLIENT_DIR      client gameDir (created here)
#   E2E_MOD_JAR         the lane's built mod jar
#   E2E_SENTINEL_PNG    canonical sentinel PNG (repo path)
#   E2E_SERVER_HOST     test server host (default 127.0.0.1)
#   E2E_SERVER_PORT     test server port (default 25565)
#   E2E_JAVA8           Java 8 binary (default $JAVA_HOME)
#   E2E_USERNAME        offline player (default TestPlayer)
#
# Exit codes (master-plan contract): 0 all green | 1 assertion failed |
# 2 retryable infra (boot/join timeout, artifact fetch) | 3 hard failure.

set -euo pipefail
E2E_DRIVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../lib.sh
source "$E2E_DRIVER_DIR/lib.sh"

: "${E2E_CLIENT_DIR:?pre18-xvfb.sh: E2E_CLIENT_DIR is required}"
: "${E2E_MOD_JAR:?pre18-xvfb.sh: E2E_MOD_JAR is required}"
: "${E2E_SENTINEL_PNG:?pre18-xvfb.sh: E2E_SENTINEL_PNG is required}"
: "${E2E_SERVER_HOST:=127.0.0.1}"
: "${E2E_SERVER_PORT:=25565}"
: "${E2E_JAVA8:?pre18-xvfb.sh: E2E_JAVA8 (Java 8) is required}"
: "${E2E_USERNAME:=TestPlayer}"
: "${E2E_CLIENT_TIMEOUT_S:=300}"

# ---------------------------------------------------------------------------
# Pinned 1.6.4 client artifacts (lib-8 + slice-1 empirical pinning)
# ---------------------------------------------------------------------------
CLIENT_JAR_SHA1="1703704407101cf72bd88e68579e3696ce733ecd"
CLIENT_JAR_URL="https://launcher.mojang.com/v1/objects/${CLIENT_JAR_SHA1}/client.jar"
UNIVERSAL_SHA1="eb9d954c8d057fa1768acaa40a35b864ad05c58b"
UNIVERSAL_URL="https://maven.minecraftforge.net/net/minecraftforge/forge/1.6.4-9.11.1.1345/forge-1.6.4-9.11.1.1345-universal.jar"

# name|url|sha1 — the vanilla 1.6.4 launcher library set + the tweaker-model
# extras (launchwrapper/asm/jopt-simple; authlib/log4j-api for :common at
# runtime). LWJGL 2.9.4-nightly-20150209 is the lib-8 uniform pick.
LIB_SPECS=(
  "launchwrapper-1.8.jar|https://libraries.minecraft.net/net/minecraft/launchwrapper/1.8/launchwrapper-1.8.jar|d4c0895977dd7f0b3f56281cee53a64d4c0c0322"
  "asm-all-4.1.jar|https://libraries.minecraft.net/org/ow2/asm/asm-all/4.1/asm-all-4.1.jar|054986e962b88d8660ae4566475658469595ef58"
  "lwjgl-2.9.4-nightly-20150209.jar|https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar|697517568c68e78ae0b4544145af031c81082dfe"
  "lwjgl_util-2.9.4-nightly-20150209.jar|https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl_util/2.9.4-nightly-20150209/lwjgl_util-2.9.4-nightly-20150209.jar|d51a7c040a721d13efdfbd34f8b257b2df882ad0"
  "lwjgl-platform-2.9.4-nightly-20150209-natives-linux.jar|https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-linux.jar|931074f46c795d2f7b30ed6395df5715cfd7675b"
  "jinput-2.0.5.jar|https://libraries.minecraft.net/net/java/jinput/jinput/2.0.5/jinput-2.0.5.jar|39c7796b469a600f72380316f6b1f11db6c2c7c4"
  "jinput-platform-2.0.5-natives-linux.jar|https://libraries.minecraft.net/net/java/jinput/jinput-platform/2.0.5/jinput-platform-2.0.5-natives-linux.jar|7ff832a6eb9ab6a767f1ade2b548092d0fa64795"
  "jutils-1.0.0.jar|https://libraries.minecraft.net/net/java/jutils/jutils/1.0.0/jutils-1.0.0.jar|e12fe1fda814bd348c1579329c86943d2cd3c6a6"
  "gson-2.2.2.jar|https://libraries.minecraft.net/com/google/code/gson/gson/2.2.2/gson-2.2.2.jar|1f96456ca233dec780aa224bff076d8e8bca3908"
  "guava-14.0.jar|https://libraries.minecraft.net/com/google/guava/guava/14.0/guava-14.0.jar|67b7be4ee7ba48e4828a42d6d5069761186d4a53"
  "commons-lang3-3.1.jar|https://libraries.minecraft.net/org/apache/commons/commons-lang3/3.1/commons-lang3-3.1.jar|905075e6c80f206bbe6cf1e809d2caa69f420c76"
  "commons-io-2.4.jar|https://libraries.minecraft.net/commons-io/commons-io/2.4/commons-io-2.4.jar|b1b6ea3b7e4aa4f492509a4952029cd8e48019ad"
  "codecjorbis-20101023.jar|https://libraries.minecraft.net/com/paulscode/codecjorbis/20101023/codecjorbis-20101023.jar|c73b5636faf089d9f00e8732a829577de25237ee"
  "codecwav-20101023.jar|https://libraries.minecraft.net/com/paulscode/codecwav/20101023/codecwav-20101023.jar|12f031cfe88fef5c1dd36c563c0a3a69bd7261da"
  "libraryjavasound-20101123.jar|https://libraries.minecraft.net/com/paulscode/libraryjavasound/20101123/libraryjavasound-20101123.jar|5c5e304366f75f9eaa2e8cca546a1fb6109348b3"
  "librarylwjglopenal-20100824.jar|https://libraries.minecraft.net/com/paulscode/librarylwjglopenal/20100824/librarylwjglopenal-20100824.jar|73e80d0794c39665aec3f62eee88ca91676674ef"
  "soundsystem-20120107.jar|https://libraries.minecraft.net/com/paulscode/soundsystem/20120107/soundsystem-20120107.jar|419c05fe9be71f792b2d76cfc9b67f1ed0fec7f6"
  "argo-2.25_fixed.jar|https://libraries.minecraft.net/argo/argo/2.25_fixed/argo-2.25_fixed.jar|751761ce15a3e3aaf3fc75b9f013ff8f7b88a585"
  "bcprov-jdk15on-1.47.jar|https://libraries.minecraft.net/org/bouncycastle/bcprov-jdk15on/1.47/bcprov-jdk15on-1.47.jar|b6f5d9926b0afbde9f4dbe3db88c5247be7794bb"
  "jopt-simple-4.5.jar|https://libraries.minecraft.net/net/sf/jopt-simple/jopt-simple/4.5/jopt-simple-4.5.jar|6065cc95c661255349c1d0756657be17c29a4fd3"
  "lzma-0.0.1.jar|https://libraries.minecraft.net/lzma/lzma/0.0.1/lzma-0.0.1.jar|521616dc7487b42bef0e803bd2fa3faf668101d7"
  "authlib-1.5.16.jar|https://libraries.minecraft.net/com/mojang/authlib/1.5.16/authlib-1.5.16.jar|ef1582b11fd0943d069cdcb72e99008ac209a283"
  "log4j-api-2.8.1.jar|https://libraries.minecraft.net/org/apache/logging/log4j/log4j-api/2.8.1/log4j-api-2.8.1.jar|e801d13612e22cad62a3f4f3fe7fdbe6334a8e72"
)

fetch_artifact "minecraft_client.1.6.4.jar" "$CLIENT_JAR_URL" "$CLIENT_JAR_SHA1"
fetch_artifact "forge-1.6.4-9.11.1.1345-universal.jar" "$UNIVERSAL_URL" "$UNIVERSAL_SHA1"

CACHE="$E2E_CACHE_DIR/$E2E_LANE"
CLIENT_JAR="$CACHE/minecraft_client.1.6.4.jar"
UNIVERSAL_JAR="$CACHE/forge-1.6.4-9.11.1.1345-universal.jar"

# ---------------------------------------------------------------------------
# gameDir setup: mods/, sentinel, natives
# ---------------------------------------------------------------------------
e2e_log "client gameDir: $E2E_CLIENT_DIR"
mkdir -p "$E2E_CLIENT_DIR/mods" "$E2E_CLIENT_DIR/natives"

cp "$E2E_MOD_JAR" "$E2E_CLIENT_DIR/mods/"
e2e_log "mod jar: $(basename "$E2E_MOD_JAR")"

[ -f "$E2E_SENTINEL_PNG" ] || e2e_fail "sentinel PNG missing: $E2E_SENTINEL_PNG"
cp "$E2E_SENTINEL_PNG" "$E2E_CLIENT_DIR/e2e-sentinel.png"

# Native .so extraction (lwjgl + jinput platform jars). LWJGL 2.9.4 loads
    # liblwjgl.so / libopenal.so (the -64 twins are 2.9.x legacy); jinput loads
    # libjinput-linux64.so on 64-bit.
    (
        cd "$E2E_CLIENT_DIR/natives"
        unzip -o -q "$CACHE/lwjgl-platform-2.9.4-nightly-20150209-natives-linux.jar" '*.so'
        unzip -o -q "$CACHE/jinput-platform-2.0.5-natives-linux.jar" '*.so'
        [ -f liblwjgl.so ] || ln -sf liblwjgl64.so liblwjgl.so
        [ -f libopenal.so ] || ln -sf libopenal64.so libopenal.so
    )
e2e_log "natives: $(ls "$E2E_CLIENT_DIR/natives" | tr '\n' ' ')"

# ---------------------------------------------------------------------------
# Classpath assembly + launch (tweaker model)
# ---------------------------------------------------------------------------
# Classpath: the mod jar is NOT on the classpath — FML discovers it in
    # <gameDir>/mods/ (classpath + mods/ duplicates the mod and kills the scan:
    # "Found a duplicate mod everlastingskins").
    CP="$CLIENT_JAR:$UNIVERSAL_JAR"
for spec in "${LIB_SPECS[@]}"; do
    name="${spec%%|*}"
    CP="$CP:$CACHE/$name"
done

CLIENT_LOG="$E2E_CLIENT_DIR/client.log"
JAVA8_BIN="$E2E_JAVA8"
[ -x "$JAVA8_BIN" ] || JAVA8_BIN="$E2E_JAVA8/bin/java"

e2e_log "launching client (xvfb + Mesa llvmpipe, tweaker model)..."
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
# shellcheck disable=SC2086
CLIENT_PID=$(start_guarded "$CLIENT_LOG" \
    timeout --kill-after=10 "$E2E_CLIENT_TIMEOUT_S" \
    xvfb-run -a "$JAVA8_BIN" -Xmx1G -Xms512M \
    -Djava.library.path="$E2E_CLIENT_DIR/natives" \
    -Deverlastingskins.e2e=true \
    -Dfml.ignoreInvalidMinecraftCertificates=true \
    -Dfml.ignorePatchDiscrepancies=true \
    -cp "$CP" net.minecraft.launchwrapper.Launch \
    --version 1.6.4 \
    --tweakClass cpw.mods.fml.common.launcher.FMLTweaker \
    --gameDir "$E2E_CLIENT_DIR" \
    --assetsDir "$E2E_CLIENT_DIR/assets" \
    --username "$E2E_USERNAME" \
    --session "e2e-session-token" \
    --server "$E2E_SERVER_HOST" --port "$E2E_SERVER_PORT")

RESULT_FILE="$E2E_CLIENT_DIR/e2e-result.json"
# Stale-result guard (discovered during audit remediation verification): the
# driver-wait loop below accepts ANY existing result file, so a leftover
# e2e-result.json from a previous run short-circuits the wait and produces a
# false pass. Remove it before launch so the wait is honest.
rm -f "$RESULT_FILE"

# ---------------------------------------------------------------------------
# Wait for the driver's result file (boot + join + renderer assertion)
# ---------------------------------------------------------------------------
e2e_log "waiting for driver result (up to ${E2E_CLIENT_TIMEOUT_S}s)..."
i=0
while [ "$i" -lt "$E2E_CLIENT_TIMEOUT_S" ]; do
    if [ -f "$RESULT_FILE" ]; then
        break
    fi
    if ! kill -0 "$CLIENT_PID" 2>/dev/null; then
        # Client exited before writing a result — a boot failure.
        e2e_warn "client exited early (tail of client.log follows)"
        tail -n 40 "$CLIENT_LOG" >&2 || true
        exit 2
    fi
    sleep 2
    i=$((i + 2))
done

if [ ! -f "$RESULT_FILE" ]; then
    e2e_warn "driver result timeout (tail of client.log follows)"
    tail -n 60 "$CLIENT_LOG" >&2 || true
    kill_tree "$CLIENT_PID" 2>/dev/null || true
    exit 2
fi

cp "$RESULT_FILE" "$RUNNER_TMP/e2e-result.json"
CODE=$(python3 -c "import json; print(json.load(open('$RESULT_FILE')).get('exit_code', 3))")
e2e_log "driver result: exit_code=$CODE"
kill_tree "$CLIENT_PID" 2>/dev/null || true
exit "$CODE"
