# AGENTS.md — multi-module workflow

## Layout & build

One Gradle root (9.3.1) for the Forge line. Modules:

- `:common` — version-independent core, `--release 8`. NEVER add a forge
  binding here; never raise the release level. Consumer of last resort:
  every `forge-*` module and the mc1.12.2 lane.
- `:forge-1.21`, `:forge-1.21.1`, `:forge-1.21.4`, `:forge-1.21.8`, `:forge-26.1`, `:forge-26.2` —
  thin binding layers applying `everlastingskins.forge-module`; MC/Forge
  versions live in each subproject's `gradle.properties`. forge-26.1 and
  forge-26.2 are the newest 26.x lines (MC 26.1 / Forge 62.0.9 / Java 25 /
  unobfuscated MC / EventBus 7.0.1 and MC 26.2 / Forge 65.0.9 / Java 25 /
  unobfuscated MC / EventBus 7; FG 7.0.17 on the buildSrc classpath;
  `minecraft.unobfuscated=true` gates the convention's mappings() call).
- `mc1.12.2/` — NOT a subproject (FG 2.3.4 needs Gradle 4.x + Java 8). It
  stays on its own wrapper (4.10.3), builds out-of-band, and consumes `:common`
  via a source-dir share (`srcDir '../common/src/main/java'` in its
  `build.gradle`). Classes present in both `src/main/java` and `:common` were
  deleted from the lane at import — `:common` is canonical for every shared
  class.

## Convention plugins (`buildSrc/src/main/kotlin/`)

- `everlastingskins.forge-module.gradle.kts` — FG 7.x (consumed at 7.0.17)
  + parameterized toolchain (Java 21 for 1.21.x, Java 25 for 26.x via
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

## Legacy lanes (forge-1.5.2 / forge-1.6.4 / forge-1.4.7 / mc1.12.2 / forge-1.7.10 / forge-1.8.9 / forge-1.16.5 / forge-1.20.1 / forge-1.18.2 / forge-1.10.2) — out-of-band

Not part of this Gradle root (lib-34 lane separation, PR #267): ForgeGradle
5.1.x rejects Gradle 8.0+ and ForgeGradle 6.0.x rejects Gradle 9.0+, so
neither can run inside the root's Gradle 9.3.1 (verified empirically
2026-08-06 against 5.1.77 / 6.0.54). The 1.7.10 and 1.8.9 lanes predate
even FG 5.1.x and use Gradle wrappers pinned at 4.4.1 / 4.10.3 respectively.
Each lane is its own build with its own wrapper and FG version:

- `forge-1.5.2/` — Gradle 4.4.1 (run on Java 8), vendored SpecialSource
  1.7.4 deobf harness (NO ForgeGradle — FG 1.2.11's support envelope does
  not reach 1.5.2 and no MCP 1.5.2 channel exists anywhere live; see
  plan-forge-1-5-2-port.json). Remap-only pipeline: server.jar
  (sha1-pinned, launcher.mojang.com — single host) + universal.zip
  (md5-pinned, forge maven — the .zip variant is the jar) →
  merged-deobf.jar dev classpath; reobf deobf→obf + assertNameDomain
  self-check on the shipped jar (production 1.5.2 is obf-named).
  FML 5.2 `@Mod`/`@Mod.EventHandler` (cpw.mods.fml) +
  `NetworkRegistry.instance().registerChannel(...)`, no GameProfile
  (pre-UUID). Consumes `:common` by source-dir share; dep-analysis NOT
  eligible (Gradle 4.x < 8.11 minimum).
- `forge-1.6.4/` — Gradle 4.4.1 (run on Java 8), vendored SpecialSource
  1.7.4 deobf harness (NO ForgeGradle — GTNH FG 1.2.11 rejects
  forge 9.11.1.1345 at configuration time and no MCP 1.6.4 channel
  exists anywhere live; see plan-forge-1-6-4-vendored-harness.json).
  Remap-only pipeline: server.jar (sha1-pinned, piston-data) +
  universal.jar (md5-pinned, forge maven) → merged-deobf.jar dev
  classpath; reobf deobf→obf + assertNameDomain self-check on the
  shipped jar (production 1.6.4 is obf-named). `@Mod`/`@Mod.EventHandler`
  (cpw.mods.fml) + `NetworkRegistry.instance().registerChannel(...)`,
  no GameProfile (pre-UUID). Consumes `:common` by source-dir share;
  dep-analysis NOT eligible (Gradle 4.x < 8.11 minimum).
- `forge-1.4.7/` — Gradle 4.4.1 (run on Java 8), vendored SpecialSource
  1.7.4 deobf harness (NO ForgeGradle — FG 1.2.11's support envelope does
  not reach forge major 6; see plan-forge-1-4-7-port.json).
  Remap-only pipeline: server.jar (sha1-pinned, piston-data) +
  universal.zip (md5-pinned, forge maven — the .zip variant is the jar;
  universal.jar 404s) → merged-deobf.jar dev classpath (universal wins
  collisions); reobf deobf→obf + assertNameDomain self-check on the
  shipped jar (production 1.4.7 is obf-named). FML 4.7
  `@Mod`/`@Mod.Init` (cpw.mods.fml) +
  `NetworkRegistry.instance().registerChannel(...)`, no GameProfile
  (pre-UUID). Consumes `:common` by source-dir share; dep-analysis NOT
  eligible (Gradle 4.x < 8.11 minimum).
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
- `forge-1.10.2/` — Gradle 4.10.3 (run on Java 8), ForgeGradle 2.2.5,
  MCP stable_29. Build with `cd forge-1.10.2 && JAVA_HOME=<jdk8> ./gradlew
  build`; consumes `:common` via source-dir share; inline no-mixin gate
  (heavier variant — scans build files too); dep-analysis NOT eligible
  (Gradle 4.x < 8.11 minimum).
- `forge-1.16.5/` — Gradle 7.6.4 (run on Java 8), ForgeGradle 5.1.77.
- `forge-1.20.1/` — Gradle 8.14 (run on Java 21, Java 17 toolchain via
  foojay), ForgeGradle 6.0.54.
- `forge-1.18.2/` — Gradle 8.14 (run on Java 21, Java 17 toolchain via
  foojay), ForgeGradle 6.0.54, official Mojang mappings (MCP does not
  exist as of 1.17).

All ten consume `:common` by source-dir sharing (they cannot use
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

### Reading vendored-lane bytecode (1.4.7 / 1.5.2 / 1.6.4)

Read the PRE-DECOMPILED tree at `~/.gradle/everlastingskins-vendored/<era>/decompiled/`
(one `<name>.src/` per jar: client/merged/server/universal deobf + raw vendored)
FIRST; `scripts/decompile.sh` (Vineflower 1.12.0, sha1-pinned) is only for uncached
jars. `scripts/bytecode.sh` (ASM 9.10.1 Textifier; `--offsets` prints numeric byte
offsets) is the bytecode-precision tool — offsets/owners/flags/descriptors; `javap -c -p`
for quick checks; `javap -v` only for raw constant-pool dumps. Resolve obf names via
`<lane>/build/deobf/{obf-to-deobf,deobf-to-obf}.srg` + `<lane>/build/mcp*/{methods,fields}.csv`.
Never decompile raw obf jars for reading — use the deobf jars' decompiled output.

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
   forge-26.1 follows the same 26.x line pattern — own tag prefix
   `mc26.1-v*` (matching the Forge 62.x line naming), mod_version
   2.1.0-beta.1.
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
./gradlew :forge-26.1:build verifyNoMixin  # 26.1 lane (Java 25 toolchain, unobfuscated MC, FG 7.0.17, EventBus 7.0.1)
./gradlew :forge-26.2:build    # 26.2 lane (Java 25 toolchain, unobfuscated MC, FG 7.0.17)
./gradlew build                # whole Forge line
bash scripts/config-order-gate.sh  # config-order gate (CI-mirror probe: CoD + no-config-cache :forge-1.21:jar)
bash scripts/config-order-gate-test.sh  # gate self-test (proves the gate catches the regression forms)
bash scripts/gradle-health.sh  # dep-hygiene sweep (manual; per-consumer :projectHealth; graduated lanes fail on duplicate-class)
```

CI (`ci.yml`) is a per-module matrix (PR #260): lint-yaml → `build` over
`:common` + the four 1.21.x modules + forge-26.2 (`Build (26.2)`, Java 25),
plus the out-of-band mc1.12.2 build, `E2E (mc1.12.2)` (P1-6 LIVE — no
longer a stub: mc1.12.2/test-infrastructure/run-e2e.sh builds the lane,
boots a real Forge 14.23.5.2847 server with the mod, launches a
HeadlessMC client (TestPlayer) under xvfb, and asserts on the server
log that the server booted, the client joined, and the FML handshake
mod-list line names everlastingskins; PR #376), a real-client E2E program
for the pre-1.8 lanes is in progress (master plan
`.slim/deepwork/real-client-e2e-plan.md`, Slice 1 = 1.6.4) — no CI job
exists until it lands, and out-of-band
`Build (forge-1.8.9)` / `Build (forge-1.16.5)` / `Build (forge-1.20.1)` /
`Build (forge-1.7.10)` / `Build (forge-1.18.2)` / `Build (forge-1.10.2)` /
`Build (forge-1.6.4)` / `Build (forge-1.5.2)` / `Build (forge-1.4.7)`
lanes (own wrappers, JDK 8 / JDK 8 / JDK 21 / JDK 8 / JDK 21 / JDK 8 / JDK 8 / JDK 8 / JDK 8).
A `Vendored harness diff-guard` job (lib-12) fails CI when a
vendored-harness lane stops applying the shared harness script
(harness/specialsource-harness.gradle, Option B graduation — extracted from
forge-1.6.4/build.gradle post-PR-#374), re-defines a harness task locally,
or drops a required harnessConfig key (see
scripts/ci-vendored-harness-diff-guard.sh). `Build (forge-1.4.7)` is
live in the matrix (PR #404). Treat the matrix as
authoritative for what is buildable in CI.

Source status: every lane is SOURCE-COMPLETE — forge-1.16.5 (post-#274),
forge-1.20.1 (post-#273), the 1.21.1 / 1.21.4 / 1.21.8 point releases
(post-#278 / #280 / #281), forge-26.2 (Java 25, unobfuscated MC,
EventBus 7; data-driven GameTest green), forge-26.1 (Java 25, unobfuscated
MC, EventBus 7.0.1), forge-1.7.10 (GTNH FG 1.2.11 +
MCP stable_12; 48 unit tests, JUnit 4), forge-1.8.9 (post-#311,
Gradle 4.10.3 / Java 8 / MCP stable_20), and forge-1.18.2 (post-#364,
Gradle 8.14 / Java 17 toolchain / official Mojang mappings), and
forge-1.10.2 (Gradle 4.10.3 / Java 8 / MCP stable_29), and forge-1.6.4
(Gradle 4.4.1 / Java 8 / vendored SpecialSource 1.7.4 deobf harness,
MCP 8.11 conf; 40 unit tests, JUnit 4), and forge-1.5.2 (Gradle 4.4.1 /
Java 8 / vendored SpecialSource 1.7.4 deobf harness, MCP 7.51 conf), and
forge-1.4.7 (Gradle 4.4.1 / Java 8 / vendored SpecialSource 1.7.4 deobf
harness, MCP 7.26a conf).

### Fail-fast hooks

Tiered local gates mirroring CI (see `lefthook.yml`):

- **pre-commit** (<30s, sequential): aislop staged scan → test-count gate →
  `verifyNoMixin` → offline parallel compile.
- **pre-push** (5-10 min, heavy): full unit test suite → GameTest (1.21) via
  `forge-1.21/test-infrastructure/run-gametest-local.sh` → `aislop ci
  --changes` (mirrors CI's `aislop (M2)` job) → config-order gate
  (`scripts/config-order-gate.sh`: structural guard against bare
  `:common.sourceSets` bundling forms in the convention + the CI-mirror
  probe `--configure-on-demand --no-configuration-cache :forge-1.21:jar`,
  which reproduces the take-1/2 WorkValidationException locally). Skip once
  with `git push --no-verify`.
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

Lefthook is the active hook manager (Go binary, parallel pre-push; see
`lefthook.yml`). After cloning, run `bash scripts/setup-hooks.sh` to
bootstrap Lefthook.

Fresh clones bootstrap lefthook with `bash scripts/setup-hooks.sh` (idempotent;
verifies the binary + config, then runs `lefthook install --reset-hooks-path`
to write `.git/hooks/` shims and clear `core.hooksPath`).

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
positives. Duplicate-class is now graduated per-lane (P2-6): it is a
zero-false-positive category on this codebase, so a graduated lane fails
`projectHealth` (and therefore `check`/`build`) on a real duplicate — see
"P2-6 dependency-analysis graduation" below. The reflection-FP categories
(unused deps, used-transitive "declare directly") remain WARN-only.

Lane policy: buildSrc classpath (lane 1) + convention plugin (lane 2) +
`scripts/gradle-health.sh` (manual runs, this lane) are in place.
Out-of-band lanes (mc1.12.2 / forge-1.7.10 / forge-1.8.9 / forge-1.10.2 /
forge-1.16.5 / forge-1.20.1 / forge-1.18.2 / forge-1.6.4 / forge-1.5.2) are NOT eligible for
dependency-analysis: the
Java 8 lanes sit below the plugin's Gradle 8.11 minimum (verified Feb 2026;
forge-1.8.9 / forge-1.10.2 run Gradle 4.10.3), and the FG 6.0.x lanes
(forge-1.20.1 / forge-1.18.2) are structurally outside the root build —
gradle-health.sh
iterates root-build consumers only. They continue to rely on
AFT/Qartez/Codegraph.

### P2-6 dependency-analysis graduation

Duplicate-class dep-analysis is now graduated per-lane (P2-6). A graduated lane
sets `depAnalysis.graduateDuplicateClass=true` in its `gradle.properties`; the
convention gates three things on that single property:

- `onDuplicateClassWarnings { severity("fail") }` — precedence over the WARN-forever `onAny`;
- the `projectsEvaluated` wiring of `check -> projectHealth` (so a finding fails `build` and the CI `Build (X)` cell);
- the test-variant #960 edges (`ClassListExploderTask` / `AbiAnalysisTask -> dependsOn processTestResources`).

Graduated lanes (duplicate-class is a zero-false-positive category here):
forge-1.21, forge-1.21.1, forge-1.21.4, forge-1.21.8, forge-26.2,
forge-26.1. `:common`
stays WARN-forever by design (aggregate-jar false positives, no Forge runtime)
and must never set the property. No other lane should graduate: forge-1.18.2
did NOT set the property (lib-9 verified on disk), and the out-of-band lanes
(mc1.12.2 / forge-1.7.10 / forge-1.8.9 / forge-1.10.2 / forge-1.16.5 /
forge-1.20.1 / forge-1.18.2 / forge-1.6.4 / forge-1.5.2 /
forge-1.4.7) sit below the
plugin's Gradle 8.11 minimum (see the lane-policy paragraph above).

Rollback: flip `depAnalysis.graduateDuplicateClass` back to `false` (or delete
it) — instantly reverts BOTH the severity change and the check-wiring with no
rebuild and no branch-protection change. Do not add a dedicated ci-health
required check.

Contributor guide — graduating a new lane: set the property, then run
`./gradlew --offline :<lane>:projectHealth` and confirm duplicate-class is 0
(zero-FP category, so any finding is a real duplicate). A lane that sets the
property without being zero-FP will fail its `Build (X)` cell.

The tooling gap note: `scripts/gradle-health.sh` CONSUMERS is enumerated (not
globbed) — every new forge module must be appended there (see the script header).

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
force pushes/deletions) with 22 contexts: `YAML Lint`, the `Build
 (common)` / `Build (1.21.x)` / `Build (26.2)` / `Build (26.1)` matrix,
`Build (mc1.12.2)`,
`E2E (mc1.12.2)` (push-only job — fires on push events only), `GameTest
`(1.21)`, the out-of-band `Build (forge-1.8.9)` / `Build (forge-1.7.10)`
/ `Build (forge-1.16.5)` / `Build (forge-1.20.1)` / `Build (forge-1.18.2)`
/ `Build (forge-1.10.2)` / `Build (forge-1.6.4)` / `Build (forge-1.5.2)`
/ `Build (forge-1.4.7)` lanes, `aislop
(M2)`, and `CI Health` (informational -> required, lib-69). CI job names
must match the required-check strings exactly.

### Known GameTest false-positives

Mavenizer cache-miss failures are infrastructure noise, NOT real test
failures (lib-58). The signature is:

```
java.lang.Exception: Cache miss! Stacktrace for Information Only
```

Observed across the forge-1.21, forge-1.21.4, and forge-26.2 GameTest lanes;
the same signature was previously classified as noise for forge-1.21.4.
Triage rule: re-run a red GameTest check before investigating — only treat
it as a real failure when an actual assertion/text failure (or a non-
Mavenizer stack trace) is present. Mavenizer cache-miss noise alone must
not block a merge or trigger a bug hunt.

Required-check contract count progression (legacy-lane expansion): 12 → 13
after `Build (26.2)` lands (forge-26.2 lane, PR #310) → 14 after
`Build (forge-1.8.9)` (PR #311) → 15 after `Build (forge-1.7.10)` lands
(PR #312) → 16 after `CI Health` is promoted via
`scripts/gh-api-bump/CI-Health.sh` (lib-69) → 17 after `Build (26.1)`
(forge-26.1 lane, gh-api-bump/26.1.sh) → 18 after `Build (forge-1.18.2)`
(gh-api-bump/1.18.2.sh) → 19 after `Build (forge-1.10.2)`
(gh-api-bump/1.10.2.sh) → 20 after `Build (forge-1.6.4)`
(gh-api-bump/1.6.4.sh) → 21 after `Build (forge-1.5.2)`
(gh-api-bump/1.5.2.sh) → 22 after `Build (forge-1.4.7)`
(gh-api-bump/1.4.7.sh). Each lane is added to the
contract via the `gh-api-bump-<lane>.sh` script (out-of-repo tooling) at
PR-open time.

The contract now stands at 22: `Build (forge-1.4.7)` landed as the 22nd
context via gh-api-bump/1.4.7.sh (21→22, after gh-api-bump/1.5.2.sh had
applied 20→21).

### Branch protection bump scripts

`scripts/gh-api-bump/{26.2,26.1,1.8.9,1.7.10,1.18.2,1.10.2,1.6.4,1.5.2,1.4.7}.sh`
are one-shot gh-API scripts that
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

### Merge automation

`scripts/gh-merge-bot.sh` — bounded single-pass merge bot (--verify state
machine: DONE/READY_TO_MERGE/STALE/FAILED/BLOCKED/PENDING; update-branch
on STALE; timeout-bounded `gh pr checks --watch --fail-fast`;
fire-and-forget `gh pr merge --auto`). Never self-retries; the caller's
tool timeout must exceed --timeout (default 600s). Exit-code contract in
the script header. `--verify` is read-only by default; the OPT-IN
`--verify --fix-stale` triggers exactly ONE `gh pr update-branch` + ONE
re-verify on a STALE verdict (belt-and-suspenders manual fallback, never
a loop).

`.github/workflows/auto-update-pr-branches.yml` (`Auto-update PR branches`)
is the STANDING BEHIND-resolution owner: on every push to `main` (primary),
plus a `*/30 * * * *` schedule safety net and manual dispatch, it runs
`gh pr update-branch` on exactly the open, non-draft, auto-merge-armed PRs
whose `mergeStateStatus` is BEHIND. GitHub's auto-merge never updates a
behind branch — it only fires once the PR is mergeable — so under strict
protection ("require branches to be up to date") a BEHIND PR stalls
forever without this. HARD CONSTRAINT: the workflow authenticates with the
fine-grained `EVERLASTINGSKINS_PAT` (pull-requests: write, contents:
read), never `GITHUB_TOKEN` — GITHUB_TOKEN-authenticated pushes do not
re-trigger workflows (recursive-run prevention), so ci.yml would not
re-run on the updated head and auto-merge would stall on stale required
checks. (The `EVERLASTINGSKINS_PAT` secret must exist for the workflow to
function; `permissions: {}` leaves GITHUB_TOKEN with no scopes either
way.) Churn tradeoff is accepted: each update re-runs the required-check
matrix, but the workflow touches only auto-merge-armed non-draft BEHIND
PRs, so churn stays proportional to merge traffic. Strict branch
protection is kept as-is — the workflow exists to make strictness +
auto-merge work together, not to relax either.

## Merge / CI-wait policy (no-wait rule)

Implementation fixers NEVER wait on CI. Their terminal state is: edit → local verify → commit → push → open PR → return the PR number. Zero foreground blocking — no `gh pr checks --watch`, no sleep loops, no `timeout`-wrapped watches, ever, in an implementation lane. The orchestrator's re-dispatch at natural wave boundaries is the only wait mechanism.

Merging is a separate concern, handled fire-and-forget: arm `gh pr merge <PR> --squash --auto` immediately (GitHub waits only on *required* checks; informational jobs like CodeQL never block auto-merge) and return. A later out-of-band `scripts/gh-merge-bot.sh <PR> --verify` confirms DONE. No full-list `gh pr checks --watch` anywhere, ever. Only on a REFUSED merge does anyone read required-check state — by name from branch protection — re-run a Mavenizer-noise failure once, re-arm auto-merge, return. A diagnostic watch, when unavoidable, must use `gh pr checks <N> --watch --required` and be bounded ≤180s.

### Publishing workflow

`.github/workflows/publish.yml` (`Publish`) is the release pipeline — it is
tag-triggered (`on: push: tags:`), NOT branch-triggered. Tag prefixes route to
their module: `mc1.21-v*` / `mc1.21.1-v*` / `mc1.21.4-v*` / `mc1.21.8-v*`
(the four 1.21.x point releases), `mc26.2-v*` (colloquial '26.2', dropping
the leading '1.' — the Forge 65.x line naming), plus the out-of-band lanes
`mc1.12.2-v*` / `mc1.8.9-v*` / `mc1.10.2-v*` / `mc1.16.5-v*` /
`mc1.20.1-v*` / `mc1.18.2-v*` / `mc1.7.10-v*` / `mc1.6.4-v*` /
`mc1.5.2-v*` / `mc1.4.7-v*`.
NeoForge is intentionally absent (no `mcneoforge` lane).

Each publish job is a per-prefix matrix entry (`prefix` → Gradle subproject →
game version → Java). Since 2026-08-14 the pipeline is **GitHub Releases
only** (user decision — Modrinth + CurseForge publishing fully removed):
each lane builds with `clean` (prevents stale-jar accumulation in
`build/libs`, the root cause of the old duplicate "additional files"
uploads), then runs `gh release create "$GITHUB_REF_NAME"
<lane>/build/libs/everlastingskins-<mc>-*.jar --title ... --generate-notes`
with `env: GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}` — no Modrinth/CurseForge
ids or tokens anywhere, and no mc-publish for any platform. The jar glob
matches the SINGLE primary jar (every lane's archivesBaseName/archivesName is
`everlastingskins-<mcVersion>`); `--generate-notes` supplies the changelog
(the old no-changelog-input → empty changelog failure mode is gone). The
malformed `CURSEFORGE_TOKEN` and the now-unused `MODRINTH_TOKEN` repo secrets
were deleted; `GITHUB_TOKEN` needs no secret. Two hard-won constraints from
the #356 fix: the tag gate lives on the **steps**
(`if: startsWith(github.ref_name, matrix.prefix)`), not on
`jobs.<job_id>.if` — GitHub's context-availability table excludes `matrix`
from job-level `if`, and a matrix ref there rejects the whole file at
registration; and the workflow declares top-level
`permissions: contents: write` (the releases:write fix) so the `gh release
create` step may create GitHub Releases. Change it only with the GH-only
reality in mind; a broken publish.yml blocks ALL tag releases, not just one
lane.
