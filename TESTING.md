# Testing Architecture

EverlastingSkins uses a three-tier testing pyramid:

## Tier 1: Pure Java unit tests (JUnit 5)
- 335 unit tests + 31 GameTest = 366 total on 1.21 branch
- 325 tests on mc1.12.2 branch
- Coverage: provider fallback, HTTP outcomes, persistence atomicity, corruption handling, cache behavior, permission system, command dispatch, integration hooks
- New in 2.1.0-rc.1: `MojangProfileCacheTest`, `UrlAllowlistTest`, `DefaultSkinResolverTest`, `PermissionServiceManagerTest`, `VanillaPermissionServiceTest`, `LuckPermsPermissionServiceTest`
- Run: `./gradlew test`

## Tier 2: Forge server integration (manual smoke test)
- Real Forge server startup
- `/skin` command execution
- Lifecycle event observation
- Run: `./gradlew runServer --no-daemon`

## Tier 3: End-to-end tests

### 1.21: GameTest (automated)

The 1.21 branch uses Minecraft's GameTest framework for automated integration testing. The `gametest-121` CI job runs 31 tests covering the /skin command pipeline via mock players + EmbeddedChannel packet assertions.

Components:
- `src/gametest/java/` — GameTest methods (skin-set, skin-clear, persistence, refresh)
- `.github/workflows/ci.yml` — `gametest-121` job

Run locally:
```bash
./gradlew runGameTestServer --no-daemon --console=plain
```

### mc1.12.2: Server-log smoke test (automated)

The vanilla 1.12.2 client has no console (stdin/stdout), so HeadlessMC `SEND`/`ENDS_WITH`/`CONTAINS` scenario steps cannot drive it. The mc1.12.2 E2E is a boot smoke test: start a real Forge 14.23.5.2847 server with the mod, launch a headless client through the HeadlessMC wrapper, and assert on the server log (server booted, mod discovered, `TestPlayer joined the game`). Mojang endpoints are stubbed with WireMock in CI.

Components:
- `test-infrastructure/run-e2e.sh` — local runner + log assertions
- `test-infrastructure/assert-skin-property.sh` — asserts the `SKIN_REFRESH` log line
- `test-infrastructure/server/` — server.properties + eula.txt templates
- `.github/workflows/ci.yml` — `e2e-test-1122` job

Run locally (Java 8+ on PATH):
```bash
bash test-infrastructure/run-e2e.sh mc1.12.2
```

## Skipping E2E for local commits

E2E runs only on pushes to the `mc1.12.2` branch (GameTest on `1.21` runs via CI's `gametest-121` job). Feature branches don't trigger E2E.
