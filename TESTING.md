# Testing Architecture

EverlastingSkins uses a three-tier testing pyramid:

## Tier 1: Pure Java unit tests (JUnit 5)
- 257 tests on 1.21 branch
- 205 tests on mc1.12.2 branch
- Coverage: provider fallback, HTTP outcomes, persistence atomicity, corruption handling, cache behavior, permission system, command dispatch, integration hooks
- Run: `./gradlew test`

## Tier 2: Forge server integration (manual smoke test)
- Real Forge server startup
- `/skin` command execution
- Lifecycle event observation
- Run: `./gradlew runServer --no-daemon`

## Tier 3: End-to-end tests

### 1.21: GameTest (automated)

The 1.21 branch uses Minecraft's GameTest framework for automated integration testing. The `gametest-121` CI job runs 28 tests covering the /skin command pipeline via mock players + EmbeddedChannel packet assertions.

Components:
- `src/test/java/` — GameTest methods (skin-set, skin-clear, persistence, refresh)
- `.github/workflows/ci.yml` — `gametest-121` job

Run locally:
```bash
./gradlew runGameTestServer --no-daemon --console=plain
```

### mc1.12.2: HeadlessMC E2E (automated)

The mc1.12.2 branch uses HeadlessMC 2.10.0 + HMC-Specifics to drive a real Minecraft client against a real Forge server.

Components:
- `test-infrastructure/scenarios/*.json` — HeadlessMC JSON test definitions
- `test-infrastructure/server/` — server.properties + eula.txt templates
- `test-infrastructure/wiremock/` — WireMock mappings for Mojang API stubs
- `.github/workflows/ci.yml` — `e2e-test-1122` job

Scenarios:
- `skin-set-mojang.json` — sets skin to Mojang username "Notch", verifies success
- `skin-clear.json` — sets skin, then clears it, verifies cleared state

Run locally:
```bash
# Prerequisites: Java 8+, network access to Forge Maven
./test-infrastructure/run-e2e.sh mc1.12.2 skin-set-mojang
./test-infrastructure/run-e2e.sh mc1.12.2 skin-clear
```

## Skipping E2E for local commits

E2E runs only on pushes to `1.21` and `mc1.12.2` branches. Feature branches don't trigger E2E.
