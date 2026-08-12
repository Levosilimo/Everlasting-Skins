# EverlastingSkins — forge-1.7.10 lane

Out-of-band legacy lane: Minecraft 1.7.10 / Forge 10.13.4.1614 / LaunchWrapper `@Mod`/`@Mod.EventHandler` (`cpw.mods.fml`) / first UUID-capable line (`EntityPlayer.getGameProfile()`). Builds standalone with its own Gradle 4.4.1 wrapper on Java 8, GTNH ForgeGradle 1.2.11 (jitpack-pinned) + MCP stable_12; consumes `:common` by source-dir share. See `build.gradle` for the supply-chain pin rationale.

Build: `cd forge-1.7.10 && JAVA_HOME=<jdk8> ./gradlew build`

## Era limitations (audit-documented, 2026-08)

These are intentional hardcodings for this lane; the full Config/i18n/metrics backport is a later batch.

- **No config surface.** No `Config` file; permission levels are hardcoded (permission nodes `everlastingskins.command.*` with fixed required op levels — self-service mojang/random/clear at op 0, web/other/metrics at op 2, mirroring the mc1.12.2 Config defaults). No config command.
- **English-only player messages.** No i18n system; every chat message is a hardcoded English string.
- **No network-latency metrics.** In-process skin metrics exist, but no API-latency tracking (MineSkin/Mojang round-trip times).
- **Web skins supported with the allowlist always ON.** Unlike the pre-1.6.4 lanes, `/skin set web` works via MineSkin — and because there is no Config surface to disable it, the URL domain allowlist is hard-wired on with the default domain list (mirroring the 1.21 Config defaults; see `SkinCommand.ALLOWLIST_DOMAINS`).

Command surface: `/skin` (aliases `skins`, `setskin`) with `set mojang|web|random`, `clear`, `source`, `metrics` — parity with the 1.21 reference.
