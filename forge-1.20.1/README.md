# forge-1.20.1 (reserved)

Future subproject for the Minecraft 1.20.1 lane (Java 8 source, ForgeGradle
6.x). NOT yet included in `settings.gradle.kts` — when this port starts:

1. `include("forge-1.20.1")` in the root settings.
2. Add `minecraft_version` / `forge_version` here (apply
   `everlastingskins.java8-forge-module` from `buildSrc/`).
3. Port the binding layer; lift shared code into `:common` (it already
   compiles `--release 8` and runs on 1.12.2+).

The current `common/` sources are the version-independent core this module
will consume via `implementation(project(":common"))`.
