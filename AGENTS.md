# AGENTS.md — multi-module workflow

## Layout & build

One Gradle root (9.3.1) for the Forge line. Modules:

- `:common` — version-independent core, `--release 8`. NEVER add a forge
  binding here; never raise the release level. Consumer of last resort:
  every `forge-*` module and the mc1.12.2 lane.
- `:forge-1.21`, `:forge-1.21.1`, `:forge-1.21.4`, `:forge-1.21.8`, `:forge-26.2` —
  thin binding layers applying `everlastingskins.forge-module`; MC/Forge
  versions live in each subproject's `gradle.properties`. forge-26.2 is the
  newest line (MC 26.2 / Forge 65.0.9 / Java 25 / unobfuscated MC, FG 7.0.17
  on the buildSrc classpath; `minecraft.unobfuscated=true` gates the
  convention's mappings() call).
- `mc1.12.2/` — NOT a subproject (FG 2.3.4 needs Gradle 4.x + Java 8). It
  stays on its own wrapper (4.10.3), builds out-of-band, and consumes `:common`
  via a source-dir share (`srcDir '../common/src/main/java'` in its
  `build.gradle`). Classes present in both `src/main/java` and `:common` were
  deleted from the lane at import — `:common` is canonical for every shared
  class.

## Convention plugins (`buildSrc/src/main/kotlin/`)

- `everlastingskins.forge-module.gradle.kts` — FG 7.x (consumed at 7.0.17)
  + parameterized toolchain (Java 21 for 1.21.x, Java 25 for 26.2 via
  `java.toolchain.version`). All forge build logic lives here; subproject
  build scripts are just `plugins { id("everlastingskins.forge-module") }`.
  Parameterization is via gradle.properties, never via editing build
  scripts. The `minecraft.unobfuscated` property (default false) gates the
  mappings() call for 26.x and enables the configuration-cache
  re-materialization of the unobfuscated-UserDev compile classpath.
- `no-mixin.gradle.kts` — the Mixin-usage gate (port from the parent's
  `common/build-logic`, M2 step 2). Registers `verifyNoMixin` (fails the
  build on `@Mixin` annotations, mixingradle, the mixin plugin id, `mixin {}`
  blocks, or mixins.json bundling) and wires it into `build`. Applied by
  `everlastingskins.forge-module` and `:common` itself; new forge convention
  plugins must apply it too.

## Legacy lanes (mc1.12.2 / forge-1.7.10 / forge-1.8.9 / forge-1.16.5 / forge-1.20.1) — out-of-band

Not part of this Gradle root (lib-34 lane separation, PR #267): ForgeGradle
5.1.x rejects Gradle 8.0+ and ForgeGradle 6.0.x rejects Gradle 9.0+, so
neither can run inside the root's Gradle 9.3.1 (verified empirically
2026-08-06 against 5.1.77 / 6.0.54). The 1.7.10 and 1.8.9 lanes predate
even FG 5.1.x and use Gradle wrappers pinned at 4.4.1 / 4.10.3 respectively.
Each lane is its own build with its own wrapper and FG version:

- `mc1.12.2/` — Gradle 4.10.3 (run on Java 8), ForgeGradle 2.3.4.
- `forge-1.7.10/` — Gradle 4.4.1 (run on Java 8), GTNH `ForgeGradle:1.2.11`
  via jitpack (`https://jitpack.io/`, plus `maven.minecraftforge.net` for
  legacy MCP/RetroGuard artifacts FG 1.2.11 still resolves), MCP stable_12.
  Uses LaunchWrapper `@Mod`/`@Mod.EventHandler` (cpw.mods.fml) and the
  netty-based `NetworkRegistry.INSTANCE.newChannel(...)` — the 1.6.4
  `@NetworkMod` annotation was REMOVED in FML 1.7 (verified absent from
  10.13.4.1614). `EntityPlayer.getGameProfile()` is the profile surface
  (no `getPersistentID` — 1.8+). Consumes `:common` by source-dir share.
  dep-analysis NOT eligible (out-of-band, same policy as
  mc1.12.2/forge-1.16.5/forge-1.20.1).
- `forge-1.8.9/` — Gradle 4.10.3 (run on Java 8), ForgeGradle 2.1-SNAPSHOT,
  MCP stable_20. Build with `cd forge-1.8.9 && JAVA_HOME=<jdk8> ./gradlew
  build`; consumes `:common` via source-dir share; inline no-mixin gate
  (heavier variant — scans build files too); dep-analysis NOT eligible
  (Gradle 4.x < 8.11 minimum).
- `forge-1.16.5/` — Gradle 7.6.4 (run on Java 8), ForgeGradle 5.1.77.
- `forge-1.20.1/` — Gradle 8.7 (run on Java 21, Java 17 toolchain via
  foojay), ForgeGradle 6.0.54.
- `forge-1.7.10/` — Gradle 4.4.1 (run on Java 8), GTNH ForgeGradle 1.2.11
  via jitpack (`https://jitpack.io/`, plus `maven.minecraftforge.net` for
  legacy MCP/RetroGuard artifacts FG 1.2.11 still resolves), MCP stable_12.
  Uses LaunchWrapper `@Mod`/`@Mod.EventHandler` (cpw.mods.fml) and the
  netty-based `NetworkRegistry.INSTANCE.newChannel(...)` — the 1.6.4
  `@NetworkMod` annotation was REMOVED in FML 1.7 (verified absent from
  10.13.4.1614). `EntityPlayer.getGameProfile()` is the profile surface
  (no `getPersistentID` — 1.8+). Consumes `:common` by source-dir share.
  dep-analysis NOT eligible (out-of-band, same policy as
  mc1.12.2/forge-1.16.5/forge-1.20.1).

All five consume `:common` by source-dir sharing (they cannot use
`project(":common")`), inline their own no-mixin gate, and are built from
their own directory: `cd <lane-dir> && ./gradlew build` (or
`JAVA_HOME=<jdk8> ./gradlew build` for the Java 8 lanes). The root's
settings.gradle.kts does not include them. The `consumeCommon=false` gate
was removed in #276 and was scoped only to in-root `forge-*` subprojects
via the convention; legacy lanes use source-dir share and never
exercise the gate. Re-add caveat: if a future forge-* subproject (e.g.
1.7.10 / 1.8.9) requires vendored `:common` copies for tooling reasons,
re-add the gate to buildSrc `forge-module.gradle.kts` and re-introduce
the opt-out property. (forge-1.7.10 + forge-1.8.9 resolved 2026-08-07:
source-dir share is sufficient — the gate was NOT re-added.)

### forge-1.7.10 supply chain (jitpack pin + fallback)

`com.github.GTNewHorizons:ForgeGradle:1.2.11` is pinned EXACTLY (never
float a SNAPSHOT) and resolved from jitpack; the GTNH fork is the Legacy
Modding Wiki's prescribed replacement for the dead upstream FG 1.2 (the
hardcoded Mojang API 403s since 2022). This is in-lane supply-chain
hardening, not scope creep: jitpack uptime is a real CI risk. Mitigations:

