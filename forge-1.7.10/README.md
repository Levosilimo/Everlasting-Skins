# forge-1.7.10 (standalone build — out-of-band lane)

Minecraft 1.7.10 lane: Java 8 bytecode, MCP stable_12, GTNH
**ForgeGradle 1.2.11** (resolved from jitpack, see the root AGENTS.md
supply-chain note) — running on its **own Gradle wrapper (4.4.1, Java 8)**.
FML 7.10.99.99+ (forge 10.13.4.1614), LaunchWrapper `@Mod` under
`cpw.mods.fml.*`, netty-based `NetworkRegistry` channels.

This lane is deliberately NOT part of the monorepo root's Gradle build
(lib-34 lane separation, same policy as the other legacy lanes).

## Build

```bash
cd forge-1.7.10
JAVA_HOME=/path/to/jdk8 ./gradlew build   # Gradle 4.4.1 must run on Java 8
```

## Client-join parity (FIX-6)

FML 7 enforces mod-list parity at handshake: a server mod absent from the
client's mod list rejects the join ("Mod rejections [everlastingskins]")
unless the mod opts out. The canonical opt-out on this line is the
`@Mod` attribute `acceptableRemoteVersions = "*"` (see the annotation's
comment in `EverlastingSkins.java` for the bytecode-verified mechanism:
the holder constructor special-cases exactly `"*"` to IgnoredChecker,
which accepts any remote — including vanilla clients). Enforced by
`ModParityTest` (annotation-presence regression).

### Era limitation: pre-1.7 lanes (1.4.7 / 1.5.2 / 1.6.4)

The FML 4.7 / 5.2 `@Mod` annotation has **no** `acceptableRemoteVersions`
attribute (added in FML 6.1+/7), so those lanes cannot express the opt-out
in code. They do not need to: their handshake is permissive by default —
FML 5.2's `@NetworkMod` `clientSideRequired` / `serverSideRequired`
default to `false` (verified against forge 9.11.1.1345 bytecode), and the
lanes set neither. A client without the mod is therefore accepted.

## Layout notes

- `:common` (the shared version-independent core) is consumed by
  source-dir sharing (`../common/src/main/java` added to this build's
  main source set), since a standalone build cannot use
  `project(":common")`.
- Versions live in `gradle.properties` (`minecraft_version`,
  `forge_version`).
