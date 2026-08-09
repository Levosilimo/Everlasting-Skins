# Repository Structure

Layout of the Everlasting-Skins monorepo, split into one Gradle root plus
out-of-band lanes that cannot run under the root's Gradle version.

## One Gradle root

`settings.gradle.kts` at the repo root includes:

- `:common` — the version-independent core. Pure Java, compiled with
  `--release 8`; runs on every supported Minecraft version. Canonical home
  for shared code (`common/src/main/java` + `common/src/main/resources`).
- `:forge-1.21`, `:forge-1.21.1`, `:forge-1.21.4`, `:forge-1.21.8`, `:forge-26.1`, `:forge-26.2` — the
  Forge line, each consuming `:common` via `implementation(project(":common"))`.
  forge-26.1 is IN-ROOT (not out-of-band): MC 26.1 / Forge 62.0.9 / Java 25
  (`gradle.properties`), applies both conventions
  (`everlastingskins.forge-module` + `everlastingskins.dependency-analysis`),
  unobfuscated MC (mappings gated), EventBus 7.0.1 (module-local
  eventbus-validator AP + strict runtime checks on its runs).
  forge-26.2 is IN-ROOT (not out-of-band): MC 26.2 / Forge 65.0.9 / Java 25
  (`gradle.properties`), applies both conventions
  (`everlastingskins.forge-module` + `everlastingskins.dependency-analysis`),
  unobfuscated MC (mappings gated), EventBus 7 (module-local
  eventbus-validator AP + strict runtime checks on its runs).

Root runs Gradle 9.3.1 on Java 21 (`java.toolchain.version=21`):

```
./gradlew build
```

## Out-of-band lanes

These lanes keep their own Gradle wrapper because ForgeGradle hard-rejects
newer Gradle versions (verified empirically 2026-08-06): ForgeGradle 5.1.x
rejects Gradle 8.0+, ForgeGradle 6.0.x rejects Gradle 9.0+, ForgeGradle 2.3.4
requires Gradle 4.x. Included builds would run under the root's Gradle, so
each lane is a separate build with its own wrapper and JDK:

| Lane           | Gradle  | ForgeGradle       | Java | Build command                                  |
|----------------|---------|-------------------|------|------------------------------------------------|
| `forge-1.5.2/`  | 4.4.1   | none (vendored SpecialSource 1.7.4) | 8 | `cd forge-1.5.2 && JAVA_HOME=<jdk8> ./gradlew build` |
| `forge-1.6.4/`  | 4.4.1   | none (vendored SpecialSource 1.7.4) | 8 | `cd forge-1.6.4 && JAVA_HOME=<jdk8> ./gradlew build` |
| `mc1.12.2/`     | 4.10.3  | 2.3.4             | 8    | `cd mc1.12.2 && JAVA_HOME=<jdk8> ./gradlew build` |
| `forge-1.7.10/` | 4.4.1   | 1.2.11 (GTNH/jitpack) | 8 | `cd forge-1.7.10 && JAVA_HOME=<jdk8> ./gradlew build` |
| `forge-1.8.9/`  | 4.10.3  | 2.1-SNAPSHOT      | 8    | `cd forge-1.8.9 && JAVA_HOME=<jdk8> ./gradlew build` |
| `forge-1.10.2/` | 4.10.3  | 2.2.5             | 8    | `cd forge-1.10.2 && JAVA_HOME=<jdk8> ./gradlew build` |
| `forge-1.16.5/` | 7.6.4   | 5.1.77            | 8    | `cd forge-1.16.5 && JAVA_HOME=<jdk8> ./gradlew build` |
| `forge-1.20.1/` | 8.14    | 6.0.54            | 21   | `cd forge-1.20.1 && JAVA_HOME=<jdk21> ./gradlew build` |
| `forge-1.18.2/` | 8.14    | 6.0.54            | 21   | `cd forge-1.18.2 && JAVA_HOME=<jdk21> ./gradlew build` |

All nine source-dir share `common/src/main/java` and
`common/src/main/resources`, so shared code edits land in one place and are
picked up by every lane. `forge-1.6.4` is the most fragile toolchain in the
repo (Gradle 4.4.1 = FG 1.2 hard floor, Java 8 ONLY, MCP 8.11 conf from the
vendored src zip; no ForgeGradle — GTNH FG 1.2.11 rejects forge
9.11.1.1345 at configuration time); it is built strictly out-of-band with
its own wrapper.

## Standalone parent

The historical standalone checkout at `/home/levosilimo/code/Everlasting-Skins/`
is archived (see its `README.md`). Its lanes were folded in here: `1.21/` →
`:forge-1.21` line (PR #268), `mc1.12.2/` → `mc1.12.2/` lane (PR #269). CI and
publishing use this monorepo checkout only.
