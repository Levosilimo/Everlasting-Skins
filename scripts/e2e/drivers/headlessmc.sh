#!/usr/bin/env bash
# scripts/e2e/drivers/headlessmc.sh — HeadlessMC real-client E2E driver
# (master plan slice 2: forge-1.7.10 / forge-1.8.9 bridge lanes, the
# forge-1.10.2 in-jar-driver lane, and the mc1.12.2 bridge lane).
#
# The pre-1.12 vanilla clients have a real console, so HeadlessMC (hmc)
# drives them deterministically: the launcher runs the game with
# -lwjgl (headless rendering stub), the scenario file (hmc.test.filename)
# drives stdin/stdout with SEND / CONTAINS / WAIT steps, and CommandTest
# reports success. Two lane shapes (lib-10 design):
#
#   bridge (1.7.10 / 1.8.9): hmc-specifics (lexforge) ships a Forge tweaker
#   that pipes game chat through stdin — the scenario SENDs "/skin set ..."
#   as if typed in chat, and the ". hmc-e2e-bridge-ok" bridge command
#   acknowledges. The server-side ES_E2E_SKIN=ok sentinel (SkinAction under
#   -Deverlastingskins.e2e=true) is the primary assertion.
#
#   in-jar driver (1.10.2): no 1.10 module exists in hmc-specifics, so the
#   lane ships an in-jar E2EDriver (shipped-gated by the same property) that
#   sends /skin through the real chat surface and writes e2e-result.json
#   into the gameDir. The scenario just waits for the driver's markers; the
#   result file is the primary assertion. (Forge 1.10.2's FMLSecurityManager
#   traps System.exit, so the game idles after the result write and the
#   launcher's CommandTest completion terminates it — the driver guards its
#   shutdown tick against clobbering the result file.)
#
#   bridge (1.12.2): same shape as 1.7.10/1.8.9 — the vanilla 1.12.2 client
#   has NO stdin console (removed before 1.12; verified against the MCP
#   client jar), so the lexforge specifics jar is REQUIRED: its
#   HeadlessMcMcTweaker + mixins provide the stdin->chat bridge the
#   scenario SENDs through. The server profile is installed with the
#   forge-1.12.2-14.23.5.2860 installer (the canonical 2847 installer has no
#   --installClient CLI, and HMCLite's forgecli crashes on 1.12.2 installers
#   — see the lane wrapper history). The server-side ES_E2E_SKIN sentinel
#   (mc1.12.2 SkinCommand/SkinAction under -Deverlastingskins.e2e=true,
#   mirrored from 1.7.10) is the primary assertion. S5 (run 31567935416):
#   the 1.12.2 lane's scenario readiness gate is the CLIENT-side join chat
#   line ("TestPlayer joined the game"), NOT minecraft:music.game — the
#   dummy-asset client WARNs that sound event pre-play, which raced the FML
#   handshake and lost the /skin SEND. The driver also HARD-waits the
#   server-side join line (exit 2 on timeout) and asserts the ES_E2E_SKIN=cmd
#   entry marker (command_reached_server contract field, exit 1 if the join
#   succeeded but the command never reached the server).
#
# This driver owns the FULL flow (server boot + client + assertions) for
# the headlessmc era; e2e-common.sh dispatches to it when E2E_ERA=headlessmc.
#
# Env contract (set by the lane wrapper test-infrastructure/run-e2e.sh):
#   E2E_LANE           lane id (1.7.10 | 1.8.9 | 1.10.2 | mc1.12.2)
#   E2E_MOD_JAR        built mod jar
#   E2E_JAVA8          Java 8 binary
#   E2E_SERVER_PORT    test port (default 25565)
#   E2E_USERNAME       offline player (default TestPlayer)
#   E2E_HMC_VERSION    headlessmc release (default 2.10.0)
#   E2E_HMC_SPECIFICS_JAR  bridge-lane specifics jar (default: lane
#                          test-infrastructure/hmc-specifics/*.jar)
#
# Exit codes (master-plan contract): 0 all green | 1 assertion failed |
# 2 retryable infra (boot/join timeout, artifact fetch) | 3 hard failure.

set -euo pipefail
E2E_DRIVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../lib.sh
source "$E2E_DRIVER_DIR/lib.sh"

: "${E2E_LANE:?headlessmc.sh: E2E_LANE is required}"
: "${E2E_MOD_JAR:?headlessmc.sh: E2E_MOD_JAR is required}"
: "${E2E_JAVA8:?headlessmc.sh: E2E_JAVA8 (Java 8) is required}"
: "${E2E_SERVER_PORT:=25565}"
: "${E2E_USERNAME:=TestPlayer}"
: "${E2E_HMC_VERSION:=2.10.0}"
: "${E2E_SERVER_BOOT_TIMEOUT_S:=240}"
: "${E2E_CLIENT_TIMEOUT_S:=420}"
# S5: hard join gate window — the server must log the player's in-world
# join line within this before any assertion is meaningful (mirrors
# modern-smoke.sh's join assert; scenario CONTAINS gate timeout is 240).
: "${E2E_JOIN_TIMEOUT_S:=240}"

CACHE="$E2E_CACHE_DIR/$E2E_LANE"
HMC_DIR="$RUNNER_TMP/hmc-$E2E_LANE"
CLIENT_DIR="$RUNNER_TMP/client-$E2E_LANE"
SERVER_DIR="$RUNNER_TMP/server-$E2E_LANE"
SERVER_LOG="$SERVER_DIR/server.log"

