# EverlastingSkins — forge-1.5.2 lane

Out-of-band legacy lane: Minecraft 1.5.2 / FML 5.2 (`cpw.mods.fml`, `@Mod`/`@Mod.EventHandler`) / pre-UUID (no GameProfile). Builds standalone with its own Gradle 4.4.1 wrapper on Java 8 via the vendored SpecialSource remap harness (no ForgeGradle); consumes `:common` by source-dir share. See `build.gradle` for the full toolchain rationale.

Build: `cd forge-1.5.2 && JAVA_HOME=<jdk8> ./gradlew build`

## Era limitations (audit-documented, 2026-08)

These are intentional hardcodings for this lane; the full Config/i18n/metrics backport is a later batch.

- **Web skins unsupported.** There is no MineSkin/URL pipeline pre-GameProfile: `/skin set web` is honestly rejected with `WEB_UNSUPPORTED` ("web skins are not supported on this version"). A URL allowlist gate is therefore not applicable — no URL ever reaches an HTTP skin API.
- **English-only player messages.** No i18n system; every chat message is a hardcoded English string.
- **No config surface.** No `Config` file; permission levels are hardcoded in `SkinRestorerCommand` (op-level gating, no config command).
- **No network-latency metrics.** In-process skin metrics exist, but no API-latency tracking (MineSkin/Mojang round-trip times).
- **No test tree.** This lane has no `src/test`; changes are verified by build only.

Command surface: the single `/skin` command (aliases `skins`, `setskin`) with `set <name|random>` (web rejected), `clear`, `source`, `metrics`.
