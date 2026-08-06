# Changelog

## M2 (monorepo) — unreleased

### FG lane separation: forge-1.16.5 / forge-1.20.1 go out-of-band

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
- Follow-ups: CI wiring for the lane wrappers (publish.yml entries stay
  commented out until the lanes reach SOURCE-COMPLETE) and the actual
  userdev pipeline runs (prepareRuns/runClient/runServer, reobfJar) —
  configuration now uses the real FG 5.1/6.0 for the first time.

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

### Known follow-ups (next PRs)

- Port `forge-1.21` sources onto `:common` (reconcile the two copies).
- Port 1.21.1/1.21.4/1.21.8 sources.
- Rework `.github/workflows/ci.yml` to a per-module matrix.
- Start 1.16.5 / 1.20.1 lanes (Java 8 source).