- **Pin:** the buildscript classpath is exact (`1.2.11`), so resolution is
  reproducible across runs.
- **Vendor fallback:** after a successful build, the resolved artifacts
  live in `~/.gradle/caches/modules-2/files-2.1/com.github.GTNewHorizons/`.
  To ride out jitpack downtime, pre-populate the same cache path on the
  build host (copy the jar/poms), or point the buildscript repo at a local
  `maven { url = uri("file://...") }` mirror. FG 1.2.11 also resolves
  legacy MCP artifacts (`de.oceanlabs.mcp:RetroGuard:3.6.6`) from
  `maven.minecraftforge.net` — keep that repo in the buildscript block.

## Rules

1. **`:common` owns version-independent code.** Every forge module is a thin
   binding layer: version-independent classes live in `:common`, bindings in
   the `forge-*` module. When a class moves into `:common`, delete the moved
   copy from the module (duplicate classes break the mc1.12.2 source-dir
   share).
2. **`:common` is frozen at `--release 8`** with `-Werror`. Compile it with
   `./gradlew :common:build`; if it fails, fix it before touching forge
   modules.
3. **No mixingradle** anywhere. Mixins are annotation-processor + jar
   manifest only. Enforced by the `verifyNoMixin` gate (buildSrc
   `no-mixin.gradle.kts`) on every forge module and `:common`.
