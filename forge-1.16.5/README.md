# forge-1.16.5 (standalone build — out-of-band lane)

Minecraft 1.16.5 lane: Java 8 bytecode, official Mojang mappings (lib-35),
ForgeGradle **5.1.77** — running on its **own Gradle wrapper (7.6.4, Java 8)**.

This lane is deliberately NOT part of the monorepo root's Gradle build
(lib-34 lane separation): ForgeGradle 5.1.x hard-rejects Gradle 8.0+, so it
cannot run inside the root's Gradle 9.3.1 — not as a subproject, not as an
included build (included builds run under the root's Gradle version).

## Build

```bash
cd forge-1.16.5
JAVA_HOME=/path/to/jdk8 ./gradlew build   # Gradle 7.6.4 daemon must run on Java 8
```

Useful tasks: `tasks`, `runClient`, `runServer`, `prepareRuns`,
`reobfJar`, `verifyNoMixin` (wired into `build`).

## Layout notes

- `build.gradle.kts` applies FG 5.1.77 via the buildscript classpath (the
  1.16.5 MDK pattern) and inlines the scaffolding the deleted buildSrc
  convention plugin used to provide (Java 8, archives name, repos, JUnit,
  jacoco, no-mixin gate).
- `:common` (the shared version-independent core) is consumed by
  source-dir sharing (`../common/src/{main,test}` dirs added to this
  build's source sets), since a standalone build cannot use
  `project(":common")`.
- Versions live in `gradle.properties` (`minecraft_version`,
  `forge_version`).
