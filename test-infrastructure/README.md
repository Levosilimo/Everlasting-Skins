# EverlastingSkins — E2E Test Infrastructure

HeadlessMC-based end-to-end test scenarios for EverlastingSkins. Tests simulate Minecraft client commands and assert server responses.

## Quick start

```bash
# Ensure Java 8+ is on PATH
java -version

# Run a scenario
java -jar headlessmc/headlessmc-launcher-wrapper.jar \
  --command test-infrastructure/scenarios/skin-set-mojang.json
```

## Install HeadlessMC

The launcher wrapper JAR is bundled at `headlessmc/headlessmc-launcher-wrapper.jar` (v2.10.0).

To download the latest version:

```bash
curl -L -o headlessmc/headlessmc-launcher-wrapper.jar \
  "https://github.com/headlesshq/headlessmc/releases/latest/download/headlessmc-launcher-wrapper.jar"
```

HeadlessMC supports Forge from 1.7.10 through 1.21.5+. Use the `-lwjgl` flag for headless LWJGL mode on servers that require a GL context.

## Local development workflow

1. Build the mod: `./gradlew build --no-daemon`
2. Copy the built JAR to the server test environment
3. Run the scenario against a local Forge server with the mod installed
4. Check exit code (0 = pass, non-zero = assertion failure)

## Scenario JSON format

Each scenario file follows this structure:

| Field | Type | Description |
|---|---|---|
| `name` | string | Human-readable test name |
| `description` | string | What the scenario verifies |
| `type` | string | Always `"RUNS"` for single-client tests |
| `config` | object | Server connection config (accountType, username) |
| `steps` | array | Ordered list of test steps |

### Step types

| Type | Behavior |
|---|---|
| `ENDS_WITH` | Pass if the last received line ends with the given message |
| `CONTAINS` | Pass if any received line contains the message |
| `SEND` | Send chat text or command to the server |
| `WAIT` | Pause for `timeout` seconds |

## Adding scenarios

1. Create a new JSON file in `scenarios/`
2. Add descriptive steps that exercise the feature
3. Run locally to verify
4. Add the scenario path to the CI workflow's `scenario` input

## CI integration

The E2E job runs on the `headlesshq/mc-runtime-test` GitHub Action (v4.5.1):

```yaml
- uses: headlesshq/mc-runtime-test@4.5.1
  with:
    mc: '1.21'
    modloader: 'forge'
    version: '51.0.8'
    xvfb: true
    cache-mc: 'github'
    scenario: 'test-infrastructure/scenarios/skin-set-mojang.json'
```

The job depends on the `build` job and runs only after a successful build.
