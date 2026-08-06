# AGENTS.md — multi-module workflow

## Layout & build

One Gradle root (9.3.1) for the Forge line. Modules:

- `:common` — version-independent core, `--release 8`. NEVER add a forge
  binding here; never raise the release level. Consumer of last resort:
  every `forge-*` module and the mc1.12.2 lane.
- `:forge-1.21`, `:forge-1.21.1`, `:forge-1.21.4`, `:forge-1.21.8` —
  thin binding layers applying `everlastingskins.forge-module`; MC/Forge
  versions live in each subproject's `gradle.properties`.
- `mc1.12.2/` — NOT a subproject (FG 2.3.4 needs Gradle 4.x + Java 8). It
  stays on its own wrapper (4.10.3), builds out-of-band, and consumes `:common`
  via a source-dir share (`srcDir '../common/src/main/java'` in its
  `build.gradle`). Classes present in both `src/main/java` and `:common` were
  deleted from the lane at import — `:common` is canonical for every shared
  class.

## Convention plugins (`buildSrc/src/main/kotlin/`)

- `everlastingskins.forge-module.gradle.kts` — FG 7.x + Java 21 toolchain.
  All forge build logic lives here; subproject build scripts are just
  `plugins { id("everlastingskins.forge-module") }`. Parameterization is via
  gradle.properties, never via editing build scripts.
- `no-mixin.gradle.kts` — the Mixin-usage gate (port from the parent's
  `common/build-logic`, M2 step 2). Registers `verifyNoMixin` (fails the
  build on `@Mixin` annotations, mixingradle, the mixin plugin id, `mixin {}`
  blocks, or mixins.json bundling) and wires it into `build`. Applied by
  `everlastingskins.forge-module` and `:common` itself; new forge convention
  plugins must apply it too.

## Legacy lanes (forge-1.16.5 / forge-1.20.1) — out-of-band

Not part of this Gradle root (lib-34 lane separation, PR #267): ForgeGradle
5.1.x rejects Gradle 8.0+ and ForgeGradle 6.0.x rejects Gradle 9.0+, so
neither can run inside the root's Gradle 9.3.1 (verified empirically
2026-08-06 against 5.1.77 / 6.0.54). Each lane is its own build with its
own wrapper and FG version:

- `forge-1.16.5/` — Gradle 7.6.4 (run on Java 8), ForgeGradle 5.1.77.
- `forge-1.20.1/` — Gradle 8.7 (run on Java 21, Java 17 toolchain via
  foojay), ForgeGradle 6.0.54.

Both consume `:common` by source-dir sharing (they cannot use
`project(":common")`), inline their own no-mixin gate, and are built from
their own directory: `cd forge-1.16.5 && ./gradlew build`. The root's
settings.gradle.kts does not include them.

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
   on Minecraft classpaths).
5. **Point-release parity:** keep `forge-1.21.x` gradle.properties versions
   in sync with the tags (`mc1.21.x-v2.1.0-rc1` etc.). Verify against git
   history before changing.
6. **`:common` consumed unconditionally:** every forge-* subproject depends
   on `:common` via `implementation(project(":common"))` in
   buildSrc `everlastingskins.forge-module.gradle.kts`; there is no opt-out.
   Historical context: a `consumeCommon=false` gate existed as a safety
   valve for forge-1.21's JPMS split-package (#265) and was removed after
   the Option B1 relocation to `forge21.*` (#268) resolved the conflict.
   Re-add caveat: if a future forge-* subproject (e.g., forge-1.7.10 /
   forge-1.8.9) requires vendored `:common` copies for tooling reasons,
   re-add the gate to buildSrc `forge-module.gradle.kts` and re-introduce
   the opt-out property. (The dead plugin-era gate was deleted in #267.)
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
./gradlew build                # whole Forge line
```

CI (`ci.yml`) is a per-module matrix (PR #260): lint-yaml → `build` over
`:common` + the four 1.21.x modules, plus the out-of-band mc1.12.2 build,
`E2E (mc1.12.2)` stub, and out-of-band `Build (forge-1.16.5)` /
`Build (forge-1.20.1)` lanes (own wrappers, JDK 8 / JDK 21). Treat the
matrix as authoritative for what is buildable in CI.

Source status: every lane is SOURCE-COMPLETE — forge-1.16.5 (post-#274),
forge-1.20.1 (post-#273), and the 1.21.1 / 1.21.4 / 1.21.8 point releases
(post-#278 / #280 / #281).

## Fail-fast hooks

Tiered local gates mirroring CI (see `.githooks/` and `lefthook.yml`):

- **pre-commit** (<30s, sequential): aislop staged scan → test-count gate →
  `verifyNoMixin` → offline parallel compile. Active via
  `git config core.hooksPath .githooks` (already set in this repo).
- **pre-push** (5-10 min, heavy): full unit test suite → GameTest (1.21) via
  `forge-1.21/test-infrastructure/run-gametest-local.sh` → `aislop ci
  --changes` (mirrors CI's `aislop (M2)` job). Skip once with
  `git push --no-verify`.
- `scripts/test-count-gate.sh` mirrors ci.yml's `@Test >= 150` floor: counts
  `@Test` + `@ParameterizedTest` in `common/src` + `forge-1.21/src`.

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

Validate `ci.yml` changes locally with `act --dryrun` before pushing.

`lefthook.yml` documents the intended lefthook adoption (Go binary, parallel
pre-push); `.githooks/` stays active until lefthook is installed and
`core.hooksPath` unset.

## Required checks (integration/m2-monorepo branch protection)

Enforced via the gh API — do not edit branch protection in-repo. `Build
(forge-1.16.5)` and `Build (forge-1.20.1)` are required status checks on
integration/m2-monorepo, additive to the existing contract (`YAML Lint`,
`Build (common)` / `Build (1.21.x)` matrix, `Build (mc1.12.2)`, `E2E
(mc1.12.2)`, `GameTest (1.21)`, `aislop (M2)`). CI job names must match
the required-check strings exactly.
