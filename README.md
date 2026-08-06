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
forge-1.16.5/  forge-1.20.1/ reserved placeholders (future lanes, not included yet)
mc1.12.2/                    NOT a subproject — own Gradle 4.10.3 wrapper + FG 2.3.4, Java 8,
                            builds out-of-band, shares ../common as a source dir
```

## Modules

| Module | MC | Forge | FG | Gradle | Toolchain |
|---|---|---|---|---|---|
| `:common` | — | — | — | root 9.3.1 | build JDK 21, `--release 8` |
| `:forge-1.21` | 1.21 | 51.0.8 | 7.x | root 9.3.1 | 21 |
| `:forge-1.21.1` | 1.21.1 | 52.1.16 | 7.x | root 9.3.1 | 21 |
| `:forge-1.21.4` | 1.21.4 | 54.1.18 | 7.x | root 9.3.1 | 21 |
| `:forge-1.21.8` | 1.21.8 | 58.1.21 | 7.x | root 9.3.1 | 21 |
| (future) `:forge-1.16.5` / `:forge-1.20.1` | — | — | 5.1+/6.x | TBD | Java 8 source |
| `mc1.12.2/` (not a subproject) | 1.12.2 | 14.23.5.2847 | 2.3.4 | own 4.10.3 wrapper | JDK 8 |

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
- **CI:** `.github/workflows/` still targets the old single-module layout and
  is NOT green on this branch; the module-matrix CI rework is a follow-up
  (see AGENTS.md).
- **Artifact naming:** `everlastingskins-<mc>` (was `EverlastingSkins-<mc>`).
- `mc1.12.2/` is imported from the parent checkout's history and builds
  out-of-band with its own wrapper (Gradle 4.10.3 + FG 2.3.4 + Java 8). Its
  main source set shares `../common/src/main/java`; overlapping lane copies
  were deleted at import, so `:common` is canonical. Its 514 unit tests pass
  (`cd mc1.12.2 && JAVA_HOME=<jdk8> ./gradlew test`).
