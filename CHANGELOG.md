# Changelog

## 2.1.0-rc.1 (2026-08-02)

### Added
- **MojangProfileCache config** (#143): `mojang_profile_cache_enabled/ttl_ms/max_size` — admins can tune cache lifetime and size to protect against Mojang rate limits (default 1h / 1000).
- **Custom messages tree** (#144): 22 new message keys (e.g., `messages_change`, `messages_fulfilled`, `messages_timeout`, `messages_no_skin_found`, `messages_mineskin_rejected`) with per-server config defaults and per-locale overrides via existing I18nUtils.
- **Default skins list** (#145): Configurable list of default skins with `<random>` token — random picks thread through `RandomMojangSkin.randomUsername()` then `MojangAPI.getSkin`. New `applyForPremium` flag controls whether defaults override premium players' saved custom skins.
- **URL domain allowlist** (#146): `Security.urlAllowlistEnabled` + `urlAllowlistDomains` (default list of 9 common skin hosts: imgur, storage.googleapis.com, cdn.discordapp.com, textures.minecraft.net, namemc.com, crafatar, mc-heads.net, githubusercontent, minecraftskins). eTLD+1 suffix match post-`sanitizeImageURL` to handle the namemc.com rewrite.
- **Discord announce i18n** (#150): DiscordSRV announce message routed through `I18nUtils.get("discord_announce", ...)`. New `messages_discord_announce` config key.
- **Per-command op level config** (#152): 7 `op_level.{mojang,url,clear,random,other,metrics,metrics_reset}` keys (default mojang/clear/random=0, url/other/metrics/metrics_reset=2).
- **`everlastingskins.bypass.cooldown` permission node** (#152): OP default; skips the rate-limit cooldown window for holders.
- **`everlastingskins.command.skin.source` permission node** (#152): registered as a real node (was hardcoded `true`); retains ALL default for read-only self-source check.
- **PermissionContext widened** (#152): from `(UUID, boolean isOp)` to `(UUID, int opLevel)` with 0-4 validation. VanillaPermissionService reads per-node op level from Config; LuckPerms/Forge paths delegate fallback to vanilla.
- **i18n infrastructure overhaul** (#161): translations moved from in-code maps to JSON resource files (`src/main/resources/assets/everlastingskins/lang/<locale>.json`, 11 locales); `defaultLocaleFor()` normalizes MC locale codes (`en_us` -> `en`); per-player locale via `ServerPlayer.clientInformation().language()` with `Config.LANGUAGE` fallback, threaded through all skin command feedback paths; fixed cross-file key leak in locale loading.

### Changed
- Default permission posture: `/skin set mojang/clear/random` are now open to all players (op_level=0); `/skin set web` and `/skin set/clear <other>` still require op-2; `/skin metrics` and `/skin metrics reset` still require op-2.

### Fixed
- Login-time 3-provider HTTP chain offloaded to executor (no more 30s server freeze per login)
- Multi-target `/skin clear` per-target restores from Mojang
- `SkinRefreshHandler.task()` wrapped in try/catch (no partial cascade on exception)
- `ForgePermissionService` properly registers all 8 permission nodes (was 6; added `.source` + `.bypass.cooldown`)
- LuckPerms backend falls back to op check on pre-load (was: unconditional deny)
- MineSkin 429 bounded sleep (was: unbounded provider-controlled delay)
- CustomSkinProperty base64 validation
- GameTest flake on `concurrentSkinSet_twoPlayers` — wall-clock deadline (20s) instead of tick budget
- **i18n regression** (#168): restored `Messages` config override in I18nUtils (locale files no longer shadow per-server overrides) + locale code normalization for `defaultLocaleFor()`
- Dead lang key `fulfilled_force` removed from all 11 JSON locale files (#176)

### Tests added
- `MojangProfileCacheTest` (8 cases)
- `UrlAllowlistTest` (8 cases)
- `DefaultSkinResolverTest` (11 cases)
- `PermissionServiceManagerTest` + `VanillaPermissionServiceTest` + `LuckPermsPermissionServiceTest` (refreshed with opLevel)
- **Test gaps closed** (#169): Discord I18n routing tests + Mojang cache config tests + per-player locale tests (11 tests)

### Docs
- README — fix Forge versions (51.0.8 / 14.23.5.2847), add languages section + message key CHANGELOG link (#163)
- CHANGELOG/AGENTS/TESTING cleanup (#165)

## 2.1.0 (2026-08-02)

### Added
- Behavioral fixes from lib-17 audit (cherry-picked from mc1.12.2 port)
- EntityTracker refresh step (restores observer-cache fix that PR #121 accidentally dropped)
- MojangProfileCache metrics wiring (hit/miss counters)
- Dead metric counter wiring (rateLimited/debounced/skipped/skippedStored)
- Base64 validation for CustomSkinProperty
- Login-time async HTTP offload (no more 30s server freeze per login)
- ForgePermissionService uses PermissionAPI.hasPermission (not just op-check)
- LuckPerms pre-load fallback to op check
- MineSkin 429 bounded sleep (was unbounded)
- /skin metrics command (PR #126)
- SPDX MIT license headers on all 108 .java files

### Changed
- Multi-target /skin clear now per-target restores from Mojang
- SkinRefreshHandler.task() wrapped in try/catch (was: partial cascade on exception)

### Fixed
- GameTest --add-opens JVM flags for JDK 21/Netty reflective access

### Removed
- UPDATE_DISPLAY_NAME from refresh cascade (carried zero textures on wire)

## 2.0.0 (2026-08-02)

### Added
- REMOVE+ADD_PLAYER observer fix (replaces UPDATE_DISPLAY_NAME that carried zero textures)
- 6 ranks of improvements (skip-if-unchanged, async IO coalesce, dimension-scoped broadcast, rate limit, debounce, redundancy removal)
- Network metrics instrumentation via ChannelDuplexHandler
- MojangProfileCache (TTL 1h, cap 1000)
- /skin metrics command tree (human/json/players/cleanup/reset)
- saveSkinAsync drain-coalesce writer (50ms debounce, race-safe latch)
- 30 integration tests (GameTest) + wire-level packet assertions

### Changed
- SkinRefreshHandler refactored (split into SkinRefreshHandler + SkinActionCommand + SkinMetricsCommand)
- Permission semantics: 4 permission backends (Vanilla, Forge, LuckPerms, auto-detection)
- Config refactored: Config.java split + ForgeConfigSpec

### Fixed
- Observer-cache bug: REMOVE+ADD_PLAYER ensures observers see skin updates

### Removed
- UPDATE_DISPLAY_NAME from refresh cascade
