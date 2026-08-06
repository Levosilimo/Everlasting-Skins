# Repository Structure

Layout of the Everlasting-Skins monorepo, split into one Gradle root plus
out-of-band lanes that cannot run under the root's Gradle version.

## One Gradle root

`settings.gradle.kts` at the repo root includes:

- `:common` — the version-independent core. Pure Java, compiled with
  `--release 8`; runs on every supported Minecraft version. Canonical home
  for shared code (`common/src/main/java` + `common/src/main/resources`).
- `:forge-1.21`, `:forge-1.21.1`, `:forge-1.21.4`, `:forge-1.21.8` — the
  Forge line, each consuming `:common` via `implementation(project(":common"))`.

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

| Lane           | Gradle  | ForgeGradle | Java | Build command            |
|----------------|---------|-------------|------|--------------------------|
| `forge-1.16.5/` | 7.6.4   | 5.1.77      | 8    | `cd forge-1.16.5 && ./gradlew build` |
| `forge-1.20.1/` | 8.7     | 6.0.54      | 21   | `cd forge-1.20.1 && ./gradlew build` |
| `mc1.12.2/`     | 4.10.3  | 2.3.4       | 8    | `cd mc1.12.2 && ./gradlew build`     |

All three source-dir share `common/src/main/java` and
`common/src/main/resources`, so shared code edits land in one place and are
picked up by every lane.

## Standalone parent

The historical standalone checkout at `/home/levosilimo/code/Everlasting-Skins/`
is archived (see its `README.md`). Its lanes were folded in here: `1.21/` →
`:forge-1.21` line (PR #268), `mc1.12.2/` → `mc1.12.2/` lane (PR #269). CI and
publishing use this monorepo checkout only.
