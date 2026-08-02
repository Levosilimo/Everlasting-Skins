# Changelog

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
