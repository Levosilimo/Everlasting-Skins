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
- `everlastingskins.java8-forge-module.gradle.kts` — stub for 1.16.5/1.20.1
  (Java 8 source, FG 5.1+/6.x). Reconcile its `minecraft {}` block against
  the FG6 API when the port starts.
- `no-mixin.gradle.kts` — the Mixin-usage gate (port from the parent's
  `common/build-logic`, M2 step 2). Registers `verifyNoMixin` (fails the
  build on `@Mixin` annotations, mixingradle, the mixin plugin id, `mixin {}`
  blocks, or mixins.json bundling) and wires it into `build`. Applied by
  `everlastingskins.forge-module`, `everlastingskins.java8-forge-module` and
  `:common` itself; new forge convention plugins must apply it too.

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
6. **1.12.2 lane:** never include it in `settings.gradle.kts`. It builds
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

CI (`ci.yml`) is still the old single-module workflow — rework into a
per-module matrix is a tracked follow-up; do not rely on it for this branch.
