# EverlastingSkins — forge-1.10.2 lane

Out-of-band legacy lane: Minecraft 1.10.2 / Forge 12.18.3.2511 / LaunchWrapper `@Mod`/`@Mod.EventHandler` (`cpw.mods.fml`) / MCP stable_29. Builds standalone with its own Gradle 4.10.3 wrapper on Java 8 and ForgeGradle 2.2.5; consumes `:common` by source-dir share.

Build: `cd forge-1.10.2 && JAVA_HOME=<jdk8> ./gradlew build`

## Era limitations (audit-documented, 2026-08)

These are intentional hardcodings for this lane; the full Config/i18n/metrics backport is a later batch.

- **No config surface.** No `Config` file; permission levels are hardcoded (permission nodes `everlastingskins.command.*` with fixed required op levels — self-service mojang/random/clear at op 0, web/other/metrics at op 2, mirroring the mc1.12.2 Config defaults). No config command.
- **English-only player messages.** No i18n system; every chat message is a hardcoded English string.
- **No network-latency metrics.** In-process skin metrics exist, but no API-latency tracking (MineSkin/Mojang round-trip times).
- **Web skins supported with the allowlist always ON.** `/skin set web` works via MineSkin — and because there is no Config surface to disable it, the URL domain allowlist is hard-wired on with the default domain list (mirroring the 1.21 Config defaults; see `SkinCommand.ALLOWLIST_DOMAINS`).

## Lane extra: `/everlastingskins` admin command

This lane registers an additional `/everlastingskins <status|reload|help>` admin command (`SkinRestorerCommand`) that exists in **no other lane** (except its sibling forge-1.8.9) and is absent from the 1.21 reference. Kept deliberately for its admin value (storage status, config-less reload, usage); it is a documented lane extra, not a parity gap.

Command surface: `/skin` with `set mojang|web|random`, `clear`, `source`, `metrics` — parity with the 1.21 reference.
