# Testing Architecture

EverlastingSkins uses a three-tier testing pyramid:

## Tier 1: Pure Java unit tests (JUnit 5)
- 257 tests on 1.21 branch
- 235 tests on mc1.12.2 branch
- Coverage: provider fallback, HTTP outcomes, persistence atomicity, corruption handling, cache behavior, permission system, command dispatch, integration hooks
- Run: `./gradlew test`

## Tier 2: Forge server integration (manual smoke test)
- Real Forge server startup
- `/skin` command execution
- Lifecycle event observation
- Run: `./gradlew runServer --no-daemon`

## Tier 3: End-to-end tests (HeadlessMC + HMCSpecifics)

### Architecture

The E2E test suite uses HeadlessMC 2.10.0 + HMC-Specifics to drive a real Minecraft client against a real Forge server. Tests run in CI on every push to `1.21` and `mc1.12.2` branches.

Components:
- `test-infrastructure/scenarios/*.json` — HeadlessMC JSON test definitions (EndsWith, Contains, Send, Wait step types)
- `test-infrastructure/server/` — server.properties + eula.txt templates
- `test-infrastructure/run-e2e.sh` — local execution script
- `.github/workflows/ci.yml` — CI orchestration

### Scenarios

- `skin-set-mojang.json` — sets skin to Mojang username "Notch", verifies success
- `skin-clear.json` — sets skin, then clears it, verifies cleared state
- `two-client-skin-visibility.json` — observer client verifies another player's skin

### Local execution

Prerequisites: Java 21 (for 1.21) or Java 8+ (for 1.12.2), network access to Forge Maven

```bash
# Run E2E for 1.21 with default scenario
./test-infrastructure/run-e2e.sh 1.21

# Run E2E for 1.12.2 with skin-clear scenario
./test-infrastructure/run-e2e.sh mc1.12.2 skin-clear

# Run with observer scenario (after setting skin as TestPlayer)
./test-infrastructure/run-e2e.sh 1.21 two-client-skin-visibility
```

### CI execution

The CI workflow `.github/workflows/ci.yml` orchestrates:
1. **lint-yaml** — yamllint over all YAML files
2. **build** matrix — Gradle build + test on Java 21 (1.21) and Java 8 (mc1.12.2)
3. **e2e-test** matrix — HeadlessMC scenario execution against a Forge server

### Troubleshooting

**Server fails to start**: Check `eula.txt` exists and contains `eula=true`. Check `server.properties` has `enforce-secure-profile=false`.

**Mojang API rate limiting**: CI runners share IP space with rate-limited sources. If skin fetch fails, use `--offline` mode or stub the Mojang API.

**HeadlessMC crashes**: Delete `~/.minecraft` and `$PROJECT_DIR/headlessmc-run` to clear caches.

**Two-client scenario fails**: HeadlessMC currently runs single client per invocation. Two-client testing requires coordinated invocations.

## Skipping E2E for local commits

E2E runs only on pushes to `1.21` and `mc1.12.2` branches. Feature branches don't trigger E2E.
