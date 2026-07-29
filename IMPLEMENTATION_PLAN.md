# Implementation Plan

## Goal

Deliver a stable Minecraft Forge 1.21 reference, then validate the pure-Forge legacy niche on Minecraft 1.12.2. Every phase produces a concrete artifact and has an explicit acceptance gate. The program pauses at the gate rather than shipping a partial build.

## Constraints (from AGENTS.md)

- One branch per materially different Minecraft era. Tags identify published releases (`mc1.21-v0.1.0-beta.1`, `mc1.12.2-v0.1.0-beta.1`).
- No multi-module Gradle build. No Architectury. No Stonecutter. No shared module layer.
- `1.21` stays the architectural reference and is never replaced as the headline target.
- Narrow commits. Portable fixes cherry-picked with `Backport-of: <sha>` trailers.
- License header cleanup is housekeeping, not blocking.
- mskins.net HTML scraping is feature-conditional, not the product identity.
- Branches: `1.21` (current, active), `mc1.12.2` (new, experimental), archived branches preserved.

## Phase 0 — Housekeeping on `1.21`

Branch: `1.21`.

Deliverables:

- Fix `gradlew`: convert CRLF line endings to LF; set executable bit so `./gradlew` runs on Linux.
- Reconcile `gradle.properties` against authoritative `build.gradle` coordinates. Drop the placeholder `mod_description`, the broad `forge_version_range=[0,)`, the broad `loader_version_range=[0,)`, and the stale `curse_versions=1.16.5`. Keep only what the executable build actually uses.
- Add `README.md` covering: supported Minecraft and Forge versions, installation, `/skin` command reference, external services contacted (Mojang, MineSkin, optional mskins.net), MineSkin API-key configuration, where skins and localization overrides are stored, license and upstream attribution, build instructions.

Acceptance gate:

- `./gradlew build` succeeds on a clean checkout.
- `gradle.properties` no longer references placeholder values.
- `README.md` exists and covers the items above.

NO-GO: wrapper still failing halts the program before any refactor.

## Phase 1 — Test seams and event migration on `1.21`

Branch: `1.21`.

Deliverables:

- Extract seven interfaces from the inventoried core, with default implementations moved into adapter packages and callers rewritten against the interface:
  - `HttpClient` (replaces `HttpClient` class; `WebUtils` becomes a deprecated shim or is deleted)
  - `MojangAPI` (replaces `MojangAPIImpl`)
  - `MineSkinAPI` (replaces `MineSkinAPIImpl`)
  - `SkinStorage` (replaces `SkinStorage`; UUID-keyed; `ServerPlayer` knowledge stays in adapter)
  - `SkinIO` (replaces `SkinIO`; consumes a `SkinRecord` POJO, not `CustomSkinProperty`)
  - `SkinCommand` (command contract becomes instance methods with injected dependencies)
  - `RandomNameProvider` (replaces `RandomMojangSkin`)
- Replace `MixinCommandManager` with a `RegisterCommandsEvent` handler.
- Replace `MixinPlayerManager` with `PlayerLoggedInEvent`, `PlayerLoggedOutEvent`, `ServerStoppingEvent`, and `ServerAboutToStartEvent` handlers.
- Add pure-Java JUnit 5 suite covering:
  - Mojang provider fallback order (Eclipse → Mojang → MineTools)
  - HTTP outcomes per provider: 200, 204, 404, 429 with retry-after, 500, timeout, malformed body
  - MineSkin retry classification: invalid key, rate-limit, terminal failures
  - Texture round-trip: encode a property, decode it, extract URL and variant
  - Persistence atomicity: write → simulated crash mid-write → next read produces prior state or quarantines partial file
  - Corrupt-record handling: malformed JSON, partial JSON, missing fields
  - Cache behavior: TTL on external lookups, size bound
  - URL sanitization
- All tests run against a local fake HTTP server. No live Mojang or MineSkin traffic in tests.
- Move `DEFAULT_SKIN` base64 blob out of source into a resource file.

Acceptance gate:

- Both mixin classes are removed or reduced to no-ops.
- JUnit suite green on Java 21.
- Local 1.21 Forge server (gradle `runServer`) with the mod loaded applies a skin via `/skin <name>`, persists across restart, clears, and reports source.
- No null returns from transport or filesystem paths at module boundaries; transport failures surface as typed outcomes.

