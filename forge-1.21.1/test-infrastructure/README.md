# EverlastingSkins — Forge 1.21.1 GameTest Infrastructure

Local GameTest runner for this lane:

```bash
./test-infrastructure/run-gametest-local.sh
```

Runs `:forge-1.21.1:runGameTestServer` with the `everlastingskins`
namespace (override: `./test-infrastructure/run-gametest-local.sh <namespace>`)
and fails fast on assertion output.

Note: the E2E wiremock harness (HeadlessMC scenarios + wiremock profiles) is
reference-lane (forge-1.21) only; this lane uses the in-game GameTest framework
(`src/gametest`, `@GameTest` annotations).