# ---------------------------------------------------------------------------
# Per-lane pins (all verified live in the slice-2 trials; sha1 = the
# launcher.mojang.com object id for the server jars).
# ---------------------------------------------------------------------------
case "$E2E_LANE" in
        1.7.10)
        HMC_SPECIFICS_NAME="hmc-specifics-1.7.10-2.4.0-lexforge-release.jar"
        MC_VERSION="1.7.10"
        MC_VERSION_ID="1.7.10-Forge10.13.4.1614-1.7.10"
        FORGE_UID="10.13.4.1614"
        FORGE_VERSION="1.7.10-10.13.4.1614-1.7.10"
        SERVER_SHA1="952438ac4e01b4d115c5fc38f891710c4941df29"
        FORGE_SHA1="25fd97f72beca728112256938e03e8105b1b78cc"
        SERVER_MAIN="cpw.mods.fml.relauncher.ServerLaunchWrapper"
        # Post-#423 grammar: the 1.7.10 lane's /skin is
        # `set <mojang|web|random>` (bare `set Notch` hits the usage reply
        # and never runs the skin action — the slice-2 trial jar predated
        # the parity rework, so its bare-name command masked this).
        SCENARIO_SKIN_CMD="/skin set mojang Notch"
        ;;
    1.8.9)
        HMC_SPECIFICS_NAME="hmc-specifics-1.8.9-2.4.0-lexforge-release.jar"
        MC_VERSION="1.8.9"
        MC_VERSION_ID="1.8.9-forge1.8.9-11.15.1.2318-1.8.9"
        FORGE_UID="11.15.1.2318"
        FORGE_VERSION="1.8.9-11.15.1.2318-1.8.9"
        SERVER_SHA1="b58b2ceb36e01bcd8dbf49c8fb66c55a9f0676cd"
        FORGE_SHA1="beda619c465af293e63952dd573c137c17c0a4cd"
        SERVER_MAIN="net.minecraftforge.fml.relauncher.ServerLaunchWrapper"
        SCENARIO_SKIN_CMD="/skin set mojang Notch"
        ;;
    1.10.2)
        HMC_SPECIFICS_NAME=""
        MC_VERSION="1.10.2"
        MC_VERSION_ID="1.10.2-forge1.10.2-12.18.3.2511"
        FORGE_UID="12.18.3.2511"
        FORGE_VERSION="1.10.2-12.18.3.2511"
        SERVER_SHA1="3d501b23df53c548254f5e3f66492d178a48db63"
        FORGE_SHA1="7560ca0432084f1b34d8b355371ba5889000544a"
        SERVER_MAIN="net.minecraftforge.fml.relauncher.ServerLaunchWrapper"
        SCENARIO_SKIN_CMD=""
        ;;
    mc1.12.2)
        HMC_SPECIFICS_NAME="hmc-specifics-1.12.2-2.4.0-lexforge-release.jar"
        MC_VERSION="1.12.2"
        # The client profile is installed by the forge-1.12.2-14.23.5.2860
        # installer (--installClient), which names it with a DASH after
        # "forge" (unlike the modern Forge profile naming) — the driver
        # launches this exact id.
        MC_VERSION_ID="1.12.2-forge-14.23.5.2860"
        FORGE_UID="14.23.5.2860"
        # Server side stays on the canonical 2847 build (the E2E-verified
        # version of record); the client profile is 2860 (see the lane
        # wrapper history for why the client cannot use 2847).
        FORGE_VERSION="1.12.2-14.23.5.2847"
        SERVER_SHA1="886945bfb2b978778c3a0288fd7fab09d315b25f"
        FORGE_SHA1="c15dbf708064a9db9a9d66dd84688b9f31b6006e"
        SERVER_MAIN="net.minecraftforge.fml.relauncher.ServerLaunchWrapper"
        SCENARIO_SKIN_CMD="/skin set mojang Notch"
        # The 1.12.2 client loads the PROFILE's standalone jline-3.5.1 (a
        # forge dep) ahead of the specifics' shaded copy; its UnixTerminal
        # execs `stty` with the inherited piped stdin and BLOCKS forever
        # (observed live: client froze in the reader's terminal init, server
        # read-timed-out the join). Forcing the dumb terminal skips the
        # unix/exec path entirely — the reader then polls plain stdin lines.
        HMC_LAUNCH_EXTRA="--jvm -Dhmc.jline.dumb=true"
        ;;
    *)
        e2e_fail "headlessmc era: unsupported lane $E2E_LANE (expect 1.7.10, 1.8.9, 1.10.2 or mc1.12.2)"
        ;;
esac

# Server library set (name|url|sha1) — the FML ServerLaunchWrapper libs,
# verified in the slice-2 trials.
if [ "$E2E_LANE" = "1.7.10" ]; then
    LIB_SPECS=(
        "launchwrapper-1.12.jar|https://libraries.minecraft.net/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar|111e7bea9c968cdb3d06ef4632bf7ff0824d0f36"
        "asm-all-5.0.3.jar|https://libraries.minecraft.net/org/ow2/asm/asm-all/5.0.3/asm-all-5.0.3.jar|4333508b8dd8ee72aa4e39afa713b3a74579b773"
        "akka-actor_2.11-2.3.3.jar|https://maven.minecraftforge.net/com/typesafe/akka/akka-actor_2.11/2.3.3/akka-actor_2.11-2.3.3.jar|ed62e9fc709ca0f2ff1a3220daa8b70a2870078e"
        "config-1.2.1.jar|https://maven.minecraftforge.net/com/typesafe/config/1.2.1/config-1.2.1.jar|f771f71fdae3df231bcd54d5ca2d57f0bf93f467"
        "scala-library-2.11.1.jar|https://maven.minecraftforge.net/org/scala-lang/scala-library/2.11.1/scala-library-2.11.1.jar|0e11da23da3eabab9f4777b9220e60d44c1aab6a"
        "lzma-0.0.1.jar|https://libraries.minecraft.net/lzma/lzma/0.0.1/lzma-0.0.1.jar|521616dc7487b42bef0e803bd2fa3faf668101d7"
        "jopt-simple-4.5.jar|https://libraries.minecraft.net/net/sf/jopt-simple/jopt-simple/4.5/jopt-simple-4.5.jar|6065cc95c661255349c1d0756657be17c29a4fd3"
        "guava-17.0.jar|https://libraries.minecraft.net/com/google/guava/guava/17.0/guava-17.0.jar|9c6ef172e8de35fd8d4d8783e4821e57cdef7445"
        "commons-lang3-3.3.2.jar|https://libraries.minecraft.net/org/apache/commons/commons-lang3/3.3.2/commons-lang3-3.3.2.jar|90a3822c38ec8c996e84c16a3477ef632cbc87a3"
        "commons-io-2.4.jar|https://libraries.minecraft.net/commons-io/commons-io/2.4/commons-io-2.4.jar|b1b6ea3b7e4aa4f492509a4952029cd8e48019ad"
        "gson-2.2.4.jar|https://libraries.minecraft.net/com/google/code/gson/gson/2.2.4/gson-2.2.4.jar|a60a5e993c98c864010053cb901b7eab25306568"
        "authlib-1.5.16.jar|https://libraries.minecraft.net/com/mojang/authlib/1.5.16/authlib-1.5.16.jar|ef1582b11fd0943d069cdcb72e99008ac209a283"
        "netty-all-4.0.10.Final.jar|https://libraries.minecraft.net/io/netty/netty-all/4.0.10.Final/netty-all-4.0.10.Final.jar|9e50bd52ffe257a0e2cd8d971688d6ce7d174325"
        "log4j-api-2.0-beta9.jar|https://libraries.minecraft.net/org/apache/logging/log4j/log4j-api/2.0-beta9/log4j-api-2.0-beta9.jar|1dd66e68cccd907880229f9e2de1314bd13ff785"
        "log4j-core-2.0-beta9.jar|https://libraries.minecraft.net/org/apache/logging/log4j/log4j-core/2.0-beta9/log4j-core-2.0-beta9.jar|678861ba1b2e1fccb594bb0ca03114bb05da9695"
        "argo-2.25_fixed.jar|https://libraries.minecraft.net/argo/argo/2.25_fixed/argo-2.25_fixed.jar|751761ce15a3e3aaf3fc75b9f013ff8f7b88a585"
        "bcprov-jdk15on-1.47.jar|https://libraries.minecraft.net/org/bouncycastle/bcprov-jdk15on/1.47/bcprov-jdk15on-1.47.jar|b6f5d9926b0afbde9f4dbe3db88c5247be7794bb"
    )
