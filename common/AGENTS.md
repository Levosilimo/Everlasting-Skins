# AGENTS.md — :common (version-independent core)

The pure-Java heart of Everlasting-Skins, shared by every per-version lane.
Compiled with `--release 8 -Werror`: one jar runs on Java 8 (mc1.12.2,
forge-1.16.5) and Java 21 (1.21+).

## Rules

- **No Forge bindings.** `:common` must stay pure Java; anything touching
  Minecraft classes belongs in a `forge-*` binding layer.
- **Frozen at `--release 8`.** Never raise the release level; compile with
  `./gradlew :common:build` and fix failures before touching forge modules.
- **No Mixin** — the `no-mixin` gate (`verifyNoMixin`) applies here too.

## Scope

`src/main/java/levosilimo/everlastingskins/` — enums, DiscordSrvConfig,
metrics, the permission service seam, skinchanger (HTTP sources, SkinIO /
SkinStorage), utils (JsonUtils, CustomSkinProperty, HttpClient).
`src/test/` carries unit tests + fakes (FakeHttpClient, FakeMojangAPI);
306 test cases, no skips (per common/CHANGELOG.md 0.1.0-SNAPSHOT).

## Consumption

Every `forge-*` module depends on `:common` via
`implementation(project(":common"))` — `:common` is unconditionally consumed.

The union of `:common` + each per-version binding layer covers the full mod.

See the monorepo root `AGENTS.md` for the overall module layout, convention
plugins, and CI.
