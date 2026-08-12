#!/usr/bin/env bash
# scripts/e2e/drivers/pre18-xvfb.sh — pre-1.8 real-client E2E driver
# (forge-1.6.4 slice 1 + 1.5.2/1.4.7 slice 1 merge model).
#
# Two era models, selected by $E2E_ERA (default "1.6.4-tweaker"):
#
# 1.6.4-tweaker (unchanged from slice 1): boots a REAL 1.6.4 client under
# xvfb + Mesa software GL via the vanilla tweaker model (lib-8 boot
# mechanics, empirically re-verified in slice 1): the pristine vanilla
# client jar stays untouched; forge-universal.jar + launchwrapper-1.8.jar sit
# on the classpath as libraries; main =
# net.minecraft.launchwrapper.Launch with
# --tweakClass cpw.mods.fml.common.launcher.FMLTweaker.
#
# merge (1.4.7/1.5.2, lib-8 MERGE model): the obf client jar is merged with
# the FML universal zip (universal's PATCHED classes win:
# net/minecraft/client/Minecraft, MinecraftApplet, ClientBrandRetriever),
# producing a self-contained Forge client; main =
# net.minecraft.client.Minecraft; NO launchwrapper/tweaker. The mod jar goes
# into <gameDir>/mods/; the game dir is pinned via
# -Dminecraft.applet.TargetDirectory=<gameDir> (FML 4.7/5.2's
# computeExistingClientHome relocates Minecraft's static minecraftDir to it
# — the pre-launchwrapper launcher mechanism) AND CWD=gameDir (the FML
# relaunch log is CWD-relative). 1.5.2/1.4.7 Minecraft.main parses no
# --server/--port args, so the in-jar E2EDriver auto-connects via
# Minecraft.setServer at @Mod.Init.
#
# In both models the in-jar E2EDriver (shipped-gated by
# -Deverlastingskins.e2e=true) runs the phase machine and writes
# e2e-result.json into the gameDir.
#
# E2E_ROLE selects WHICH in-jar driver boots (lib-23 gap (d), observer
# fan-out proof):
#   actor    (default) — -Deverlastingskins.e2e=true, --username TestPlayer
#             (E2E_USERNAME); result copied to $RUNNER_TMP/e2e-result.json
#   observer — -Deverlastingskins.e2e.observer=true, --username
#             ObserverPlayer, NO commands; result copied to
#             $RUNNER_TMP/e2e-result-observer.json
# The observer role is used by e2e-common.sh's two-client orchestration.
#
# The client jar and every library are fetched into the shared e2e cache
# with pinned sha1s (client jar sha1 is what keeps the driver's referenced
# member names stable). Java 8 is a HARD requirement (launchwrapper dies on
# 9+; the merge-model JVM is Java 8 too).
#
# Env contract (set by e2e-common.sh / lib.sh):
#   E2E_ERA             "1.6.4-tweaker" (default) | "merge"
#   E2E_CLIENT_DIR      client gameDir (created here)
#   E2E_MOD_JAR         the lane's built mod jar
#   E2E_SENTINEL_PNG    canonical sentinel PNG (repo path)
#   E2E_SERVER_HOST     test server host (default 127.0.0.1)
#   E2E_SERVER_PORT     test server port (default 25565)
#   E2E_JAVA8           Java 8 binary (default $JAVA_HOME)
#   E2E_USERNAME        offline player (default TestPlayer; the observer role
#                       ignores this and uses ObserverPlayer)
#   E2E_ROLE            actor (default) | observer
#
# Exit codes (master-plan contract): 0 all green | 1 assertion failed |
# 2 retryable infra (boot/join timeout, artifact fetch) | 3 hard failure.

set -euo pipefail
E2E_DRIVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../lib.sh
source "$E2E_DRIVER_DIR/lib.sh"

