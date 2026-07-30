# EverlastingSkins — E2E Test Infrastructure

HeadlessMC-based end-to-end test scenarios for EverlastingSkins. Tests simulate Minecraft client commands and assert server responses.

## Quick start

```bash
# Ensure Java 8+ is on PATH
java -version

# Run a scenario
java -jar test-infrastructure/headlessmc/headlessmc-launcher-wrapper.jar \
  --command test-infrastructure/scenarios/skin-set-mojang.json
```

## Install HeadlessMC

The launcher wrapper JAR is v2.10.0. Download it before running locally:

```bash
mkdir -p test-infrastructure/headlessmc
curl -L -o test-infrastructure/headlessmc/headlessmc-launcher-wrapper.jar \
  "https://github.com/headlesshq/headlessmc/releases/download/2.10.0/headlessmc-launcher-wrapper-2.10.0.jar"
```

HeadlessMC supports Forge from 1.7.10 through 1.21.5+. Use the `-lwjgl` flag for headless LWJGL mode on servers that require a GL context.

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

1. Create a new JSON file in `test-infrastructure/scenarios/`
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