elif [ "$E2E_LANE" = "1.8.9" ]; then
    LIB_SPECS=(
        "launchwrapper-1.12.jar|https://libraries.minecraft.net/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar|111e7bea9c968cdb3d06ef4632bf7ff0824d0f36"
        "asm-all-5.0.3.jar|https://libraries.minecraft.net/org/ow2/asm/asm-all/5.0.3/asm-all-5.0.3.jar|4333508b8dd8ee72aa4e39afa713b3a74579b773"
        "jline-2.13.jar|https://maven.minecraftforge.net/jline/jline/2.13/jline-2.13.jar|2d9530d0a25daffaffda7c35037b046b627bb171"
        "akka-actor_2.11-2.3.3.jar|https://maven.minecraftforge.net/com/typesafe/akka/akka-actor_2.11/2.3.3/akka-actor_2.11-2.3.3.jar|ed62e9fc709ca0f2ff1a3220daa8b70a2870078e"
        "config-1.2.1.jar|https://maven.minecraftforge.net/com/typesafe/config/1.2.1/config-1.2.1.jar|f771f71fdae3df231bcd54d5ca2d57f0bf93f467"
        "scala-library-2.11.1.jar|https://maven.minecraftforge.net/org/scala-lang/scala-library/2.11.1/scala-library-2.11.1.jar|0e11da23da3eabab9f4777b9220e60d44c1aab6a"
        "lzma-0.0.1.jar|https://libraries.minecraft.net/lzma/lzma/0.0.1/lzma-0.0.1.jar|521616dc7487b42bef0e803bd2fa3faf668101d7"
        "trove4j-3.0.3.jar|https://libraries.minecraft.net/net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar|42ccaf4761f0dfdfa805c9e340d99a755907e2dd"
        "jopt-simple-4.6.jar|https://libraries.minecraft.net/net/sf/jopt-simple/jopt-simple/4.6/jopt-simple-4.6.jar|306816fb57cf94f108a43c95731b08934dcae15c"
        "guava-17.0.jar|https://libraries.minecraft.net/com/google/guava/guava/17.0/guava-17.0.jar|9c6ef172e8de35fd8d4d8783e4821e57cdef7445"
        "commons-lang3-3.3.2.jar|https://libraries.minecraft.net/org/apache/commons/commons-lang3/3.3.2/commons-lang3-3.3.2.jar|90a3822c38ec8c996e84c16a3477ef632cbc87a3"
        "commons-io-2.4.jar|https://libraries.minecraft.net/commons-io/commons-io/2.4/commons-io-2.4.jar|b1b6ea3b7e4aa4f492509a4952029cd8e48019ad"
        "commons-codec-1.9.jar|https://libraries.minecraft.net/commons-codec/commons-codec/1.9/commons-codec-1.9.jar|9ce04e34240f674bc72680f8b843b1457383161a"
        "gson-2.2.4.jar|https://libraries.minecraft.net/com/google/code/gson/gson/2.2.4/gson-2.2.4.jar|a60a5e993c98c864010053cb901b7eab25306568"
        "authlib-1.5.21.jar|https://libraries.minecraft.net/com/mojang/authlib/1.5.21/authlib-1.5.21.jar|aefba0d5b53fbcb70860bc8046ab95d5854c07a5"
        "netty-all-4.0.23.Final.jar|https://libraries.minecraft.net/io/netty/netty-all/4.0.23.Final/netty-all-4.0.23.Final.jar|0294104aaf1781d6a56a07d561e792c5d0c95f45"
        "log4j-api-2.0-beta9.jar|https://libraries.minecraft.net/org/apache/logging/log4j/log4j-api/2.0-beta9/log4j-api-2.0-beta9.jar|1dd66e68cccd907880229f9e2de1314bd13ff785"
        "log4j-core-2.0-beta9.jar|https://libraries.minecraft.net/org/apache/logging/log4j/log4j-core/2.0-beta9/log4j-core-2.0-beta9.jar|678861ba1b2e1fccb594bb0ca03114bb05da9695"
    )
