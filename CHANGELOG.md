# Changelog

## M2 (monorepo) — unreleased

### forge-26.2 lane (Minecraft 26.2, Forge 65.0.9, Java 25)

Add the `forge-26.2` lane (in-root): MC 26.2 / Forge 65.0.9 / Java 25
toolchain, unobfuscated mappings (`minecraft.unobfuscated=true` gates the
convention's mappings() call), EventBus 7 + module-local
`eventbus-validator:7.0.5` AP, and the data-driven GameTest scaffold
(#292 pattern, 35 cases green). Tag prefix `mc26.2-v*` (colloquial '26.2',
matching the Forge 65.x line naming). CI gains a `Build (26.2)` matrix
cell (JDK 25) and a publish matrix entry; the required-check contract
goes 12 → 13.

### Mainline promotion (2026-08-06)

`integration/m2-monorepo` promoted to the repo's new default branch
`main` (Option A): branch protection applied to `integration/m2-monorepo`
and `main` (strict, `enforce_admins`, no force pushes/deletions, 12
required checks — the full m2 ci.yml contract), `main` created at
`055031b` from the m2 HEAD, default branch switched via
`gh repo edit --default-branch main`. `1.21` stays as a frozen stable
alias (tagged `archived-m2-complete`); its 4 PR #256 commits (e3e6531,
8099ede, 02e2020, 1bb09d0) are superseded by the monorepo's `/common`
vendor approach and were not ported. `1.21` and `mc1.12.2` retain their
existing branch protection and are not deleted.

### Missing CHANGELOG entries backfilled

- **#266** — docs: sync monorepo README/AGENTS/CHANGELOG + add
  common/AGENTS.md + supersede forge-1.21/AGENTS.md
- **#275** — docs: sync README + CHANGELOG to reflect post-M2
  SOURCE-COMPLETE state (modules Status column, post-#270/#274 markers)
- **#277** — ci: add Build (forge-1.16.5) + Build (forge-1.20.1) +
  dedicated publish-mc1_16_5/publish-mc1_20_1 jobs; uncomment
  mc1.16.5-v*/mc1.20.1-v* tag triggers

### #276 — consumeCommon gate removed

The `consumeCommon=false` opt-out gate in
`buildSrc/.../everlastingskins.forge-module.gradle.kts` is gone: every
forge-* subproject now consumes `:common` unconditionally via
`implementation(project(":common"))`. The gate was a safety valve for
forge-1.21's JPMS split-package (#265) and became dead after the Option B1
relocation to `forge21.*` (#268) resolved the conflict — no module ever
sets `consumeCommon=false`. Docs updated (root AGENTS.md Rule 6,
common/AGENTS.md, README.md).

### #267 — FG lane separation: forge-1.16.5 / forge-1.20.1 go out-of-band

ForgeGradle 5.1.x hard-rejects Gradle 8.0+ and ForgeGradle 6.0.x
hard-rejects Gradle 9.0+ (verified empirically 2026-08-06 against
5.1.77 / 6.0.54), so neither lane can run inside the root's Gradle 9.3.1 —
not as a subproject, not as an included build (included builds run under
the root's Gradle version). The lanes were split into standalone builds
with their own wrappers and FG on their own classpath:

- `forge-1.16.5/` — Gradle 7.6.4 (run on Java 8), ForgeGradle 5.1.77,
  Java 8 bytecode, official Mojang mappings.
- `forge-1.20.1/` — Gradle 8.7 (run on Java 21, Java 17 toolchain via
  foojay), ForgeGradle 6.0.54, official Mojang mappings.
- Both consume `:common` by source-dir sharing and inline the no-mixin
gate; the root's `settings.gradle.kts` no longer includes them. Build
from the lane dir: `cd forge-1.16.5 && ./gradlew build`.
- Deleted: the `everlastingskins.fg5-forge-module`,
  `everlastingskins.fg6-forge-module` and unused
  `everlastingskins.java8-forge-module` buildSrc convention plugins
  (the lanes can no longer use buildSrc).
- Follow-ups: the actual userdev pipeline runs
  (prepareRuns/runClient/runServer, reobfJar) — configuration now uses
  the real FG 5.1/6.0 for the first time. CI wiring for the lane
  wrappers is done (dedicated Build + publish jobs).

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

### M2 completion: SOURCE-COMPLETE (PRs #270–#274)

- **#270 — REPOSITORY-STRUCTURE.md + standalone archival tags:** new doc
  explaining the one-root-plus-out-of-band-lanes layout; the standalone
  parent checkout is documented as archived (its lanes folded in here).
- **#271 — docs wart fixes:** stale `consumeCommon` docs corrected
  (post-#268), #269 added to the README merged list.
- **#272 — P0 forge-1.16.5 compile fix:** Java-16 syntax (instanceof
  patterns, records, `List.of`) downgraded to Java 8; duplicate `SkinUtils`
  and the vendored httpclient-4.5.13.jar dropped; build script adjusted.
  The lane compiles on Java 8 again.
- **#273 — forge-1.20.1 source carry-over:** full main source brought over
  from `forge-1.21` (bindings, lang files, inlined no-mixin gate) — the
  lane is SOURCE-COMPLETE.
- **#274 — forge-1.16.5 SOURCE-COMPLETE:** version-shape errors fixed for
  Java 8 (e.g. `List.of`/`Map.of` → Java-8 equivalents, lambdas kept Java-8
  compatible); stray `endpoints.properties`, `mixins.json` and
  `default-skin.properties` dropped; `JavaHttpClient` removed. The lane is
  SOURCE-COMPLETE.

### 1.21.x point-release source carry-over

- **#278** — feat(monorepo): forge-1.21.1 main source carry-over (Forge
  52.1.16, 0 Java changes; 34/34 gametests pass)
- **#279** — docs: backfill CHANGELOG entries + bump forge-1.21
  pack.mcmeta to pack_format 34 + fix stale README CI note
- **#280** — feat(monorepo): forge-1.21.4 main source carry-over (Forge
  54.1.18, 2 API fixes; 34/34 gametests pass)
- **#281** — feat(monorepo): forge-1.21.8 main source carry-over (Forge
  58.1.21, EventBus 7 migration; gametest dropped per MC 1.21.5 overhaul;
  215 tests pass). Gametest coverage was restored by #292 below.
- **#292** — feat(gametest): forge-1.21.8 GameTest re-introduced (post-#281)
  using the MC 1.21.5+ data-driven test-instance framework
  (`minecraft:test_function` / `test_instance` / `test_environment`
  registries; `@GameTestNamespace` + `@GameTest` on a JPMS-isolated
  `everlastingskins_gametest` mod). 34 skin-pipeline tests ported from
  forge-1.21 + vanilla builtin `always_pass` = 35/35 required tests pass.

### Known follow-ups (next PRs)

- Run the lane userdev pipelines end-to-end on the out-of-band
  1.16.5 / 1.20.1 wrappers (prepareRuns/runClient/runServer, reobfJar).
