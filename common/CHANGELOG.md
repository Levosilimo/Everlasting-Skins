# Changelog

## 0.1.0-SNAPSHOT — 2026-08-05

Initial extraction (M2 step 1 of the multi-version plan). Pure-Java core
lifted from both branches — the 1.12.2 class forms as the Java-8 baseline
(mc1.12.2 @ ea6a2598, post-M1 SkinIO/SkinStorage), reconciled with the 1.21
forms (1.21 @ e51fef4) where noted.

### Extracted

- `skinchanger.SkinIO` / `SkinStorage` — post-M1 async drain-coalesce
  writer, serialized delete, write-after-delete ordering, in-band SHA-256
  checksums, startup sweep.
- HTTP layer — `HttpClient` (unified execute contract), `HttpsUrlConnectionHttpClient`,
  `MojangApiHttpImpl`, `MineSkinApiHttpImpl`, `CosmeticaApi`, `RandomMojangSkin`,
  `RandomCapeSource`, `MojangEndpoints`, `ProfileLookup`, all 18 response types
  (final-class forms), `HttpResult` (abstract-class form).
- Metrics — `SkinMetrics`, `LatencyHistogram`, `MetricsFormat`, `Snapshot`,
  `PlayerSnapshot`.
- Utils — `JsonUtils`, `CustomSkinProperty`, `PropertyUtils`, `UUIDUtils`,
  `UrlAllowlist`, `EverlastingHelpers`, `EndpointsConfig`.
- Permission seam — `IPermissionService`, `PermissionServiceManager`
  (registration-based; the MC-bound `PermissionContext` stays per-version).
- Integration — `DiscordSrvConfig` (decoupled settings holder).
- Resources — `endpoints.properties`, `everlastingskins/default-skin.properties`.
- Tests — 34 test/support files + 17 fixtures (306 test cases, no skips).

### Decoupling (Config/EverlastingSkins/Minecraft seams)

- Logging moved to Log4j2 `LogManager.getLogger(Class)` (both consumers ship
  log4j at runtime).
- `MojangProfileCache` — TTL/capacity constructor injection; defaults mirror
  the per-version Config (1 h / 1000).
- `MojangApiHttpImpl` — `profileCacheEnabled` constructor injection
  (default true).
- `MineSkinApiHttpImpl` — API key + allowlist constructor injection
  (defaults: empty key, allowlist off, Config's domain list).
- `DiscordSrvConfig` — static `configure(enabled, channelId)` injection.
- `PermissionServiceManager` — per-version bootstrap registers backends;
  unregistered checks fail closed.
- `RandomMojangSkin` — Mojang API seam (`setMojangAPI`, defaults to the HTTP impl).
- Source discriminators promoted to the API interfaces: `MojangAPI.SOURCE_MOJANG`,
  `MineSkinAPI.SOURCE_MINESKIN` (were per-version `SkinAction`/`SkinActionCommand`).

### Reconciliations (behavioral deltas documented)

- `MineSkinApiHttpImpl.REQUEST_TIMEOUT`: 30 000 ms (1.12.2) → 10 000 ms
  (1.21 form, per plan rule "pick the 1.21 form").
- `SkinIO.deleteSkin` propagates failures (M1 form) instead of 1.21's
  swallow-and-log.
- `PropertyUtils.getTextureId` added as alias of `getSkinTextureUrlStripped`
  so both branches' consumers compile unchanged.
- `EverlastingHelpers`, `MojangEndpoints`, `HttpResponse` and friends use the
  1.12.2 forms (Java 8 idioms, `Locale.ROOT` formatting, fail-closed
  `parseBodyOrNull`).
- `IPermissionService.hasPermission(PermissionContext, String)` →
  `hasPermission(UUID, int, String)` — the only interface signature change;
  `PermissionContext` is exactly a (UUID, opLevel) pair.

### Excluded (stays per-version)

`JavaHttpClient` (java.net.http, Java 11+), `CompletionSources`,
`PermissionContext`, `MetricsDumper`, `NetworkMetricsHandler`, all
`broadcast/*`, command layer (`SkinActionCommand`, `SkinAction`,
`SkinCommand`, `SkinMetricsCommand`), event/refresh layer
(`SkinRefreshHandler`, `SkinRefreshTask`, `SkinRestorer`), permission
services (`Forge`/`LuckPerms`/`Vanilla`), `Config`, placeholder
integration, `I18nUtils`, `PlayerLanguage`, `SkinUtils`.
