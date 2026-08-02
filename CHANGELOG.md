# Changelog

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
