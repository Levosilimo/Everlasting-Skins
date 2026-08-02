# Changelog

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
- Forge 14.23.5.2860 pinned (was: version drift 2847/2860)
