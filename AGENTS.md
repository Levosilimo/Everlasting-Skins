# AGENTS.md — multi-module workflow

## Layout & build

One Gradle root (9.3.1) for the Forge line. Modules:

- `:common` — version-independent core, `--release 8`. NEVER add a forge
  binding here; never raise the release level. Consumer of last resort:
  every `forge-*` module and (future) the 1.12.2 lane.
- `:forge-1.21`, `:forge-1.21.1`, `:forge-1.21.4`, `:forge-1.21.8` —
  thin binding layers applying `everlastingskins.forge-module`; MC/Forge
  versions live in each subproject's `gradle.properties`.
- `mc1.12.2/` — NOT a subproject (FG 2.3.4 needs Gradle 4.x + Java 8). It
  stays on its own wrapper, builds out-of-band, consumes `:common` via
  filePath when ported.

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

Not part of this Gradle root (lib-34 lane separation, PR #2xx): ForgeGradle
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
