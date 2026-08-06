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
forge-1.16.5/  forge-1.20.1/ legacy-lane scaffolds (included; FG applied per-subproject)
mc1.12.2/                    NOT a subproject — own Gradle 4.10.3 wrapper, builds out-of-band
```

## Modules

| Module | MC | Forge | FG | Gradle | Toolchain |
|---|---|---|---|---|---|
| `:common` | — | — | — | root 9.3.1 | build JDK 21, `--release 8` |
| `:forge-1.21` | 1.21 | 51.0.8 | 7.x | root 9.3.1 | 21 |
| `:forge-1.21.1` | 1.21.1 | 52.1.16 | 7.x | root 9.3.1 | 21 |
| `:forge-1.21.4` | 1.21.4 | 54.1.18 | 7.x | root 9.3.1 | 21 |
| `:forge-1.21.8` | 1.21.8 | 58.1.21 | 7.x | root 9.3.1 | 21 |
| `:forge-1.16.5` | 1.16.5 | 36.2.34 | 5.1.x (per-subproject) | root 9.3.1 | Java 8 source |
| `:forge-1.20.1` | 1.20.1 | 47.4.10 | 6.x (per-subproject) | root 9.3.1 | Java 17 source |

`forge-1.16.5` and `forge-1.20.1` are scaffold-only: their build scripts and
`gradle.properties` are in place (and in `settings.gradle.kts`), but the
FG-typed `minecraft {}` blocks wait on the source carry-over (FG 5.1/6.x
cannot be applied from `buildSrc/`, which carries FG 7.x for the 1.21 lane).

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
cd mc1.12.2 && ./gradlew build   # Gradle 4.10.3 wrapper, Java 8
```

## Notes / known state

- **Scaffold stage (M2 step 2):** this branch establishes the multi-module
  layout and convention plugins. The per-subproject source ports (splitting
  `forge-1.21` into binding layer + `:common`) land in later PRs. Until then
  `forge-1.21/` still carries the full pre-split source, and `common/` holds
  the standalone module copied from the parent `Everlasting-Skins/common/`
  (Lane B's in-flight lift; the two copies intentionally diverge until the
  port reconciles them).
- **No mixingradle:** the convention plugin applies mixin annotation
  processing + jar-manifest attributes only (Lane C). Enforced by the
  `verifyNoMixin` gate in `buildSrc/` (`no-mixin.gradle.kts`, ported from the
  parent's `common/build-logic`), which fails the build on any Mixin usage.
- **CI:** `.github/workflows/ci.yml` is a per-module matrix (PR #260):
  lint-yaml, then `build` over `:common` + the four 1.21.x modules, an
  out-of-band mc1.12.2 build (own wrapper, JDK 8), and the `E2E (mc1.12.2)`
  required-check placeholder. `publish.yml` was reworked in the same PR.
  The 1.16.5 / 1.20.1 scaffolds are not in the matrix yet.
- **Artifact naming:** `everlastingskins-<mc>` (was `EverlastingSkins-<mc>`).
- `mc1.12.2/` is not yet included in this repo; it is imported from the
  parent checkout's history when the port happens.

## Recently merged (M2 campaign)

- **#257** — initial `forge-1.16.5` subproject scaffold (+ lib-35 first shims).
- **#258** — `verifyNoMixin` build gate ported into `buildSrc/`.
- **#259** — `run-gametest-local.sh` paths corrected for the multi-module layout.
- **#260** — `ci.yml` + `publish.yml` reworked to a per-module matrix.
- **#261** — initial `forge-1.20.1` subproject scaffold.
- **#263** — `forge-1.21` compile compat restored with Forge 51.0.8.
- **#264** — `forge-1.21` test/gametest sources downgraded for Forge 51.0.8.
- **#265** — `forge-1.21` runtime blockers resolved (incl. JPMS split-package).

Still ahead: source carry-over onto `:common` for the legacy lanes, the
`consumeCommon` flip for `forge-1.21`, and the `pack.mcmeta` format bump
(see CHANGELOG).