4. **No new dependencies without a lane decision** — same Maven deps as the
   legacy 1.21/mc1.12.2 builds (gson, authlib, log4j-api arrive transitively
   on Minecraft classpaths). Lane decision (forge-1.8.9, 2026-08-07): the
   lane's test scaffold uses JUnit 5.10.3 + mockito-core 2.28.2, mirroring
   mc1.12.2 exactly (same Gradle 4.10.3 / Java 8 toolchain, no new Maven
   coords); rationale is recorded in forge-1.8.9/build.gradle.
5. **Point-release parity:** keep `forge-1.21.x` gradle.properties versions
   in sync with the tags (`mc1.21.x-v2.1.0-rc1` etc.). Verify against git
   history before changing. SCOPE: applies ONLY to the four `forge-1.21.x`
   point releases. forge-26.2 is a new MC major (new Forge line), not a
   point release of 1.21 — it is governed by its own tag prefix
   `mc26.2-v*` (colloquial '26.2', matching the Forge 65.x line naming).
6. **`:common` consumed unconditionally:** every forge-* subproject depends
   on `:common` via `implementation(project(":common"))` in
   buildSrc `everlastingskins.forge-module.gradle.kts`; there is no opt-out.
   Historical context: a `consumeCommon=false` gate existed as a safety
   valve for forge-1.21's JPMS split-package (#265) and was removed after
   the Option B1 relocation to `forge21.*` (#268) resolved the conflict.
   Re-add caveat: if a future forge-* subproject (e.g., forge-1.8.9)
   requires vendored `:common` copies for tooling reasons, re-add the gate
   to buildSrc `forge-module.gradle.kts` and re-introduce the opt-out
   property. (The dead plugin-era gate was deleted in #267.)
   Contingency note for 26.x: forge-26.2 consumes `:common` unconditionally
   via the convention; there is no split-package risk (common=`levosilimo.*`
   vs the 26.x forge jar=`net.*`; the binding lives under `forge26.*`). If a
   future 26.x jar ever collides, re-add the consumeCommon gate per the
   #265/#276 caveat.

   Resolution (forge-1.8.9, 2026-08-07): no gate re-added. Out-of-band
   lanes (incl. forge-1.8.9) consume `:common` purely by source-dir share
   (`srcDir '../common/src/main/java'`), which never exercises
   `implementation(project(":common"))` — the caveat's premise (vendored
   copies for tooling reasons) does not apply, so the gate would be dead
   configuration. Source-dir share is the gate-free mechanism for all
   out-of-band lanes (mc1.12.2 / forge-1.16.5 / forge-1.20.1 precedent).
   forge-1.7.10 resolution (2026-08): the caveat named forge-1.7.10 /
   forge-1.8.9 as contingencies, but forge-1.7.10 is an out-of-band lane
   that consumes `:common` by source-dir share — it can never exercise
   `implementation(project(":common"))`, so the gate was NOT re-added
   (dead configuration; see "Legacy lanes" above). forge-1.8.9 follows the
   same pattern.
7. **1.12.2 lane:** never include it in `settings.gradle.kts`. It builds
   out-of-band (`cd mc1.12.2 && ./gradlew build` with Java 8); changes there
   are reviewed against the shared `:common` contract, not against this build.
   The lane's `build.gradle` shares `:common` as an extra source dir — when a
   class moves into `:common`, delete the lane copy or javac fails on the
   duplicate class.

## 1.12.2 lane (per-lane wrapper)

`mc1.12.2/` is a self-contained Gradle build imported from the standalone
1.12.2 repo — own wrapper (Gradle 4.10.3), FG 2.3.4, Java 8, MCP
`snapshot_20171003`. Build it in isolation:

```bash
cd mc1.12.2 && JAVA_HOME=<jdk8> ./gradlew build
```

It is intentionally NOT listed in `settings.gradle.kts`; the root build never
sees it. The main source set adds `../common/src/main/java` (source-dir share,
no JPMS on Java 8), so `:common` is the single canonical copy of shared
classes. Lane-specific code (Forge bindings, commands, listeners, mixins,
permission services) stays in `mc1.12.2/src/main/java` and adapts to the
`:common` APIs (e.g. `IPermissionService.hasPermission(UUID, int, String)`;
the manager is fail-closed until the lane's `EverlastingSkins.init()`
registers backends).

## Verification

```bash
./gradlew :common:build        # fast gate after :common changes
./gradlew :forge-1.21:build    # full forge module (slow; downloads userdev)
./gradlew :forge-26.2:build    # 26.2 lane (Java 25 toolchain, unobfuscated MC, FG 7.0.17)
./gradlew build                # whole Forge line
bash scripts/gradle-health.sh  # dep-hygiene sweep (manual, WARN-only; per-consumer :projectHealth)
```

CI (`ci.yml`) is a per-module matrix (PR #260): lint-yaml → `build` over
`:common` + the four 1.21.x modules, plus the out-of-band mc1.12.2 build,
`E2E (mc1.12.2)` stub, and out-of-band `Build (forge-1.8.9)` /
`Build (forge-1.16.5)` / `Build (forge-1.20.1)` / `Build (forge-1.7.10)`
lanes (own wrappers, JDK 8 / JDK 8 / JDK 21 / JDK 8). Treat the matrix as
authoritative for what is
buildable in CI.

Source status: every lane is SOURCE-COMPLETE — forge-1.16.5 (post-#274),
forge-1.20.1 (post-#273), the 1.21.1 / 1.21.4 / 1.21.8 point releases
(post-#278 / #280 / #281), forge-26.2 (Java 25, unobfuscated MC,
EventBus 7; data-driven GameTest green), and forge-1.7.10 (GTNH FG 1.2.11 +
MCP stable_12; 48 unit tests, JUnit 4).

### Fail-fast hooks

Tiered local gates mirroring CI (see `.githooks/` and `lefthook.yml`):

- **pre-commit** (<30s, sequential): aislop staged scan → test-count gate →
  `verifyNoMixin` → offline parallel compile. Active via
  `git config core.hooksPath .githooks` (already set in this repo).
- **pre-push** (5-10 min, heavy): full unit test suite → GameTest (1.21) via
  `forge-1.21/test-infrastructure/run-gametest-local.sh` → `aislop ci
  --changes` (mirrors CI's `aislop (M2)` job). Skip once with
  `git push --no-verify`.
- `scripts/test-count-gate.sh` mirrors ci.yml's `@Test >= 150` floor: counts
  `@Test` + `@ParameterizedTest` in `common/src` + `forge-1.21/src`. The
  gate is PATH-SCOPED (it counts `@Test` tokens from both JUnit 4 and
  JUnit 5 identically — the regex `@(Test|ParameterizedTest)\b` matches
  either); forge-1.7.10's JUnit 4 tests are not counted because they live
  outside those two paths, not because of the JUnit version.

The root `gradlew` requires JVM 17+ (the mc1.12.2 lane keeps its own JDK 8
wrapper). Hooks pass `--offline`; a first run needs a prior online build to
populate `~/.gradle`.

Local dev should set `~/.gradle/gradle.properties` (operational, not
committed) to:

```properties
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
```

`lefthook.yml` documents the intended lefthook adoption (Go binary, parallel
pre-push); `.githooks/` stays active until lefthook is installed and
`core.hooksPath` unset.

### Local CI validation with act

Validate workflow syntax locally with [act](https://github.com/nektos/act);
`--dryrun` parses the workflow without Docker:

```bash
brew install act   # or: curl -s https://raw.githubusercontent.com/nektos/act/master/install.sh | sudo bash
act --dryrun --workflows .github/workflows/ci.yml     # syntax gate (no Docker)
act --self-hosted -j lint-yaml -j aislop               # cheap jobs on the host runner
```

Use it for any `.github/workflows/*.yml` change, especially during GitHub
outages (the ci.yml trigger fix, #286, sat blocked by one). `--self-hosted`
runs the cheap `lint-yaml` / `aislop` jobs on the host runner; the Forge build
matrix is not reliable under act (Docker JDK image fragility) — treat `act` as
a syntax gate, not a build substitute.

## Codebase improvement tools (knip-equivalent)

Java/Gradle has no direct `knip` equivalent; the closest analog is the
dependency-analysis-gradle-plugin (autonomousapps). Role split: **AFT /
Qartez / Codegraph** handle dead-code, dead-symbol, clone, and hotspot
analysis (already integrated, no changes needed);
**dependency-analysis-gradle-plugin** handles dep hygiene — unused deps,
wrong-config, undeclared transitives, duplicate class files — the gap the
codebase-intel tools do not fill.

WARN-only policy: Forge reflection (registry `@ObjectHolder`,
`@EventBusSubscriber`, string-based resource registration) is a known
false-positive source for dependency-analysis; we run WARN-only and never
`fixDependencies` automatically until a manual triage pass confirms the
findings are real. Rolled out in WARN-only mode; hook integration is gated
on empirical validation on a forge-1.21.x module to catalog known false
positives.

Lane policy: buildSrc classpath (lane 1) + convention plugin (lane 2) +
`scripts/gradle-health.sh` (manual runs, this lane) are in place.
Out-of-band lanes (mc1.12.2 / forge-1.7.10 / forge-1.8.9 / forge-1.16.5 /
forge-1.20.1) are NOT eligible for dependency-analysis due to their Gradle
version constraints (verified Feb 2026; forge-1.8.9 runs Gradle 4.10.3 <
8.11 minimum) — they continue to rely on AFT/Qartez/Codegraph.

## Branch policy & required checks

`main` is the default branch and the single source of truth (promoted
from `integration/m2-monorepo` at `055031b`, 2026-08-06; the
integration branch itself was retired 2026-08-07 per the branch-topology
analysis — the 4-commit `1.21` divergence, all PR #256, is superseded by
the monorepo's `/common` vendor approach and was not ported). `1.21`
and `mc1.12.2` remain as frozen stable aliases (tagged
`archived-m2-complete`): do NOT delete them, do NOT force-push them.
`1.21` keeps its own 3-check protection (`Build (1.21)`, `GameTest
(1.21)`, `YAML Lint`).

Required checks (contract on `main`) are enforced via the gh API — do not edit
branch protection in-repo. The contract is strict (`enforce_admins`, no
force pushes/deletions) with 16 contexts: `YAML Lint`, the `Build
 (common)` / `Build (1.21.x)` / `Build (26.2)` matrix, `Build (mc1.12.2)`,
`E2E (mc1.12.2)` (push-only job — fires on push events only), `GameTest
`(1.21)`, the out-of-band `Build (forge-1.8.9)` / `Build (forge-1.7.10)`
/ `Build (forge-1.16.5)` / `Build (forge-1.20.1)` lanes, `aislop
(M2)`, and `CI Health` (informational -> required, lib-69). CI job names
must match the required-check strings exactly.

Required-check contract count progression (three-lane expansion, deepwork
merge order forge-26.2 → forge-1.8.9 → forge-1.7.10): 12 → 13 after
`Build (26.2)` lands (forge-26.2 lane, PR #310) → 14 after
`Build (forge-1.8.9)` (PR #311) → 15 after `Build (forge-1.7.10)` lands
(PR #312) → 16 after `CI Health` is promoted via
`scripts/gh-api-bump/CI-Health.sh` (lib-69). Each lane is added to the
contract via the `gh-api-bump-<lane>.sh` script (out-of-repo tooling) at
PR-open time.

### Branch protection bump scripts

`scripts/gh-api-bump/{26.2,1.8.9,1.7.10}.sh` are one-shot gh-API scripts that
atomically add a new required-status context to the branch-protection contract
on both `main` and (if it still exists) `integration/m2-monorepo`. They use
`gh api -X PATCH` on `/branches/<branch>/protection/required_status_checks`
(verified empirically — `PUT` returns 404 on the protection subresource).

Each script has guard-protected modes: `--dry-run` (read-only), `--apply`
(write; refuses unless the live contract matches the expected baseline
idempotently), `--verify` (confirms a check-run exists for the lane head).

Use them after a new lane PR lands: run the lane's `gh-api-bump-X.sh
--apply`, then open the next lane PR (the now-required context gates
its CI). For multi-lane expansions with cross-lane required contexts, use
the temporary-relax-then-restore dance documented in FINAL-REPORT.md
under "Post-Merge Deadlock Resolution".

