# EverlastingSkins — E2E Test Infrastructure (mc1.12.2)

Command-driven real-client E2E for the mc1.12.2 port (headlessmc era, since
#449/#460). A real Forge 14.23.5.2847 server runs the mod, a real 1.12.2
client (TestPlayer) joins under the HeadlessMC launcher, and the vendored
hmc-specifics console bridge sends `/skin set mojang Notch` through the
client's chat surface. The server-side `ES_E2E_SKIN` sentinel is the primary
assertion.

`E2E (mc1.12.2)` is a REQUIRED check on `main` (part of the 22-context
branch-protection contract), so it runs on every PR and push.

## Flow

1. **Boot** — `run-e2e.sh` builds the lane (Gradle 4.10.3 / ForgeGradle 2.3.4,
   Java 8) and hands off to the shared orchestrator:
   `scripts/e2e/e2e-common.sh` dispatches to
   `scripts/e2e/drivers/headlessmc.sh` (`E2E_ERA=headlessmc`), which owns the
   full flow (server boot + client + assertions). The server runs with
   `-Deverlastingskins.e2e=true` (offline; TestPlayer op'd via ops.json,
   level 4) and must reach `For help, type "help"` (240s, exit 2 on timeout).
2. **Join** — the client launches the forge-1.12.2-14.23.5.2860 profile
   (`-lwjgl -offline`, dummy assets, jline disabled). The vanilla 1.12.2
   client has no stdin console, so the scenario runs through the vendored
   hmc-specifics bridge (`hmc-specifics-1.12.2-2.4.0-lexforge-release.jar`):
   its HeadlessMcMcTweaker + mixins pipe stdin through the game's chat.
   A hard join gate waits for `<user> joined the game` on the server log
   (240s; exit 2 retryable, with a distinct fail-fast on the
   parent-version-resolution signature).
3. **SEND** — the scenario gates on the mod's own client-side
   `ES_E2E_CLIENT_JOINED` marker (first in-world client tick), then SENDs
   `/skin set mojang Notch` and the bridge ack `. hmc-e2e-bridge-ok`.
   There is no quit SEND: the client stays in-world until the server-side
   skin action completes (a mid-command disconnect aborts the sentinel
   path).
4. **Assert** — server-log assertions, all under
   `-Deverlastingskins.e2e=true`:
   - `ES_E2E_SKIN=cmd player=...` — SkinCommand entry marker: the /skin
     reached the server. Absent after a successful join is a distinct
     assertion failure (pre-join-race signature).
   - `ES_E2E_SKIN=ok player=...` — SkinAction success sentinel (primary).
   - `<TestPlayer> hmc-e2e-bridge-ok` — bridge ack (command_executed).
5. **Report** — the driver writes the master-plan contract JSON
   (`${RUNNER_TMP}/e2e-result.json`: lane, era=headlessmc, server_booted,
   client_joined, command_executed, command_reached_server, renderer_state,
   server_sentinel, exit_code) and exits per the contract below.

## Exit-code contract

- `0` all green (sentinel seen, result doc green)
- `1` assertion failed (sentinel miss / command never reached the server)
- `2` retryable infra (boot/join timeout, artifact or client-profile fetch
  failure)
- `3` build failure (lane build or missing Java 8)

## Quick start

```bash
cd mc1.12.2 && JAVA_HOME=<jdk8> bash test-infrastructure/run-e2e.sh mc1.12.2
```

`run-e2e.sh` finds Java 8 via `JAVA_HOME` or known install paths; the
positional lane arg is accepted and ignored.

## Materials

All artifacts are sha1-pinned and cached under
`~/.cache/everlastingskins/e2e/<lane>`: the HeadlessMC launcher wrapper
(2.10.0), the server jar (launcher.mojang.com), the Forge 14.23.5.2847
universal, and its manifest Class-Path libs. The hmc-specifics bridge jar
has no live upstream URL and is vendored (sha1-pinned seed) at
`mc1.12.2/test-infrastructure/hmc-specifics/`. The client profile is
installed by the 14.23.5.2860 installer's `--installClient` (the canonical
2847 installer has no such CLI, and HMCLite's forgecli crashes on 1.12.2
installers); the vanilla parent version is provisioned deterministically
from the Mojang version manifest before launch (fresh client homes, exit 2
on failure).

## CI integration

`.github/workflows/ci.yml` job `e2e-mc1_12_2` (displayed as
`E2E (mc1.12.2)`, the exact required-check name) runs
`bash mc1.12.2/test-infrastructure/run-e2e.sh mc1.12.2` on ubuntu-24.04
with JDK 8 and a 45-minute timeout.
