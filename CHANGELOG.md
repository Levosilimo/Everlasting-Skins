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

### Known follow-ups (next PRs)

- Port `forge-1.21` sources onto `:common` (reconcile the two copies).
- Port 1.21.1/1.21.4/1.21.8 sources.
- Rework `.github/workflows/ci.yml` to a per-module matrix.
- Start 1.16.5 / 1.20.1 lanes (Java 8 source).
