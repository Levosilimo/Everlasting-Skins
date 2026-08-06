# Everlasting-Skins monorepo

Server-side persistent custom skins on pure Forge servers — no client mod required.

This is the M2 multi-module monorepo (`integration/m2-monorepo` branch). It
unifies every Forge lane of the project under one Gradle root; the 1.12.2 lane
stays on its own wrapper out-of-band.

## Layout

```
settings.gradle.kts          one Gradle root for the Forge line
gradle.properties            shared identity + toolchain (group, mod_*, java.toolchain.version=21)
buildSrc/                    convention plugins (the whole former 1.21 build.gradle)
common/                      version-independent core (--release 8, Java 8 bytecode)
forge-1.21/                  current 1.21 mod (MC 1.21 / Forge 51.0.8)
forge-1.21.1/                point release (MC 1.21.1 / Forge 52.1.16)
forge-1.21.4/                point release (MC 1.21.4 / Forge 54.1.18)
forge-1.21.8/                point release (MC 1.21.8 / Forge 58.1.21)
forge-1.16.5/  forge-1.20.1/ out-of-band legacy lanes (own Gradle wrappers, FG per-lane)
mc1.12.2/                    NOT a subproject — own Gradle 4.10.3 wrapper + FG 2.3.4, Java 8,
                            builds out-of-band, shares ../common as a source dir
```

## Modules