elif [ "$E2E_LANE" = "mc1.12.2" ]; then
    # The Forge 1.12.2 universal jar's manifest Class-Path (verified live
    # 2026-08-12: this exact set boots the server to "Done" with NO vanilla
    # libs) — the 20 installer libraries minus the forge universal itself.
    LIB_SPECS=(
        "launchwrapper-1.12.jar|https://libraries.minecraft.net/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar|111e7bea9c968cdb3d06ef4632bf7ff0824d0f36"
        "asm-all-5.2.jar|https://maven.minecraftforge.net/org/ow2/asm/asm-all/5.2/asm-all-5.2.jar|2ea49e08b876bbd33e0a7ce75c8f371d29e1f10a"
        "jline-3.5.1.jar|https://maven.minecraftforge.net/org/jline/jline/3.5.1/jline-3.5.1.jar|51800e9d7a13608894a5a28eed0f5c7fa2f300fb"
        "jna-4.4.0.jar|https://libraries.minecraft.net/net/java/dev/jna/jna/4.4.0/jna-4.4.0.jar|cb208278274bf12ebdb56c61bd7407e6f774d65a"
        "akka-actor_2.11-2.3.3.jar|https://maven.minecraftforge.net/com/typesafe/akka/akka-actor_2.11/2.3.3/akka-actor_2.11-2.3.3.jar|ed62e9fc709ca0f2ff1a3220daa8b70a2870078e"
        "config-1.2.1.jar|https://maven.minecraftforge.net/com/typesafe/config/1.2.1/config-1.2.1.jar|f771f71fdae3df231bcd54d5ca2d57f0bf93f467"
        "scala-actors-migration_2.11-1.1.0.jar|https://maven.minecraftforge.net/org/scala-lang/scala-actors-migration_2.11/1.1.0/scala-actors-migration_2.11-1.1.0.jar|dfa8bc42b181d5b9f1a5dd147f8ae308b893eb6f"
        "scala-compiler-2.11.1.jar|https://maven.minecraftforge.net/org/scala-lang/scala-compiler/2.11.1/scala-compiler-2.11.1.jar|56ea2e6c025e0821f28d73ca271218b8dd04926a"
        "scala-continuations-library_2.11-1.0.2.jar|https://maven.minecraftforge.net/org/scala-lang/plugins/scala-continuations-library_2.11/1.0.2/scala-continuations-library_2.11-1.0.2.jar|0e517c53a7e9acd6b1668c5a35eccbaa3bab9aac"
        "scala-continuations-plugin_2.11.1-1.0.2.jar|https://maven.minecraftforge.net/org/scala-lang/plugins/scala-continuations-plugin_2.11.1/1.0.2/scala-continuations-plugin_2.11.1-1.0.2.jar|f361a3283452c57fa30c1ee69448995de23c60f7"
        "scala-library-2.11.1.jar|https://maven.minecraftforge.net/org/scala-lang/scala-library/2.11.1/scala-library-2.11.1.jar|0e11da23da3eabab9f4777b9220e60d44c1aab6a"
        "scala-parser-combinators_2.11-1.0.1.jar|https://maven.minecraftforge.net/org/scala-lang/scala-parser-combinators_2.11/1.0.1/scala-parser-combinators_2.11-1.0.1.jar|f05d7345bf5a58924f2837c6c1f4d73a938e1ff0"
        "scala-reflect-2.11.1.jar|https://maven.minecraftforge.net/org/scala-lang/scala-reflect/2.11.1/scala-reflect-2.11.1.jar|6580347e61cc7f8e802941e7fde40fa83b8badeb"
        "scala-swing_2.11-1.0.1.jar|https://maven.minecraftforge.net/org/scala-lang/scala-swing_2.11/1.0.1/scala-swing_2.11-1.0.1.jar|b1cdd92bd47b1e1837139c1c53020e86bb9112ae"
        "scala-xml_2.11-1.0.2.jar|https://maven.minecraftforge.net/org/scala-lang/scala-xml_2.11/1.0.2/scala-xml_2.11-1.0.2.jar|7a80ec00aec122fba7cd4e0d4cdd87ff7e4cb6d0"
        "lzma-0.0.1.jar|https://libraries.minecraft.net/lzma/lzma/0.0.1/lzma-0.0.1.jar|521616dc7487b42bef0e803bd2fa3faf668101d7"
        "jopt-simple-5.0.3.jar|https://libraries.minecraft.net/net/sf/jopt-simple/jopt-simple/5.0.3/jopt-simple-5.0.3.jar|cdd846cfc4e0f7eefafc02c0f5dce32b9303aa2a"
        "vecmath-1.5.2.jar|https://libraries.minecraft.net/java3d/vecmath/1.5.2/vecmath-1.5.2.jar|79846ba34cbd89e2422d74d53752f993dcc2ccaf"
        "trove4j-3.0.3.jar|https://libraries.minecraft.net/net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar|42ccaf4761f0dfdfa805c9e340d99a755907e2dd"
        "maven-artifact-3.5.3.jar|https://maven.minecraftforge.net/org/apache/maven/maven-artifact/3.5.3/maven-artifact-3.5.3.jar|7dc72b6d6d8a6dced3d294ed54c2cc3515ade9f4"
    )
