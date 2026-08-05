# everlastingskins-common

Pure-Java core of Everlasting-Skins, shared by the per-version Minecraft
branches. Compiled with `--release 8`: one jar runs on Java 8 (mc1.12.2,
Forge 1.12.2) and Java 21 (1.21+, Forge 1.21).

## Layout

```
src/main/java/levosilimo/everlastingskins/
  enums/        LanguageEnum, SkinActionType, SkinVariant
  integration/  DiscordSrvConfig (decoupled settings holder)
  metrics/      SkinMetrics, LatencyHistogram, MetricsFormat, Snapshot, PlayerSnapshot
  permission/   IPermissionService, PermissionServiceManager (registration-based, fail-closed)
  skinchanger/  HTTP API impls (Mojang/MineSkin/Cosmetica/Random sources), SkinIO/SkinStorage, responses
  util/         JsonUtils, CustomSkinProperty, HttpClient, HttpsUrlConnectionHttpClient, helpers
src/main/resources/
  endpoints.properties          endpoint URLs (loaded by EndpointsConfig)
  everlastingskins/default-skin.properties
src/test/java/...               unit tests + fakes (FakeHttpClient, FakeMojangAPI)
src/test/resources/fixtures/    HTTP/HTML fixtures (mojang/, mineskin/, mskins/)
```

## Build

Requires JDK 21 (toolchain is read from `gradle.properties` →
`java.toolchain=21`). Compilation is gated by `--release 8 -Xlint:unchecked
-Werror`; the JDK-20+ "source value 8 is obsolete" warning is an
`[options]`-category noise and is silenced with `-Xlint:-options`.

```
./gradlew build          # compile + test
./gradlew test           # tests only
./gradlew jar            # build/libs/everlastingskins-common-0.1.0-SNAPSHOT.jar
```

## Consumption

The module is a plain Gradle project (Gradle 8.8 wrapper, no git repo — it
is a sibling directory of the per-version clones, per the multi-version plan
step 1). Per-version builds consume it by local file path or `mavenLocal`
(M2 step 2):

```kotlin
implementation(files("../common/build/libs/everlastingskins-common-0.1.0-SNAPSHOT.jar"))
```

Runtime dependencies are deliberately none beyond what both consumers
already ship on their Minecraft classpaths:

| Classpath item     | Provided by                          | Declared in :common as |
|--------------------|--------------------------------------|------------------------|
| Gson               | Minecraft (1.12.2: 2.8.0, 1.21: 2.x) | compileOnly 2.8.0      |
| com.mojang.authlib | Minecraft (both versions)            | compileOnly 1.5.25     |
| log4j-api          | Minecraft (both versions)            | compileOnly 2.8.1      |
| jsr305 (@Nullable) | both consumers (annotation-only)     | compileOnly 3.0.2      |

Test-only: JUnit 5.10.3, jqwik 1.9.0, Mockito 5.12.0.

## Mixin Policy

`/common` contains ZERO Mixin annotations or `@Mixin` classes. Consumers
(forge subprojects) must not use mixingradle, must not declare `mixin {}`
blocks, and must not bundle `everlastingskins.mixins.json`. If a consumer
requires Mixin support, fork the consumer — it is a sign that should not
happen in /common.

The `no-mixin` convention plugin (see `build-logic/`) registers a
`verifyNoMixin` task that fails the build on any Mixin usage and wires it
into `build`; `scripts/assert-no-mixin.sh` is the same gate for CI (see the
`lint-no-mixin` workflow).

## Decoupling conventions

Lifted code never references the per-version layers (`Config`,
`EverlastingSkins`, Minecraft classes). Where the original read the mod
config, the common form takes injected settings with the per-version Config
defaults as fallback:

- `MojangProfileCache()` / `MojangProfileCache(ttlMs, maxEntries)` — defaults
  1 h / 1000 entries.
- `MojangApiHttpImpl(endpoints, httpClient, profileCacheEnabled)` — default
  `true`; cache settings via `MojangProfileCache` defaults.
- `MineSkinApiHttpImpl(httpClient, apiKey, allowlistEnabled,
  allowlistDomains)` — defaults: empty key, allowlist off (domains constant
  mirrors the per-version Config default list).
- `DiscordSrvConfig.configure(enabled, channelId)` — per-version bootstrap
  injects its Config values; defaults off/empty.
- `PermissionServiceManager.registerService(...)` — per-version bootstrap
  registers its MC-bound backends; unregistered checks fail closed.
- `RandomMojangSkin.setMojangAPI(...)` — package-private seam; defaults to
  `new MojangApiHttpImpl()`.

Logging uses Log4j2 `LogManager.getLogger(Class)` — identical backend and
`{}` placeholders in both consumers.

## Adding new pure-Java files

1. Keep the 1.12.2 class form as the baseline (no records, no `var`, no
   `List.of`/`Map.of`, no `Path.of`, no `Files.readString`, no
   `String.isBlank`, no `Optional.or` — the `-Werror --release 8` build
   rejects them).
2. Do not import `levosilimo.everlastingskins.Config`/`EverlastingSkins` or
   any `net.minecraft` class; inject settings instead.
3. Add matching tests under `src/test/java/...`; the build treats every
   warning as an error.