| Module | MC | Forge | FG | Gradle | Toolchain | Status |
|---|---|---|---|---|---|---|
| `:common` | — | — | — | root 9.3.1 | build JDK 21, `--release 8` | canonical shared core |
| `:forge-1.21` | 1.21 | 51.0.8 | 7.x | root 9.3.1 | 21 | SOURCE-COMPLETE |
| `:forge-1.21.1` | 1.21.1 | 52.1.16 | 7.x | root 9.3.1 | 21 | SOURCE-COMPLETE (post-#278) |
| `:forge-1.21.4` | 1.21.4 | 54.1.18 | 7.x | root 9.3.1 | 21 | SOURCE-COMPLETE (post-#280) |
| `:forge-1.21.8` | 1.21.8 | 58.1.21 | 7.x | root 9.3.1 | 21 | SOURCE-COMPLETE (post-#281) |
| `forge-1.16.5/` (not a subproject) | 1.16.5 | 36.2.34 | 5.1.x | own 7.6.4 wrapper | JDK 8 | SOURCE-COMPLETE (post-#274) |
| `forge-1.20.1/` (not a subproject) | 1.20.1 | 47.4.10 | 6.x | own 8.7 wrapper | JDK 21 (17 toolchain) | SOURCE-COMPLETE (post-#273) |
| `mc1.12.2/` (not a subproject) | 1.12.2 | 14.23.5.2847 | 2.3.4 | own 4.10.3 wrapper | JDK 8 | SOURCE-COMPLETE (post-#269) |

`forge-1.16.5` / `forge-1.20.1` are out-of-band per-lane wrappers (own Gradle
wrapper, FG applied per-lane — see AGENTS.md "Legacy lanes"): they are NOT
included in `settings.gradle.kts`, so the root build never configures them.

Every `forge-*` module's `build.gradle.kts` is three lines:
`plugins { id("everlastingskins.forge-module") }`. All build logic lives in
`buildSrc/src/main/kotlin/everlastingskins.forge-module.gradle.kts`;
MC/Forge versions come from each subproject's `gradle.properties`.

## Build

```bash
./gradlew :common:build          # version-independent core (JUnit 5 + jqwik + Mockito)
./gradlew :forge-1.21:build      # a forge module (downloads Minecraft userdev on first run)
./gradlew build                  # everything
```

The 1.12.2 lane is NOT part of this build:

```bash
cd mc1.12.2 && ./gradlew build   # own Gradle 4.10.3 wrapper, Java 8, FG 2.3.4
```

`mc1.12.2/build.gradle` adds `../common/src/main/java` to its main source set,
so the lane compiles the same `:common` sources as the Forge line (single
canonical copy; no JPMS on Java 8).

### Local validation

`act --dryrun --workflows .github/workflows/ci.yml` validates workflow syntax
locally with no Docker. See AGENTS.md → "Local CI validation with act".

## Notes / known state

- **Source layout (post-M2):** `:common` is the single canonical copy of
  shared code; every forge module consumes it unconditionally (the
  `consumeCommon` gate is gone, post-#276), and the out-of-band lanes
  share it as an extra source dir.
  Every lane is SOURCE-COMPLETE: legacy lanes post-#273/#274, the 1.21.x
  point releases post-#278/#280/#281.
- **No mixingradle:** the convention plugin applies mixin annotation
  processing + jar-manifest attributes only (Lane C). Enforced by the
  `verifyNoMixin` gate in `buildSrc/` (`no-mixin.gradle.kts`, ported from the
  parent's `common/build-logic`), which fails the build on any Mixin usage.
- **CI:** `.github/workflows/ci.yml` is a per-module matrix (PR #260,
  extended by #277): lint-yaml, then `build` over `:common` + the four
  1.21.x modules, out-of-band builds for mc1.12.2 / forge-1.16.5 /
  forge-1.20.1 (own wrappers), and the `E2E (mc1.12.2)` required-check
  stub. `publish.yml` gained dedicated `publish-mc1_16_5` /
  `publish-mc1_20_1` jobs in #277, with the `mc1.16.5-v*` /
  `mc1.20.1-v*` tag triggers uncommented.
- **Artifact naming:** `everlastingskins-<mc>` (was `EverlastingSkins-<mc>`).
- `mc1.12.2/` is imported from the parent checkout's history and builds
  out-of-band with its own wrapper (Gradle 4.10.3 + FG 2.3.4 + Java 8). Its
  main source set shares `../common/src/main/java`; overlapping lane copies
  were deleted at import, so `:common` is canonical. Its 514 unit tests pass
  (`cd mc1.12.2 && JAVA_HOME=<jdk8> ./gradlew test`).

## Recently merged (M2 campaign)

- **#257** — initial `forge-1.16.5` subproject (+ lib-35 first shims).
- **#258** — `verifyNoMixin` build gate ported into `buildSrc/`.
- **#259** — `run-gametest-local.sh` paths corrected for the multi-module layout.
- **#260** — `ci.yml` + `publish.yml` reworked to a per-module matrix.
- **#261** — initial `forge-1.20.1` subproject.
- **#263** — `forge-1.21` compile compat restored with Forge 51.0.8.
- **#264** — `forge-1.21` test/gametest sources downgraded for Forge 51.0.8.
- **#265** — `forge-1.21` runtime blockers resolved (incl. JPMS split-package).
- **#266** — docs sync (README / AGENTS / CHANGELOG) with the M2 campaign state.
- **#267** — FG 5.x/6.x lane separation: 1.16.5 / 1.20.1 moved to out-of-band
  per-lane wrappers (own Gradle + FG per lane).
- **#268** — dedup of `forge-1.21` / `forge-1.16.5` against `:common`;
  JPMS split-package resolved (Option B1, 12 survivors relocated to
  `forge21.*`); `forge-1.21` consumes `:common` like the other modules.
- **#269** — feat(monorepo): integrate mc1.12.2 lane + source-dir share
  `:common` (#269) — 162 files, 514 tests pass; mc1.12.2 now lives in the
  monorepo as per-lane wrapper directory (SOURCE-COMPLETE).
- **#270** — `REPOSITORY-STRUCTURE.md` added; standalone parent checkout
  documented as archived (tags on the standalone history).
- **#271** — docs wart fixes (stale `consumeCommon` docs; #269 added to the
  merged list).
- **#272** — P0: `forge-1.16.5` compiles on Java 8 again (Java-16 syntax
  downgraded, duplicate `SkinUtils` + vendored httpclient jar dropped).
- **#273** — `forge-1.20.1` main source carried over from `forge-1.21`
  (SOURCE-COMPLETE).
- **#274** — `forge-1.16.5` SOURCE-COMPLETE (version-shape errors fixed for
  Java 8).
- **#275** — docs sync: README + CHANGELOG reflect the post-M2
  SOURCE-COMPLETE state (modules Status column, post-#270/#274 markers).
- **#276** — `consumeCommon` gate removed: every forge-* subproject
  consumes `:common` unconditionally.
- **#277** — CI: `Build (forge-1.16.5)` / `Build (forge-1.20.1)` jobs +
  dedicated `publish-mc1_16_5` / `publish-mc1_20_1` jobs;
  `mc1.16.5-v*` / `mc1.20.1-v*` tag triggers uncommented.
- **#278** — `forge-1.21.1` main source carried over from `forge-1.21`
  (SOURCE-COMPLETE).
- **#279** — docs + pack format: CHANGELOG entries backfilled, forge-1.21
  `pack.mcmeta` bumped to pack_format 34, stale README CI note fixed.
- **#280** — `forge-1.21.4` main source carried over from `forge-1.21`
  (SOURCE-COMPLETE).
- **#281** — `forge-1.21.8` main source carried over from `forge-1.21`
  (SOURCE-COMPLETE).