: "${E2E_ERA:=1.6.4-tweaker}"
: "${E2E_CLIENT_DIR:?pre18-xvfb.sh: E2E_CLIENT_DIR is required}"
: "${E2E_MOD_JAR:?pre18-xvfb.sh: E2E_MOD_JAR is required}"
: "${E2E_SENTINEL_PNG:?pre18-xvfb.sh: E2E_SENTINEL_PNG is required}"
: "${E2E_SERVER_HOST:=127.0.0.1}"
: "${E2E_SERVER_PORT:=25565}"
: "${E2E_JAVA8:?pre18-xvfb.sh: E2E_JAVA8 (Java 8) is required}"
: "${E2E_USERNAME:=TestPlayer}"
: "${E2E_ROLE:=actor}"
: "${E2E_CLIENT_TIMEOUT_S:=300}"

case "$E2E_ROLE" in
    actor|observer) ;;
    *) e2e_fail "pre18-xvfb.sh: unknown E2E_ROLE '$E2E_ROLE' (actor|observer)" ;;
esac

if [ "$E2E_ROLE" = observer ]; then
    # The observer is a NON-ACTOR client: distinct username (no commands,
    # no op needed) and the observer in-jar driver (E2EObserverDriver).
    E2E_USERNAME="ObserverPlayer"
    E2E_JVM_PROPERTY="-Deverlastingskins.e2e.observer=true"
    RESULT_COPY="$RUNNER_TMP/e2e-result-observer.json"
else
    E2E_JVM_PROPERTY="-Deverlastingskins.e2e=true"
    RESULT_COPY="$RUNNER_TMP/e2e-result.json"
fi

# ---------------------------------------------------------------------------
# Pinned client artifacts per era/lane (lib-8 + slice-1 empirical pinning).
# The 1.6.4 pins are the slice-1 originals; the 1.4.7/1.5.2 client sha1s are
# the version_manifest downloads.client sha1s already pinned in the lanes'
# build.gradle vendoredInputs; the universal sha1s were computed from the
# vendored universal.zip files.
# ---------------------------------------------------------------------------
CACHE="$E2E_CACHE_DIR/$E2E_LANE"

if [ "$E2E_ERA" = "1.6.4-tweaker" ]; then
    CLIENT_JAR_NAME="minecraft_client.1.6.4.jar"
    CLIENT_JAR_SHA1="1703704407101cf72bd88e68579e3696ce733ecd"
    CLIENT_JAR_URL="https://launcher.mojang.com/v1/objects/${CLIENT_JAR_SHA1}/client.jar"
    UNIVERSAL_NAME="forge-1.6.4-9.11.1.1345-universal.jar"
    UNIVERSAL_SHA1="eb9d954c8d057fa1768acaa40a35b864ad05c58b"
    UNIVERSAL_URL="https://maven.minecraftforge.net/net/minecraftforge/forge/1.6.4-9.11.1.1345/forge-1.6.4-9.11.1.1345-universal.jar"
elif [ "$E2E_ERA" = "merge" ]; then
    case "$E2E_LANE" in
        1.5.2)
            CLIENT_JAR_NAME="minecraft_client.1.5.2.jar"
            CLIENT_JAR_SHA1="465378c9dc2f779ae1d6e8046ebc46fb53a57968"
            CLIENT_JAR_URL="https://launcher.mojang.com/v1/objects/${CLIENT_JAR_SHA1}/client.jar"
            UNIVERSAL_NAME="forge-1.5.2-7.8.1.738-universal.zip"
            UNIVERSAL_SHA1="76223709288287a6a8d22ab16b43a6ab2a284a0d"
            UNIVERSAL_URL="https://maven.minecraftforge.net/net/minecraftforge/forge/1.5.2-7.8.1.738/forge-1.5.2-7.8.1.738-universal.zip"
            ;;
        1.4.7)
            CLIENT_JAR_NAME="minecraft_client.1.4.7.jar"
            CLIENT_JAR_SHA1="53ed4b9d5c358ecfff2d8b846b4427b888287028"
            CLIENT_JAR_URL="https://launcher.mojang.com/v1/objects/${CLIENT_JAR_SHA1}/client.jar"
            UNIVERSAL_NAME="forge-1.4.7-6.6.2.534-universal.zip"
            UNIVERSAL_SHA1="bd0f40a78c18140265ff042a96d73f01c4f60906"
            UNIVERSAL_URL="https://maven.minecraftforge.net/net/minecraftforge/forge/1.4.7-6.6.2.534/forge-1.4.7-6.6.2.534-universal.zip"
            ;;
        *)
            e2e_fail "merge era: unsupported lane $E2E_LANE (expect 1.4.7 or 1.5.2)"
            ;;
    esac
