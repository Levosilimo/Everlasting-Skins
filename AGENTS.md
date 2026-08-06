# AGENTS.md — multi-module workflow

## Layout & build

One Gradle root (9.3.1) for the Forge line. Modules:

- `:common` — version-independent core, `--release 8`. NEVER add a forge
  binding here; never raise the release level. Consumer of last resort:
  every `forge-*` module and (future) the 1.12.2 lane.
- `:forge-1.21`, `:forge-1.21.1`, `:forge-1.21.4`, `:forge-1.21.8` —
  thin binding layers applying `everlastingskins.forge-module`; MC/Forge
  versions live in each subproject's `gradle.properties`.
- `:forge-1.16.5`, `:forge-1.20.1` — legacy-lane scaffolds (included in
  `settings.gradle.kts`), applying `everlastingskins.fg5-forge-module` /
  `everlastingskins.fg6-forge-module`. Scaffold-only: the FG-typed
  `minecraft {}` blocks are not wired until the source carry-over lands.
- `mc1.12.2/` — NOT a subproject (FG 2.3.4 needs Gradle 4.x + Java 8). It
  stays on its own wrapper, builds out-of-band, consumes `:common` via
  filePath when ported.

## Convention plugins (`buildSrc/src/main/kotlin/`)

- `everlastingskins.forge-module.gradle.kts` — FG 7.x + Java 21 toolchain.
  All forge build logic lives here; subproject build scripts are just
  `plugins { id("everlastingskins.forge-module") }`. Parameterization is via
  gradle.properties, never via editing build scripts.
- `everlastingskins.fg5-forge-module.gradle.kts` — legacy-lane scaffolding
  (Java 8 source level/bytecode, publishing, coverage, `:common` dep) for
  `forge-1.16.5`. FG 5.1.x is applied by the subproject's own
  `build.gradle.kts` (buildscript classpath), never from `buildSrc/` —
  buildSrc can carry only one FG version (7.x) on its classpath (lib-34).
- `everlastingskins.fg6-forge-module.gradle.kts` — same scaffolding for
  `forge-1.20.1` (Java 17 source), FG 6.x applied per-subproject.
- `everlastingskins.java8-forge-module.gradle.kts` — superseded stub;
  no subproject applies it (fg5/fg6 split replaced it). Candidate cleanup.
- `no-mixin.gradle.kts` — the Mixin-usage gate (port from the parent's
  `common/build-logic`, M2 step 2). Registers `verifyNoMixin` (fails the
  build on `@Mixin` annotations, mixingradle, the mixin plugin id, `mixin {}`
  blocks, or mixins.json bundling) and wires it into `build`. Applied by
  every forge convention plugin and `:common`; new forge convention plugins
  must apply it too.

## Rules

1. **No source edits in scaffold land.** Ports are separate PRs: move
   version-independent classes into `:common`, keep bindings in the
   `forge-*` module, then delete the moved copy from the module.
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
6. **`consumeCommon` gate:** each forge module opts into the `:common`
   dependency unless `consumeCommon=false` in its `gradle.properties`.
   `forge-1.21` currently sets it `false` — it still ships its own copies of
   every `:common` class, and pulling the jar in would create a JPMS split
   package. When the source port reconciles the duplicates, flip it back.
7. **1.12.2 lane:** never include it in `settings.gradle.kts`. It builds
   out-of-band; changes there are reviewed against the shared `:common`
   contract, not against this build.

## Verification

```bash
./gradlew :common:build        # fast gate after :common changes
./gradlew :forge-1.21:build    # full forge module (slow; downloads userdev)
./gradlew build                # whole Forge line
```

CI (`ci.yml`) is a per-module matrix (PR #260): lint-yaml → `build` over
`:common` + the four 1.21.x modules, plus the out-of-band mc1.12.2 build and
`E2E (mc1.12.2)` placeholder. `forge-1.16.5` / `forge-1.20.1` are not in the
matrix yet. Treat the matrix as authoritative for what is buildable in CI.
