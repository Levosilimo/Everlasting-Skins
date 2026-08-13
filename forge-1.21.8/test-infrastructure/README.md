# EverlastingSkins — Forge 1.21.8 GameTest Infrastructure

Local GameTest runner for this lane:

```bash
./test-infrastructure/run-gametest-local.sh
```

Runs `:forge-1.21.8:runGameTestServer` with the `everlastingskins`
namespace (override: `./test-infrastructure/run-gametest-local.sh <namespace>`)
and fails fast on assertion output.

Note: the E2E wiremock harness (HeadlessMC scenarios + wiremock profiles) is
reference-lane (forge-1.21) only; this lane uses the data-driven GameTest
framework (`src/gametest/resources/data/everlastingskins_gametest/test_instance/`
JSONs, MC 1.21.8+).