NO-GO: any failing acceptance blocks the tag and pauses before forking to 1.12.2.

## Phase 2 — 1.21 stability tag

Branch: `1.21`.

Deliverable: tag `mc1.21-v0.1.0-beta.1` after a loadable mod jar is produced by `./gradlew build`.

NO-GO: a non-loadable jar pauses the program; the reference is not stable enough to fork from.

## Phase 3 — 1.12.2 toolchain bootstrap

Branch: new `mc1.12.2`, created in a worktree.

Deliverables:

- Replace `build.gradle` with Anatawa12 ForgeGradle 2.3 wiring:
  - Java 8 toolchain
  - MCP mappings `stable_39` or `snapshot_20171003` (pick based on what resolves in the Anatawa12 fork)
  - Mixin 0.7.11-SNAPSHOT or 0.8.5 shaded into the jar
  - `TweakClass`, `MixinConfigs`, and `FMLCorePluginContainsFMLMod` manifest entries
- Separate Gradle 4.x–6.3 wrapper with LF line endings. Wrapper script sets `JAVA_HOME` to Java 8.
- `gradle.properties` authored fresh for 1.12.2: `mod_version=0.1.0-beta.1`, correct `minecraft_version` and `forge_version`, accurate `minecraft_version_range` and `forge_version_range`.
- Empirical Mixin bootstrap probe: a one-class empty mod with no feature code.
  - Primary: Anatawa12 FG 2.3 Mixin plugin loads under LaunchWrapper without a tweaker.
  - Fallback: `IFMLLoadingPlugin` / `FMLCorePlugin` via a single `META-INF` entry.
  - Stop and report if neither works.
- Open empirical questions resolved during this phase:
  - Anatawa12 FG 2.3 Mixin plugin behavior without tweaker
  - Which MCP mapping snapshot resolves in the fork
  - Whether 1.12.2 `MinecraftServer` reference is publicly accessible or requires a narrow mixin
  - Whether the Gradle 4.x–6.3 wrapper is fetchable from a public distribution or must be vendored

Acceptance gate:

- Empty probe mod compiles.
- Wrapper launches a 1.12.2 Forge server.
- Forge reports the mod as loaded.
- README updated with 1.12.2 build steps and `JAVA_HOME` requirement.

NO-GO: empty probe failing to load pauses the entire 1.12.2 program. No feature code is written on a broken toolchain.

## Phase 4 — 1.12.2 feature port

Branch: `mc1.12.2`.

Deliverables:

- 28 pure-Java core files copied from `1.21`. `CustomSkinProperty`, `SkinStorage`, `SkinIO`, and adapter classes are rewritten against the `1.12.2` `com.mojang.authlib.GameProfile` types and the 1.12.2 packet classes. No live `MojangAPIImpl`-vs-`MineSkinAPIImpl` circular calls; provider coordination lives in a single orchestrator.
- 12 adapter files rewritten against 1.12.2 surfaces:
  - Skin refresh via `SPacketPlayerListItem` (Action.REMOVE_PLAYER then ADD_PLAYER) + `SPacketRespawn(dimension, difficulty, worldType, gameMode)` + `SPacketPlayerPosLook` + `SPacketPlayerAbilities` + `SPacketServerDifficulty`
  - Command registration via `FMLServerStartingEvent#registerServerCommand(ICommand)` with `CommandBase`/`ICommand` (not Brigadier)
  - Login/logout via `PlayerLoggedInEvent` and `PlayerLoggedOutEvent`
  - Graceful save via `FMLServerStoppingEvent`
- Only one mixin is permitted: the PlayerList-equivalent method for pre-connection profile mutation.
- Phase 1 JUnit suite ported against the same interfaces with a 1.12.2-specific fake `MinecraftServer` fixture.
- MineSkin gated behind a config flag, off by default for the viability gate.

Acceptance gate:

- Server-only `/skin` registers and runs.
- Pure-Java tests green on Java 8.
- No feature code touches Mojang live endpoints during tests.
- Pre-connection profile mutation timing is verified to fire before the client receives the profile.

