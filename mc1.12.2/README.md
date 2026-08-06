# mc1.12.2 (per-lane wrapper, out-of-band)

The Minecraft 1.12.2 lane (Forge 14.23.5.2847). NOT a subproject of the root
Gradle build — FG 2.3.4 needs Gradle 4.x + Java 8, so this lane keeps its own
wrapper (Gradle 4.10.3) and builds in isolation:

```bash
cd mc1.12.2
JAVA_HOME=<jdk8> ./gradlew build   # JDK 8 required (sdkman: 8.0.472-amzn)
```

The main source set shares the monorepo's `:common` via
`srcDir '../common/src/main/java'` (no JPMS on Java 8). Shared classes were
deleted from `src/main/java` at import — `:common` is the single canonical
copy. Lane-specific bindings (commands, listeners, permission services,
mixins) live in `src/main/java` and adapt to the `:common` APIs.

Shared classes are reconciled against `:common`, never edited in this lane;
if a lane copy of a `:common` class reappears, delete it or javac fails on
the duplicate class.