else
    e2e_fail "unknown E2E_ERA '$E2E_ERA' (expect 1.6.4-tweaker or merge)"
fi

# name|url|sha1 — the vanilla launcher library set + the tweaker-model
# extras (launchwrapper/asm/jopt-simple; authlib/log4j-api for :common at
# runtime). LWJGL 2.9.4-nightly-20150209 is the lib-8 uniform pick. The
# merge model skips the tweaker-only extras (launchwrapper/asm/jopt).
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

fetch_artifact "$CLIENT_JAR_NAME" "$CLIENT_JAR_URL" "$CLIENT_JAR_SHA1"
fetch_artifact "$UNIVERSAL_NAME" "$UNIVERSAL_URL" "$UNIVERSAL_SHA1"

# Every classpath library is pinned + fetched here (cached+verified is a
# no-op); the 1.6.4 slice relied on a pre-populated cache, which breaks on a
# fresh machine.
for spec in "${LIB_SPECS[@]}"; do
    name="${spec%%|*}"
    url="${spec#*|}"; url="${url%%|*}"
    sha1="${spec##*|}"
    fetch_artifact "$name" "$url" "$sha1"
done

CLIENT_JAR="$CACHE/$CLIENT_JAR_NAME"
UNIVERSAL_JAR="$CACHE/$UNIVERSAL_NAME"

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
# Classpath assembly (era-dependent; tweaker-only extras skipped on merge)
# ---------------------------------------------------------------------------
if [ "$E2E_ERA" = "1.6.4-tweaker" ]; then
    # Classpath: the mod jar is NOT on the classpath — FML discovers it in
    # <gameDir>/mods/ (classpath + mods/ duplicates the mod and kills the scan:
    # "Found a duplicate mod everlastingskins").
    CP="$CLIENT_JAR:$UNIVERSAL_JAR"
    for spec in "${LIB_SPECS[@]}"; do
        name="${spec%%|*}"
        CP="$CP:$CACHE/$name"
    done
else
    # Merge model: the FMLRelauncher needs its CoreFMLLibraries in
    # <gameDir>/lib (dead fmllibs download otherwise hard-fails the boot —
    # see seed_fml_libdir in lib.sh).
    seed_fml_libdir "$E2E_CLIENT_DIR" "$E2E_LANE"

    # Merge model: self-contained Forge client = obf client jar + the
    # universal zip's patched classes (universal wins: Minecraft,
    # MinecraftApplet, ClientBrandRetriever — the FML 4.7/5.2 bootstrap lives
    # in the patched Minecraft constructor). Rebuilt every run (deterministic,
    # few seconds); the mod jar stays out of the classpath (mods/ discovery).
    MERGE_DIR="$CACHE/merge-work-$E2E_LANE"
    MERGED_JAR="$CACHE/merged-client-$E2E_LANE.jar"
    rm -rf "$MERGE_DIR"
    mkdir -p "$MERGE_DIR"
    e2e_log "assembling merged client jar (universal wins over client)..."
    unzip -q -o "$CLIENT_JAR" -d "$MERGE_DIR"
    unzip -q -o "$UNIVERSAL_JAR" -d "$MERGE_DIR"
    ( cd "$MERGE_DIR" && zip -qr "$MERGED_JAR" . )
    rm -rf "$MERGE_DIR"
    e2e_log "merged client jar: $(basename "$MERGED_JAR") ($(du -h "$MERGED_JAR" | cut -f1))"

    CP="$MERGED_JAR"
    for spec in "${LIB_SPECS[@]}"; do
        name="${spec%%|*}"
        case "$name" in
            launchwrapper-1.8.jar|asm-all-4.1.jar|jopt-simple-4.5.jar)
                # Tweaker-model extras; the merge model has no launchwrapper.
                continue
                ;;
        esac
        CP="$CP:$CACHE/$name"
    done
fi