else
    LIB_SPECS=(
        "launchwrapper-1.12.jar|https://libraries.minecraft.net/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar|111e7bea9c968cdb3d06ef4632bf7ff0824d0f36"
        "asm-all-5.0.3.jar|https://libraries.minecraft.net/org/ow2/asm/asm-all/5.0.3/asm-all-5.0.3.jar|4333508b8dd8ee72aa4e39afa713b3a74579b773"
        "jline-2.13.jar|https://maven.minecraftforge.net/jline/jline/2.13/jline-2.13.jar|2d9530d0a25daffaffda7c35037b046b627bb171"
        "akka-actor_2.11-2.3.3.jar|https://maven.minecraftforge.net/com/typesafe/akka/akka-actor_2.11/2.3.3/akka-actor_2.11-2.3.3.jar|ed62e9fc709ca0f2ff1a3220daa8b70a2870078e"
        "config-1.2.1.jar|https://maven.minecraftforge.net/com/typesafe/config/1.2.1/config-1.2.1.jar|f771f71fdae3df231bcd54d5ca2d57f0bf93f467"
        "scala-library-2.11.1.jar|https://maven.minecraftforge.net/org/scala-lang/scala-library/2.11.1/scala-library-2.11.1.jar|0e11da23da3eabab9f4777b9220e60d44c1aab6a"
        "lzma-0.0.1.jar|https://libraries.minecraft.net/lzma/lzma/0.0.1/lzma-0.0.1.jar|521616dc7487b42bef0e803bd2fa3faf668101d7"
        "trove4j-3.0.3.jar|https://libraries.minecraft.net/net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar|42ccaf4761f0dfdfa805c9e340d99a755907e2dd"
        "jopt-simple-4.6.jar|https://libraries.minecraft.net/net/sf/jopt-simple/jopt-simple/4.6/jopt-simple-4.6.jar|306816fb57cf94f108a43c95731b08934dcae15c"
        "guava-17.0.jar|https://libraries.minecraft.net/com/google/guava/guava/17.0/guava-17.0.jar|9c6ef172e8de35fd8d4d8783e4821e57cdef7445"
        "commons-lang3-3.3.2.jar|https://libraries.minecraft.net/org/apache/commons/commons-lang3/3.3.2/commons-lang3-3.3.2.jar|90a3822c38ec8c996e84c16a3477ef632cbc87a3"
        "commons-io-2.4.jar|https://libraries.minecraft.net/commons-io/commons-io/2.4/commons-io-2.4.jar|b1b6ea3b7e4aa4f492509a4952029cd8e48019ad"
        "commons-codec-1.9.jar|https://libraries.minecraft.net/commons-codec/commons-codec/1.9/commons-codec-1.9.jar|9ce04e34240f674bc72680f8b843b1457383161a"
        "gson-2.2.4.jar|https://libraries.minecraft.net/com/google/code/gson/gson/2.2.4/gson-2.2.4.jar|a60a5e993c98c864010053cb901b7eab25306568"
        "authlib-1.5.22.jar|https://libraries.minecraft.net/com/mojang/authlib/1.5.22/authlib-1.5.22.jar|afaa8f6df976fcb5520e76ef1d5798c9e6b5c0b2"
        "netty-all-4.0.23.Final.jar|https://libraries.minecraft.net/io/netty/netty-all/4.0.23.Final/netty-all-4.0.23.Final.jar|0294104aaf1781d6a56a07d561e792c5d0c95f45"
        "log4j-api-2.0-beta9.jar|https://libraries.minecraft.net/org/apache/logging/log4j/log4j-api/2.0-beta9/log4j-api-2.0-beta9.jar|1dd66e68cccd907880229f9e2de1314bd13ff785"
        "log4j-core-2.0-beta9.jar|https://libraries.minecraft.net/org/apache/logging/log4j/log4j-core/2.0-beta9/log4j-core-2.0-beta9.jar|678861ba1b2e1fccb594bb0ca03114bb05da9695"
    )
fi

# ---------------------------------------------------------------------------
# Artifact fetch (server jar + forge universal + libs) into the e2e cache
# ---------------------------------------------------------------------------
SERVER_JAR="$CACHE/minecraft_server-$E2E_LANE.jar"
if [ ! -f "$SERVER_JAR" ]; then
    curl -fsSL --retry 3 -o "$SERVER_JAR" \
        "https://launcher.mojang.com/v1/objects/$SERVER_SHA1/server.jar"
fi
echo "$SERVER_SHA1  $SERVER_JAR" | sha1sum -c - >/dev/null 2>&1 || e2e_fail "server jar sha1 mismatch ($E2E_LANE)"

FORGE_JAR="$CACHE/forge-$FORGE_VERSION.jar"
if [ ! -f "$FORGE_JAR" ]; then
    curl -fsSL --retry 3 -o "$FORGE_JAR" \
        "https://maven.minecraftforge.net/net/minecraftforge/forge/$FORGE_VERSION/forge-$FORGE_VERSION-universal.jar"
fi
echo "$FORGE_SHA1  $FORGE_JAR" | sha1sum -c - >/dev/null 2>&1 || e2e_fail "forge universal sha1 mismatch ($E2E_LANE)"

for spec in "${LIB_SPECS[@]}"; do
    name="${spec%%|*}"
    rest="${spec#*|}"
    url="${rest%%|*}"
    sha1="${rest##*|}"
    fetch_artifact "$name" "$url" "$sha1"
done

# Bridge lanes additionally need the (spike-validated, stripped) hmc-specifics
# jar: seeded per-lane in test-infrastructure/hmc-specifics/ (the lexforge
# specifics have no live upstream URL; sha1-pinned vendored copies). The
# mc1.12.2 lane dir is mc1.12.2/ (no forge- prefix — the out-of-band legacy
# layout).
if [ -n "$HMC_SPECIFICS_NAME" ]; then
    LANE_PREFIX="forge-"
    [ "$E2E_LANE" = "mc1.12.2" ] && LANE_PREFIX=""
    : "${E2E_HMC_SPECIFICS_JAR:=$REPO_ROOT/$LANE_PREFIX$E2E_LANE/test-infrastructure/hmc-specifics/$HMC_SPECIFICS_NAME}"
    if [ ! -f "$E2E_HMC_SPECIFICS_JAR" ]; then
        e2e_fail "hmc-specifics jar missing for lane $E2E_LANE: $E2E_HMC_SPECIFICS_JAR (seed from the slice-2 spike artifacts)"
    fi
fi

# HeadlessMC launcher wrapper (2.10.0, sha1-pinned; also used by the
# mc1.12.2 E2E harness — same upstream release).
HMC_WRAPPER_SHA1="c185d0cf59dd26fc5d5999c8580620fbfe64c795"
HMC_WRAPPER_JAR="$CACHE/headlessmc-launcher-wrapper.jar"
if [ ! -f "$HMC_WRAPPER_JAR" ]; then
    curl -fsSL --retry 3 -o "$HMC_WRAPPER_JAR" \
        "https://github.com/headlesshq/headlessmc/releases/download/$E2E_HMC_VERSION/headlessmc-launcher-wrapper-$E2E_HMC_VERSION.jar"
fi
echo "$HMC_WRAPPER_SHA1  $HMC_WRAPPER_JAR" | sha1sum -c - >/dev/null 2>&1 || e2e_fail "headlessmc wrapper sha1 mismatch"

