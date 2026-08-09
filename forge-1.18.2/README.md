# forge-1.18.2 (standalone build — out-of-band lane)

Minecraft 1.18.2 lane: Java 17 bytecode, official Mojang mappings (lib-13),
ForgeGradle **6.0.54** — running on its **own Gradle wrapper (8.14, Java 21)**.

This lane is deliberately NOT part of the monorepo root's Gradle build
(lib-34 lane separation): ForgeGradle 6.0.x hard-rejects Gradle 9.0+, so it
cannot run inside the root's Gradle 9.3.1 — not as a subproject, not as an
included build (included builds run under the root's Gradle version).

## Build

```bash
cd forge-1.18.2
JAVA_HOME=/path/to/jdk21 ./gradlew build   # Gradle 8.14 daemon on Java 21
```

The compile toolchain is Java 17 (Forge 40's runtime level); on machines
without a local JDK 17 the foojay resolver in `settings.gradle.kts`
auto-provisions one.

Useful tasks: `tasks`, `runClient`, `runServer`, `prepareRuns`,
`reobfJar`, `verifyNoMixin` (wired into `build`).

## Layout notes

- `build.gradle.kts` applies FG 6.0.54 via the buildscript classpath (the
  1.18.2 MDK pattern) and inlines the scaffolding the deleted buildSrc
  convention plugin used to provide (Java 17, archives name, repos, JUnit,
  jacoco, no-mixin gate).
- `:common` (the shared version-independent core) is consumed by
  source-dir sharing (`../common/src/{main,test}` dirs added to this
  build's source sets), since a standalone build cannot use
  `project(":common")`.
- Versions live in `gradle.properties` (`minecraft_version`,
  `forge_version`).
