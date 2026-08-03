# Testing Architecture

EverlastingSkins uses a three-tier testing pyramid:

## Tier 1: Pure Java unit tests (JUnit 5)
- 336 unit tests + 32 GameTest = 368 total on 1.21 branch
- 325 tests on mc1.12.2 branch
- Coverage: provider fallback, HTTP outcomes, persistence atomicity, corruption handling, cache behavior, permission system, command dispatch, integration hooks
- New in 2.1.0-rc.1: `UrlAllowlistTest`, `DefaultSkinResolverTest`, `PlayerLanguageTest`, `PermissionGateIT`; `PermissionServiceManagerTest`, `VanillaPermissionServiceTest`, `LuckPermsPermissionServiceTest`, `MetricsCommandIT` refreshed/pre-existing (not new)
- **Test gaps closed (#170)**: 11 new tests covering Discord i18n routing, MojangProfileCache config, per-player locale behavior
- Run: `./gradlew test`

## Tier 2: Forge server integration (manual smoke test)
- Real Forge server startup
- `/skin` command execution
- Lifecycle event observation
- Run: `./gradlew runServer --no-daemon`

## Tier 3: End-to-end test — server-log smoke (automated)

### mc1.12.2: server-log smoke test

The vanilla 1.12.2 client has no console (stdin/stdout), so HeadlessMC `SEND`/`ENDS_WITH`/`CONTAINS` scenario steps cannot drive it. The E2E is therefore a boot smoke test: start a real Forge server with the mod, launch a headless client, and assert on the server log.

Components:
- `test-infrastructure/run-e2e.sh` — local runner: builds the mod, installs Forge 14.23.5.2847 server, launches a headless client, asserts on the server log
- `test-infrastructure/server/` — server.properties + eula.txt templates
- `.github/workflows/ci.yml` — `e2e-test-1122` job

Assertions (server log):
1. Server booted (`For help, type "help"`)
2. Mod discovered (`everlastingskins` in the FML mod list)
3. Client connected (`TestPlayer joined the game`)

Functional coverage (command cascade, persistence, permissions, packets) lives in the JUnit integration tests (`src/test/java/.../integration/*IT`); this E2E is a boot smoke test.

### Local execution

Prerequisite: Java 8+ on PATH, network access to Forge Maven

```bash
# Run E2E for mc1.12.2
bash test-infrastructure/run-e2e.sh mc1.12.2
```

### CI execution

The `e2e-test-1122` job ("Boot Smoke (mc1.12.2)") in `.github/workflows/ci.yml` runs on pushes to the `mc1.12.2` branch and inlines the `run-e2e.sh` flow rather than calling the script: boot the Forge server, assert boot and mod discovery, launch a real headless client through the HeadlessMC wrapper, then assert `TestPlayer joined the game` on the server log. No WireMock service, no HeadlessMC scenarios.

## Skipping E2E for local commits

E2E runs only on pushes to the `mc1.12.2` branch. Feature branches don't trigger E2E.