# ---------------------------------------------------------------------------
# Server dir + boot (fresh server per run — a reused server drops chat
# packets after the first client's abnormal disconnect, observed in the
# 1.10.2 trials).
# ---------------------------------------------------------------------------
e2e_log "setting up server dir ($E2E_LANE)..."
rm -rf "$SERVER_DIR"
mkdir -p "$SERVER_DIR/mods" "$SERVER_DIR/logs"
cp "$E2E_MOD_JAR" "$SERVER_DIR/mods/"
echo "eula=true" > "$SERVER_DIR/eula.txt"
cat > "$SERVER_DIR/server.properties" <<EOF
online-mode=false
server-port=$E2E_SERVER_PORT
level-name=world
gamemode=0
motd=EverlastingSkins E2E $E2E_LANE
max-tick-time=-1
EOF
printf '%s\n' "$E2E_USERNAME" > "$SERVER_DIR/ops.txt"
if [ "$E2E_LANE" = "mc1.12.2" ]; then
    # 1.12.2 reads ops.json (JSON, not the pre-1.7.6 ops.txt); the offline
    # UUID is UUID.nameUUIDFromBytes("OfflinePlayer:" + name). The vanilla
    # service defaults op_level.mojang to 0 (everyone), so ops are
    # belt-and-suspenders — level 4 keeps every /skin node open regardless
    # of config drift.
    E2E_OFFLINE_UUID="$(python3 -c "import hashlib,sys;h=bytearray(hashlib.md5(('OfflinePlayer:'+sys.argv[1]).encode()).digest());h[6]=(h[6]&0x0f)|0x30;h[8]=(h[8]&0x3f)|0x80;b=bytes(h);print('%08x-%04x-%04x-%04x-%012x'%(int.from_bytes(b[0:4],'big'),int.from_bytes(b[4:6],'big'),int.from_bytes(b[6:8],'big'),int.from_bytes(b[8:10],'big'),int.from_bytes(b[10:16],'big')))" "$E2E_USERNAME")"
    cat > "$SERVER_DIR/ops.json" <<EOF
[{"uuid":"$E2E_OFFLINE_UUID","name":"$E2E_USERNAME","level":4,"bypassesPlayerLimit":false}]
EOF
fi

SERVER_CP="$SERVER_JAR:$FORGE_JAR"
for name in "${LIB_SPECS[@]}"; do
    SERVER_CP="$SERVER_CP:$CACHE/${name%%|*}"
done

