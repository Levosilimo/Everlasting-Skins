# Changelog

## 2.1.0-rc.1 (2026-08-02)

### Added
- **Default skins list** (#147): Configurable list with `<random>` token + `applyForPremium` flag.
- **URL domain allowlist** (#148): Same as 1.21 — 9 default domains, eTLD+1 suffix match.
- **Per-player locale via Access Transformer** (#149): Forge AT exposes `EntityPlayerMP.field_71148_cg # language` (no Mixin, no extra runtime deps). New `I18nUtils.getLocalizedString(key, EntityPlayerMP)` overload with `Config.LANGUAGE` fallback. AT file `everlastingskins_at.cfg` + `FMLAT` manifest attribute.
- **Discord announce i18n** (#151): Routed through `I18nUtils.getLocalizedString("discord_announce", Config.LANGUAGE)` with `assets/everlastingskins/lang/{en,ru,uk}.properties` resource files.
- **Per-command op level config** (#153): 7 `op_level.*` keys (same defaults as 1.21).
- **`everlastingskins.bypass.cooldown` permission node** (#153): `DefaultPermissionLevel.OP`. 8 total nodes registered (added `.source` + `bypass.cooldown`).
- **PermissionContext widened** (#153): Java 8-compatible class with opLevel 0-4 validation.
- **MojangProfileCache config wired** (#160): `mojangProfileCache*` keys now read from `Config.load()` (were compiled-in defaults).
- **i18n infrastructure overhaul** (#162): 22 message keys with per-server config defaults + per-locale overrides via I18nUtils (en/ru/uk + 8 more resource files, 11 locales total); per-player locale via `PlayerLanguage`.
- **Resource lang files**: `src/main/resources/assets/everlastingskins/lang/{en,ru,uk}.properties` shipped with the mod (not just runtime-writeable config dir).

### Fixed
- All lib-17 + lib-49 fixes ported from 1.21.
- **Translation drift** (#167): `ru`/`uk` properties realigned with the 1.21 canonical translations.
- **ObserverPacketIT race** (#177): await respawn cascade before ordering assertion (line 92).
- Dead lang key `fulfilled_force` removed from mc1.12.2 lang properties (#184).

### Tests added
- `UrlAllowlistTest` (8 cases)
- `DefaultSkinResolverTest` (11 cases)
- `PlayerLanguageTest` (3 cases)
- `PermissionServiceManagerTest` + `VanillaPermissionServiceTest` + `LuckPermsPermissionServiceTest` (refreshed)
- `PermissionGateIT` (integration test)
- **Test gaps closed** (#170): Discord I18n routing tests + `ConfigTest` defaults tests + per-player locale tests (11 tests)

### Docs
- README — fix Forge version (51.0.8 in branches table), add per-player locale + languages sections (#164)
- CHANGELOG/TESTING cleanup (#166)

## 2.1.0 (2026-08-02)

### Added
- Full production parity with 1.21 (Option B cascade, metrics layer, MojangProfileCache, saveSkinAsync drain-coalesce)
- /skin metrics command with human/json/players/cleanup/reset subcommands
- MojangProfileCache (TTL 1h, cap 1000) wired into MojangApiHttpImpl
- Network metrics instrumentation via ChannelDuplexHandler
- 9 integration tests (CommandDispatch, ObserverPacket, PersistenceRoundTrip, PermissionGate, MineSkinPath, ClearSourceRandom, CrossDimensionBroadcast, ConcurrentSet, WireLevelBytes)
- MockServer JUnit harness (TestServerContext, TestPlayerFactory, PacketLog, WireSerializer, AsyncSupport)
- EntityTracker untrack/re-track per-viewer refresh (deliberate improvement over 1.21)
- All 16 lib-17 behavioral fixes (cherry-pick from audit)
- SPDX MIT license headers on all 125 .java files

### Changed
- SkinCommand refactored to dispatch via Option B cascade
- Config refactored with metrics + cache keys
- Forge 14.23.5.2847 canonical across all docs (2860 doesn't work)

### Fixed
- Login-time synchronous 3-provider HTTP chain offloaded to executor (no more 30s server freeze per login)
- Multi-target /skin clear per-target restores from Mojang
- SkinRefreshTask wrapped in try/catch (no partial cascade on exception)
- ForgePermissionService uses PermissionAPI.hasPermission
- LuckPerms backend falls back to op check on pre-load
- MineSkin 429 bounded sleep (was unbounded)
- CustomSkinProperty base64 validation
- ConcurrentSetIT disk-vs-memory race resolved

### Removed
- HMCLite scenarios replaced with bash server-log smoke assertions
- Stale `IMPLEMENTATION_PLAN.md` references from AGENTS.md
- SkinsRestorer-derived code patterns (clean-room rewrite)
- Stale "Phase 5 viability gate" comments in Config.java

## 2.0.0 (2026-08-02)

### Added
- /skin metrics command with human/json/players/cleanup/reset subcommands
- MojangProfileCache (TTL 1h, cap 1000) wired into MojangApiHttpImpl
- saveSkinAsync with drain-coalesce writer (50ms debounce, race-safe latch)
- Network metrics instrumentation via ChannelDuplexHandler
- 9 integration tests (CommandDispatch, ObserverPacket, PersistenceRoundTrip, PermissionGate, MineSkinPath, ClearSourceRandom, CrossDimensionBroadcast, ConcurrentSet, WireLevelBytes)

### Changed
- SkinCommand.execute() now dispatches via Option B cascade (REMOVE+ADD + respawn cascade + per-viewer EntityTracker untrack/re-track)
- Improved permission semantics (PermissionAPI.hasPermission vs op-only fallback)

### Fixed
- Multi-target /skin clear now per-target restores from Mojang (was: applied first target's skin to all)
- SkinRefreshTask wrapped in try/catch (was: partial cascade on exception)
- Login-time 3-provider HTTP chain offloaded to executor (was: blocking main thread)
- CustomSkinProperty base64 validation added
- MineSkin 429 bounded sleep (was: unbounded provider-controlled delay)
- LuckPerms backend falls back to op check on pre-load (was: unconditional deny)
- ConcurrentSetIT now asserts disk state after race (was: memory only)

### Removed
- HMCLite scenarios replaced with bash server-log smoke assertions
- Forge 14.23.5.2860 pin reverted to canonical 14.23.5.2847 (2860 does not work in CI env)
- Stale `IMPLEMENTATION_PLAN.md` references from AGENTS.md
- SkinsRestorer-derived code patterns (clean-room rewrite of MojangApiHttpImpl, MineSkinApiHttpImpl, EverlastingHelpers)
- Stale "Phase 5 viability gate" comments in Config.java
- UPDATE_DISPLAY_NAME from refresh cascade (carried zero textures on wire; replaced with REMOVE+ADD_PLAYER)
