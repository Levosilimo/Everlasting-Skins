# EverlastingSkins — E2E Test Infrastructure

Server-log smoke test for the mc1.12.2 port. The vanilla 1.12.2 client has
no console (stdin/stdout), so HeadlessMC `SEND`/`ENDS_WITH`/`CONTAINS`
scenario steps cannot drive it. The runner instead starts a real Forge
server with the mod, launches a headless client that auto-connects, and
asserts on the server log:

1. Server booted (`For help, type "help"`)
2. Mod discovered (`everlastingskins` in the FML mod list)
3. Client connected (`TestPlayer joined the game`)

Functional coverage (command cascade, persistence, permissions, packets)
lives in the JUnit integration tests; this E2E is a boot smoke test.

## Quick start

```bash
# Ensure Java 8 is on PATH, then run the 1.12.2 E2E
bash test-infrastructure/run-e2e.sh mc1.12.2
```

`run-e2e.sh` builds the mod, installs Forge 14.23.5.2847 server with a
client profile pre-installed via the 14.23.5.2860 installer (the 2847
installer has no `--installClient` CLI), launches the server, and drives
the client through the HeadlessMC wrapper (`-lwjgl -offline
--uid 14.23.5.2860`).

## Why no scenario files

HeadlessMC scenario steps match against the client process's stdin/stdout.
A vanilla 1.12.2 client has neither, so `SEND`/`ENDS_WITH`/`CONTAINS` steps
cannot drive it. The `hmc-specifics` module ships an official Forge 1.12.2
build that can install a console bridge, but this repo deliberately disables
it in CI (`hmc.auto.download.specifics=false` in `ci.yml` and `run-e2e.sh`):
without the bridge the client cannot be command-driven, so the job stays a
boot smoke rather than a scenario run. A JLine `ClassNotFoundException` was
observed on a 1.21 client; that is not evidence for 1.12.2 and is not why
specifics are disabled. Scenario JSON files were removed in favor of bash
assertions on the server log, which is where all command and join activity
is visible.

## Install HeadlessMC

The launcher wrapper JAR is v2.10.0. `run-e2e.sh` downloads it on demand
into `test-infrastructure/headlessmc/`:

```bash
mkdir -p test-infrastructure/headlessmc
curl -L -o test-infrastructure/headlessmc/headlessmc-launcher-wrapper.jar \
  "https://github.com/headlesshq/headlessmc/releases/download/2.10.0/headlessmc-launcher-wrapper-2.10.0.jar"
```

## CI integration

`.github/workflows/ci.yml` (job `e2e-test-1122`, "Boot Smoke (mc1.12.2)")
inlines the same flow instead of calling `run-e2e.sh`: start the Forge
server, assert boot and mod discovery, launch the headless client, and
assert `TestPlayer joined the game` on `$SERVER_DIR/logs/latest.log`. No
`/skin` command can be sent without a console bridge, so the skin API
endpoints are never exercised: the job uses no WireMock service and no
endpoint overrides. Client crashes are visible because launch steps run
under `set -euo pipefail`.