NO-GO: JUnit suite failing to port pauses Phase 4 to reconcile interface shapes before any further adapter work.

## Phase 5 — 1.12.2 viability gate

Branch: `mc1.12.2`.

Real Forge 1.12.2 server plus two real clients.

Sequence:

1. Player A applies a skin via `/skin <name>`.
2. Player B (separate client) sees the updated skin.
3. Player A disconnects and reconnects; skin still applied.
4. Server restarts; skin still applied.
5. `/skin source` reports the original input.

Tag `mc1.12.2-v0.1.0-beta.1` only after the full sequence passes.

NO-GO: any failure blocks the tag and triggers a written post-mortem before retry. The viability test gates on Mojang-name skins only; MineSkin is a per-branch follow-up.

## Phase 6 — Maintenance

Branches separate. No unification.

- Cherry-pick portable fixes with `Backport-of: <sha>` trailers.
- Do not create 1.7.10, 1.16.5, or 1.18.2 branches without measured demand.
- License header audit on response DTOs is filed as a small cleanup commit on each branch.
- If mskins.net scraper survives the Phase 5 gate, document and keep; if it destabilizes the gate, drop it from 1.12.2 only.

## Cross-phase risk register

| Risk | Mitigation |
|---|---|
| Tweaker-only Mixin bootstrap on 1.12.2 may fail with vanilla LaunchWrapper | Anatawa12 FG 2.3 Mixin plugin as primary path; Phase 3 end is an explicit NO-GO rather than patching forward |
| Pre-connection profile mutation timing differs across eras | One narrow PlayerList mixin; Phase 5 cross-client visibility is the verification signal |
| Gradle wrapper CRLF and missing +x bit on 1.21 checkout | Phase 0 fixes both; `./gradlew build` is the Phase 1 entry gate |
| mskins.net scraper fragility destabilizes the viability gate | Gate the 1.12.2 test on Mojang-name skins only; add MineSkin after |
| Texture/property encoding differences silently corrupt persistence | Phase 1 JUnit texture round-trip; Phase 4 carries the suite with a 1.12.2 fixture |
| Packet-only skin refresh is fragile on 1.12.2 | No shortcut without real server + client testing; Phase 5 cross-client visibility is the only signal |
| 1.12.2 `ICommand`/`CommandBase` path diverges from 1.21 Brigadier | Branch-divergent adapter files only; no shared `ICommand` interface across eras |
| Stale `gradle.properties` misleads setup | Phase 0 reconciles on 1.21; 1.12.2 branch authors its own |
| 1.12.2 `MinecraftServer` static reference may not be publicly accessible | Resolve in Phase 3; if needed, add one narrow mixin, not an access transformer |
| Gradle 4.x–6.3 wrapper on Java 21 host needs Java 8 pin | `JAVA_HOME` set explicitly in wrapper script; documented in `README.md` |

## Open empirical questions to resolve before Phase 4

- Does Anatawa12 FG 2.3 Mixin plugin load a mod under LaunchWrapper without a tweaker on Forge 1.12.2? Resolved in Phase 3 probe.
- Which MCP mapping snapshot resolves in the Anatawa12 fork artifact set? Resolved during Phase 3.
- Is the 1.12.2 `MinecraftServer` reference publicly accessible, or does shutdown persistence need a narrow mixin? Resolved in Phase 3.
- Concrete packet-id and field differences for `SPacketPlayerListItem` and `SPacketRespawn` between 1.12.2 minor versions? Resolved at Phase 4 entry.
- Is MineSkin's HTML endpoint shape stable enough to keep, or should it be config-gated? Resolved at Phase 5 entry; gate the viability test on Mojang-name only.
- Is the Gradle 4.x–6.3 wrapper fetchable from a public distribution, or do we vendor it? Resolved at Phase 3 entry.

## Sequencing rationale

`1.21` is the architectural reference. Lock it first: prove mixins are not needed (Phase 1), prove the build is reproducible (Phase 2). Only then branch into `1.12.2` with the same test contract, where the toolchain is hardest and the empirical unknowns concentrate. The viability gate (Phase 5) is the only signal that proves the niche is real before any further version branch is created.