SERVER_PID=""
cleanup() {
    if [ -n "$SERVER_PID" ]; then
        kill_tree "$SERVER_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

e2e_log "booting server (own session)..."
(
    cd "$SERVER_DIR"
    exec setsid "$E2E_JAVA8" -Xmx1G -Xms512M \
        -Deverlastingskins.e2e=true \
        -cp "$SERVER_CP" "$SERVER_MAIN" nogui
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
# Client: gameDir + hmc config + scenario, then launch
# ---------------------------------------------------------------------------
e2e_log "setting up client dir + hmc config..."
rm -rf "$CLIENT_DIR" "$HMC_DIR"
mkdir -p "$CLIENT_DIR/mods" "$HMC_DIR/HeadlessMC"
cp "$E2E_MOD_JAR" "$CLIENT_DIR/mods/"
if [ -n "$HMC_SPECIFICS_NAME" ]; then
    cp "$E2E_HMC_SPECIFICS_JAR" "$CLIENT_DIR/mods/$HMC_SPECIFICS_NAME"
fi

# Scenario: bridge lanes drive /skin + the bridge ack through the
# console; the 1.10.2 in-jar driver emits its own markers. NOTE (no quit
# SEND on bridge lanes): the server-side skin action (a blocking Mojang
# profile fetch on the server thread) must COMPLETE before the client
# disconnects — a mid-command disconnect aborts the completion path and
# the ES_E2E_SKIN=ok sentinel never logs (observed on main's CI 2026-08-11
# and reproduced locally: client quit ~1s after the /skin SEND, sentinel
# never appeared despite a fast Mojang API). The steps therefore end in the
# implicit WAIT_FOR_END (test timeout), keeping the client in-world until
# the driver sees the sentinel and kills it.
SCENARIO_FILE="$RUNNER_TMP/scenario-$E2E_LANE.json"
if [ -n "$HMC_SPECIFICS_NAME" ]; then
    # Readiness gate (S5 fix, deep-dive of run 31567935416): the 1.7.10 /
    # 1.8.9 lanes gate on the sound-init marker minecraft:music.game (their
    # legacy sound systems emit no per-event WARNs, so it only appears at
    # world-load). The 1.12.2 dummy-asset client emits "[WARN] Missing
    # sound for event: minecraft:music.game" during world-load sound-init
    # ~6s BEFORE the FML handshake completes — that gate fires in the
    # PRE-PLAY phase there, the /skin SEND is lost, and the server's read
    # timeout drops the client. The 1.12.2 lane therefore gates on the
    # client-side join chat line instead (GuiNewChat logs "[CHAT] <text>"
    # on 1.8-1.12.2 clients; the bare text keeps the hmc CONTAINS match
    # regex-safe — no brackets).
    if [ "$E2E_LANE" = "mc1.12.2" ]; then
        SCENARIO_READY_MARKER="$E2E_USERNAME joined the game"
    else
        SCENARIO_READY_MARKER="minecraft:music.game"
    fi
    cat > "$SCENARIO_FILE" <<EOF
{
  "name": "$E2E_LANE bridge E2E",
  "steps": [
    {"type": "CONTAINS", "message": "$SCENARIO_READY_MARKER", "timeout": 240},
    {"type": "SEND", "message": "$SCENARIO_SKIN_CMD"},
    {"type": "SEND", "message": ". hmc-e2e-bridge-ok"}
  ],
  "timeout": 300
}
EOF
else
    cat > "$SCENARIO_FILE" <<'EOF'
{
  "name": "1.10.2 in-jar driver E2E",
  "steps": [
    {"type": "CONTAINS", "message": "ES_E2E_DRIVER=installed", "timeout": 180},
    {"type": "CONTAINS", "message": "ES_E2E_RESULT=", "timeout": 120}
  ],
  "timeout": 300
}
EOF
fi

HMC_JVMARGS=""
[ "$E2E_LANE" = "1.10.2" ] && HMC_JVMARGS="hmc.jvmargs=-Deverlastingskins.e2e=true"
: "${HMC_LAUNCH_EXTRA:=}"
# The launcher reads its config from <cwd>/HeadlessMC/config.properties
# (hmc's default config location), so the config lives in a HeadlessMC/
# subdir of the hmc runtime dir and the launcher runs with CWD there.
cat > "$HMC_DIR/HeadlessMC/config.properties" <<EOF
hmc.java.versions=$E2E_JAVA8
hmc.gamedir=$CLIENT_DIR
hmc.mcdir=$HOME/.minecraft
hmc.offline=true
hmc.offline.username=$E2E_USERNAME
hmc.rethrow.launch.exceptions=true
hmc.exit.on.failed.command=true
hmc.assets.dummy=true
hmc.auto.download.specifics=false
hmc.jline.enabled=false
$HMC_JVMARGS
hmc.gameargs=--server=127.0.0.1
hmc.test.filename=$SCENARIO_FILE
EOF

# The launcher reads hmc.mcdir from the config; a fresh CI runner has no
# ~/.minecraft client profile for this forge version, and the launcher then
# fails with "Couldn't find object for name" on launch. Bridge lanes install
# the profile through the launcher's own forge command (verified live: creates
# exactly $MC_VERSION_ID under hmc.mcdir/versions/). mc1.12.2 cannot use that
# route (HMCLite's forgecli crashes on 1.12.2 installers with a
# NoSuchMethodError on ClientInstall.run, and the canonical 2847 installer
# has no --installClient CLI), so its profile is installed with the 2860
# rebuild's --installClient (same proven route as the pre-#438 boot-smoke).
MC_DIR="${HOME}/.minecraft"
if [ ! -d "$MC_DIR/versions/$MC_VERSION_ID" ]; then
    if [ "$E2E_LANE" = "mc1.12.2" ]; then
        e2e_log "installing forge client profile $MC_VERSION_ID (2860 installer)..."
        CLIENT_INSTALLER="forge-1.12.2-14.23.5.2860-installer.jar"
        fetch_artifact "$CLIENT_INSTALLER" \
            "https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2860/forge-1.12.2-14.23.5.2860-installer.jar" \
            "5e8a91f71ef3d1f77de3f8d3261aedcc2f551c9d"
        set +e
        mkdir -p "$MC_DIR"
        echo '{"profiles":{}}' > "$MC_DIR/launcher_profiles.json"
        ( cd "$SERVER_DIR" && "$E2E_JAVA8" -jar "$CACHE/$CLIENT_INSTALLER" --installClient "$MC_DIR" ) \
            > "$RUNNER_TMP/forge-install-$E2E_LANE.log" 2>&1
        INSTALL_CODE=$?
        set -e
    else
        e2e_log "installing forge client profile $MC_VERSION_ID..."
        set +e
        ( cd "$HMC_DIR" && setsid timeout 300 "$E2E_JAVA8" -jar "$HMC_WRAPPER_JAR" \
            --command "forge $MC_VERSION --uid $FORGE_UID" ) > "$RUNNER_TMP/forge-install-$E2E_LANE.log" 2>&1
        INSTALL_CODE=$?
        set -e
    fi
    if [ $INSTALL_CODE -ne 0 ] || [ ! -d "$MC_DIR/versions/$MC_VERSION_ID" ]; then
        e2e_warn "forge client profile install failed (code $INSTALL_CODE)"
        tail -n 20 "$RUNNER_TMP/forge-install-$E2E_LANE.log" >&2 || true
        exit 2
    fi
fi

# The launcher resolves hmc.test.filename etc. relative to the HeadlessMC
# dir it runs from; run it with CWD = the config dir.
CLIENT_LOG="$RUNNER_TMP/client-$E2E_LANE.log"
e2e_log "launching HeadlessMC client ($MC_VERSION_ID)..."
set +e
(
    cd "$HMC_DIR"
    if [ -n "${DISPLAY:-}" ]; then
        exec setsid timeout --kill-after=10 "$E2E_CLIENT_TIMEOUT_S" \
            "$E2E_JAVA8" -jar "$HMC_WRAPPER_JAR" \
            --command "launch $MC_VERSION_ID -lwjgl -offline $HMC_LAUNCH_EXTRA"
    else
        exec setsid timeout --kill-after=10 "$E2E_CLIENT_TIMEOUT_S" \
            xvfb-run -a "$E2E_JAVA8" -jar "$HMC_WRAPPER_JAR" \
            --command "launch $MC_VERSION_ID -lwjgl -offline $HMC_LAUNCH_EXTRA"
    fi
) > "$CLIENT_LOG" 2>&1 &
CLIENT_PID=$!
set -e

# ---------------------------------------------------------------------------
# Join gate (S5): a HARD wait for the server-side in-world join line before
# any assertion is meaningful. The 1.12.2 dummy-asset client's sound-init
# WARNs (incl. minecraft:music.game) fire in the PRE-PLAY phase, so a
# client "ready" signal from the launcher can race the FML handshake — the
# server's "$E2E_USERNAME joined the game" line is ground truth that the
# player is in-world (vanilla PlayerList broadcast; also asserted for the
# modern-smoke era). Timeout = retryable infra (exit 2).
# ---------------------------------------------------------------------------
JOINED=0
for _ in $(seq 1 "$E2E_JOIN_TIMEOUT_S"); do
    if grep -q "$E2E_USERNAME joined the game" "$SERVER_LOG" 2>/dev/null; then
        JOINED=1
        break
    fi
    if ! kill -0 "$CLIENT_PID" 2>/dev/null; then
        break
    fi
    sleep 1
done

if [ "$JOINED" -ne 1 ]; then
    e2e_warn "client join NOT seen within ${E2E_JOIN_TIMEOUT_S}s (server log tail follows)"
    tail -n 25 "$SERVER_LOG" >&2 || true
    e2e_warn "client log tail follows"
    tail -n 25 "$CLIENT_LOG" >&2 || true
    kill_tree "$CLIENT_PID" 2>/dev/null || true
    CLIENT_PID=""
    kill_tree "$SERVER_PID" 2>/dev/null || true
    SERVER_PID=""
    exit 2
fi
e2e_log "client joined (server log: $E2E_USERNAME joined the game)"

# ---------------------------------------------------------------------------
# Assertions: server-side sentinel is primary; the launcher's CommandTest
# outcome is secondary (the driver header's contract). The bridge scenario
# has no quit SEND, so the client stays in-world until the server-side skin
# action completes and the sentinel logs; the driver then kills the client
# (WAIT_FOR_END ends with the process). If the client dies early for any
# reason, the sentinel can no longer appear (mid-command disconnect aborts
# the completion path), so the poll just reports the miss.
# ---------------------------------------------------------------------------
SENTINEL_SEEN=0
for i in $(seq 1 90); do
    if grep -q "ES_E2E_SKIN=ok player=$E2E_USERNAME" "$SERVER_LOG" 2>/dev/null; then
        SENTINEL_SEEN=1
        break
    fi
    if ! kill -0 "$CLIENT_PID" 2>/dev/null; then
        break
    fi
    sleep 2
done

kill_tree "$SERVER_PID" 2>/dev/null || true
SERVER_PID=""

if [ "$SENTINEL_SEEN" -eq 1 ]; then
    e2e_log "server sentinel seen (ES_E2E_SKIN=ok)"
    if [ "$E2E_LANE" = "1.10.2" ]; then
        # Grace window for the client-side outcome: the 1.10.2 in-jar driver
        # writes e2e-result.json ~20s after join. Poll while it is alive.
        for _ in $(seq 1 30); do
            if [ -f "$CLIENT_DIR/e2e-result.json" ]; then
                break
            fi
            if ! kill -0 "$CLIENT_PID" 2>/dev/null; then
                break
            fi
            sleep 2
        done
    fi
    # Bridge lanes: the scenario ended in WAIT_FOR_END (no quit SEND); the
    # sentinel is the authoritative outcome, so end the client here. The
    # launcher may or may not print "CommandTest was successful" before the
    # group kill lands — the driver outcome is derived from the sentinel.
    kill_tree "$CLIENT_PID" 2>/dev/null || true
else
    e2e_warn "server sentinel NOT seen (server log tail follows)"
    tail -n 20 "$SERVER_LOG" >&2 || true
    e2e_warn "client log tail follows"
    tail -n 20 "$CLIENT_LOG" >&2 || true
    kill_tree "$CLIENT_PID" 2>/dev/null || true
fi

# ---------------------------------------------------------------------------
# Result document (master-plan contract) + exit mapping
# ---------------------------------------------------------------------------
CLIENT_JOINED="False"
COMMAND_EXECUTED="False"
COMMAND_REACHED_SERVER="False"
DRIVER_CODE=3
if [ "$E2E_LANE" = "1.10.2" ]; then
    if [ -f "$CLIENT_DIR/e2e-result.json" ]; then
        CLIENT_JOINED=$(python3 -c "import json; print(str(json.load(open('$CLIENT_DIR/e2e-result.json')).get('client_joined', False)).title())")
        COMMAND_EXECUTED=$(python3 -c "import json; print(str(json.load(open('$CLIENT_DIR/e2e-result.json')).get('command_executed', False)).title())")
        DRIVER_CODE=$(python3 -c "import json; print(int(json.load(open('$CLIENT_DIR/e2e-result.json')).get('exit_code', 3)))")
    fi
else
    # Bridge lanes: with the no-quit scenario the client is killed by the
    # driver after the sentinel, so "CommandTest was successful" is not a
    # reliable signal (the group kill races the launcher's final print).
    # Derive the contract fields from the SERVER log instead: the join line,
    # the bridge-ack chat, and the sentinel (primary).
    if grep -q "$E2E_USERNAME joined the game" "$SERVER_LOG" 2>/dev/null; then
        CLIENT_JOINED="True"
    fi
    if grep -q "<$E2E_USERNAME> hmc-e2e-bridge-ok" "$SERVER_LOG" 2>/dev/null; then
        COMMAND_EXECUTED="True"
    fi
    # S5 contract: the SkinCommand entry marker (logged at the top of
    # execute() under -Deverlastingskins.e2e=true) proves the /skin SEND
    # REACHED the server. Present on 1.7.10 + mc1.12.2 (the 1.8.9 lane has
    # no command marker, so its field stays False and is not asserted).
    if [ "$E2E_LANE" = "1.7.10" ] || [ "$E2E_LANE" = "mc1.12.2" ]; then
        if grep -q "ES_E2E_SKIN=cmd" "$SERVER_LOG" 2>/dev/null; then
            COMMAND_REACHED_SERVER="True"
        fi
    fi
    if [ "$SENTINEL_SEEN" -eq 1 ] || grep -q "CommandTest was successful" "$CLIENT_LOG" 2>/dev/null; then
        DRIVER_CODE=0
    fi
fi

if [ "$SENTINEL_SEEN" -eq 1 ] && [ "$DRIVER_CODE" -eq 0 ]; then
    FINAL_CODE=0
else
    FINAL_CODE=1
fi
# S5 regression-prevention: the client JOINED (join gate passed) but the
# /skin SEND never reached the server — the pre-join-race signature (the
# scenario fired before the in-world join). Report it as a DISTINCT
# assertion failure instead of a silent sentinel miss so the failure mode
# is explicit in the job log.
if [ "$E2E_LANE" = "1.7.10" ] || [ "$E2E_LANE" = "mc1.12.2" ]; then
    if [ "$COMMAND_REACHED_SERVER" != "True" ]; then
        e2e_warn "FAIL: command never reached the server (ES_E2E_SKIN=cmd absent) — pre-join-race signature: client joined but the /skin SEND was lost"
        FINAL_CODE=1
    fi
fi

python3 - "$RESULT_JSON" <<PY
import json, sys
out = {
    "lane": "$E2E_LANE",
    "era": "headlessmc",
    "server_booted": True,
    "client_joined": $CLIENT_JOINED,
    "command_executed": $COMMAND_EXECUTED,
    "command_reached_server": $COMMAND_REACHED_SERVER,
    "renderer_state": "headless",
    "renderer_verified": True,
    "server_sentinel": $([ "$SENTINEL_SEEN" -eq 1 ] && echo True || echo False),
    "exit_code": $FINAL_CODE,
    "artifacts": {
        "driver": "headlessmc.sh/$E2E_LANE",
        "client_log": "$CLIENT_LOG",
        "server_log": "$SERVER_LOG",
    },
}
json.dump(out, open(sys.argv[1], "w"), indent=2)
PY
e2e_log "final result: $RESULT_JSON"

if [ "$FINAL_CODE" -eq 0 ]; then
    e2e_log "PASS: HeadlessMC E2E ($E2E_LANE) all green"
    exit 0
fi
e2e_warn "FAIL: HeadlessMC E2E ($E2E_LANE) sentinel=$SENTINEL_SEEN driver=$DRIVER_CODE reached_server=$COMMAND_REACHED_SERVER"
exit 1