CLIENT_LOG="$E2E_CLIENT_DIR/client.log"
JAVA8_BIN="$E2E_JAVA8"
[ -x "$JAVA8_BIN" ] || JAVA8_BIN="$E2E_JAVA8/bin/java"

export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe

# LWJGL 2.x Display.<clinit> shells out to the external `xrandr -q` binary to
# enumerate screens (XRandR.populate()); when the binary is missing the exec
# fails, the screen map stays empty and getScreenNames()[0] throws
# ArrayIndexOutOfBoundsException: 0 -> Display init dies with "No OpenGL
# context found in the current thread" (seen live on the ubuntu-24.04 CI
# runner, which ships xvfb but NOT x11-xserver-utils/xrandr). Fail fast with
# an actionable message instead of the cryptic LWJGL crash.
if ! command -v xrandr >/dev/null 2>&1; then
    e2e_fail "xrandr (package x11-xserver-utils) is required for the LWJGL 2 client GL boot"
fi

# Run Xvfb with an explicit 24-bit screen: xvfb-run's default screen config
# differs across hosts/images, and an 8-bit root breaks LWJGL 2 GL context
# creation (the classic headless-CI GL failure). 1280x1024x24 matches the
# slice-1 locally-proven-green configuration.
XVFB_SERVER_ARGS="-screen 0 1280x1024x24"
if [ "$E2E_ERA" = "1.6.4-tweaker" ]; then
    e2e_log "launching client (xvfb + Mesa llvmpipe, tweaker model)..."
    # shellcheck disable=SC2086
    CLIENT_PID=$(start_guarded "$CLIENT_LOG" \
        timeout --kill-after=10 "$E2E_CLIENT_TIMEOUT_S" \
        xvfb-run -a -s "$XVFB_SERVER_ARGS" "$JAVA8_BIN" -Xmx1G -Xms512M \
        -Djava.library.path="$E2E_CLIENT_DIR/natives" \
        $E2E_JVM_PROPERTY \
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
else
    e2e_log "launching client (xvfb + Mesa llvmpipe, merge model)..."
    # CWD=gameDir (FML relaunch log) + minecraft.applet.TargetDirectory
    # (FML 4.7/5.2 relocates Minecraft's minecraftDir to the gameDir). The
    # driver auto-connects via Minecraft.setServer at @Mod.Init — no
    # --server/--port args exist on this line's main. The role JVM property
    # (E2E_JVM_PROPERTY: -Deverlastingskins.e2e=true for the actor,
    # -Deverlastingskins.e2e.observer=true for the observer) rides through
    # as $10 so the observer client boots the observer driver, not a second
    # actor.
    # shellcheck disable=SC2016
    CLIENT_PID=$(start_guarded "$CLIENT_LOG" bash -c '
        cd "$1" || exit 3
        exec timeout --kill-after=10 "$8" xvfb-run -a -s "$9" "$2" -Xmx1G -Xms512M \
            -Djava.library.path="$3" \
            -Dminecraft.applet.TargetDirectory="$1" \
            $10 \
            -Deverlastingskins.e2e.server="$4" \
            -Deverlastingskins.e2e.port="$5" \
            -cp "$6" net.minecraft.client.Minecraft "$7" "e2e-session-token"
    ' _ "$E2E_CLIENT_DIR" "$JAVA8_BIN" "$E2E_CLIENT_DIR/natives" \
        "$E2E_SERVER_HOST" "$E2E_SERVER_PORT" "$CP" "$E2E_USERNAME" "$E2E_CLIENT_TIMEOUT_S" "$XVFB_SERVER_ARGS" "$E2E_JVM_PROPERTY")
fi

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

cp "$RESULT_FILE" "$RESULT_COPY"
if [ "$E2E_ROLE" = observer ]; then
    CODE=$(python3 -c "import json; print(json.load(open('$RESULT_FILE')).get('observer_exit_code', 3))")
else
    CODE=$(python3 -c "import json; print(json.load(open('$RESULT_FILE')).get('exit_code', 3))")
fi
e2e_log "driver result ($E2E_ROLE): exit_code=$CODE"
e2e_log "driver result: exit_code=$CODE"
kill_tree "$CLIENT_PID" 2>/dev/null || true
exit "$CODE"
