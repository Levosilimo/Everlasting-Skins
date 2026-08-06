# Changelog

## M2 (monorepo) — unreleased

### step 2: multi-module scaffold

Stand up the multi-module Gradle monorepo on `integration/m2-monorepo`.

- **Restructure:** repo root now carries `settings.gradle.kts` + `buildSrc/`
  + `gradle.properties`; the former 1.21 project (build files, `src/`,
  `test-infrastructure/`, docs) moved via `git mv` into `forge-1.21/`.
- **Convention plugins (`buildSrc/`):**
  - `everlastingskins.forge-module` — the entire former 1.21 `build.gradle`
    (FG 7.x, Java 21 toolchain, official mappings, all dev runs incl.
    `gameTestServer` netty flags, gametest source set, jacoco, jar manifest,
    mod deps). No mixingradle (Lane C).
  - `everlastingskins.java8-forge-module` — stub for the future
    1.16.5/1.20.1 lanes (Java 8 source, FG 6.x).
- **Subprojects:** `forge-1.21` (MC 1.21 / Forge 51.0.8),
  `forge-1.21.1` (52.1.16), `forge-1.21.4` (54.1.18), `forge-1.21.8`
  (58.1.21) — each a 3-line `build.gradle.kts` + version-carrying
  `gradle.properties`. `common/` included (standalone module copied in).
  `forge-1.16.5` / `forge-1.20.1` reserved as placeholders, not included yet.
- **Wrapper:** Gradle 9.3.1 (already current from the point-release merge),
  foojay toolchain resolver 1.0.0.
- **Docs:** monorepo README (structure), CHANGELOG (this file), AGENTS.md
  (multi-module workflow).
- **Deliberately out of scope for this step:** per-subproject source ports
  (split into binding layer + `:common`), the 1.12.2 lane (own Gradle 4.10.3
  wrapper, out-of-band), and the CI matrix rework.

### Multi-version refactor (PRs #257–#265)

Follow-on campaign after the step-2 scaffold: legacy-lane scaffolds, the
Mixin gate, CI matrix, and forge-1.21 compat fixes.

- **#257 — initial 1.16.5 subproject scaffold (+ lib-35 first shims):**
  `forge-1.16.5/` with `gradle.properties` (MC 1.16.5 / Forge 36.2.34,
  loader `[36,)`) and `everlastingskins.fg5-forge-module` convention
  plugin (Java 8 source, no-mixin applied; FG 5.1.x applied per-subproject
  because buildSrc can carry only one FG version — lib-34).
- **#258 — verifyNoMixin gate into buildSrc/:** `no-mixin.gradle.kts`
  ported from the parent's `common/build-logic`; fails the build on Mixin
  usage, wired into every forge module and `:common`.
- **#259 — run-gametest-local.sh paths fixed** for the multi-module layout.
- **#260 — ci.yml + publish.yml reworked** into a per-module matrix
  (`lint-yaml` → `build` over `:common` + the four 1.21.x modules, plus
  the out-of-band mc1.12.2 build and the `E2E (mc1.12.2)` placeholder
  check).
- **#261 — initial 1.20.1 subproject scaffold:** `forge-1.20.1/` (MC 1.20.1
  / Forge 47.4.10, loader `[47,)`) with `everlastingskins.fg6-forge-module`
  (Java 17 source).
- **#263 — forge-1.21 compile compat restored** with Forge 51.0.8
  (pre-EventBus-7 imports downgrade).
- **#264 — forge-1.21 test/gametest sources downgraded** for Forge 51.0.8.
- **#265 — forge-1.21 runtime blockers resolved** (incl. JPMS split-package:
  `forge-1.21` opts out of `:common` via `consumeCommon=false` until its
  duplicate classes are removed).

### Known follow-ups (next PRs)

- Port `forge-1.21` sources onto `:common` (reconcile the two copies), then
  flip `consumeCommon` back on.
- Port 1.21.1/1.21.4/1.21.8 sources.
- Wire the `minecraft {}` blocks for the 1.16.5 / 1.20.1 lanes (source
  carry-over) and add them to the CI matrix.
- Fix `forge-1.21/src/main/resources/pack.mcmeta` staleness: `pack_format`
  is still 6 (a 1.16.2-era value; 1.21 needs 15+) and the `_comment` is
  stale.
- Clean up the superseded `java8-forge-module` convention plugin stub.
