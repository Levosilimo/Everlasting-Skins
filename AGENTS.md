# EverlastingSkins agent guide

## Product direction

- Treat this as a small server-side Minecraft Forge mod, not a general platform.
- The defensible niche is persistent custom skins on pure Forge legacy servers without a client mod. Validate that niche on Minecraft 1.12.2 before funding broader ports.
- Preserve `1.21` as the behavioral and architectural reference. Do not compete broadly on modern loaders. Established modern skin mods already cover Forge, NeoForge, Fabric, and Quilt better.
- Keep Mojang-name skins, MineSkin URL skins, persistence, clear/reset, and source reporting. Do not make the fragile `mskins.net` HTML scraper the product identity.
- Do not start a cross-era multi-module build. Use one branch per materially different Minecraft era, annotated tags for releases, and worktrees for parallel local work. Add another supported branch only after measured demand.
- Initial release tags should identify both targets, for example `mc1.12.2-v0.1.0-beta.1`.

## Current branch

- The checked-in code is the Forge 1.21 implementation. `EverlastingSkins` registers common config and `SkinRestorer`; `SkinRestorer` initializes per-server JSON storage; `MixinPlayerManager` applies and saves skins; `MixinCommandManager` registers `/skin`.
- Skin refresh is server-only and relies on profile mutation plus version-specific reconnect-style packets. Do not replace it with an unverified packet shortcut. Real client/server testing is required.
- Prefer stable Forge/FML lifecycle events over Mixins when the target version exposes the required timing. Use narrow Mixins when events cannot preserve behavior. Access transformers and reflection do not replace hook injection; raw ASM is the last fallback.

## Build facts and traps

- Current configuration targets Minecraft `1.21`, Forge `51.0.8`, Java 21, Gradle 8.8, ForgeGradle 6, official mappings, and Mixin `0.8.5` annotation processing.
- Build with the repository wrapper. The current `gradlew` is non-executable and has CRLF line endings, so `./gradlew` and `bash gradlew` fail on Linux. Fix the wrapper before documenting a canonical build command or adding CI.
- There is no test source set or CI workflow yet. Do not claim tests pass. A successful Gradle build is currently the narrowest available verification.
- `gradle.properties` contains stale metadata (`minecraft_version_range`, `curse_versions`, placeholder description, broad loader/Forge ranges). Treat executable dependency coordinates in `build.gradle` as authoritative until metadata is reconciled.
- The local Qartez index currently points at another workspace. If Qartez returns paths such as `neodeal/` or `ik_llama.cpp`, stop and re-index this repository rather than trusting impact or dependency results.

## Verification direction

- Start with pure Java tests for provider fallback, HTTP outcomes, texture decoding, persistence, and cache behavior. Use a local fake HTTP server; never use live Mojang or MineSkin responses as deterministic tests.
- Minecraft GameTest is useful only on modern versions for server loading, commands, and server-side integration. It does not test authenticated login, skin rendering, client texture caching, or reconnect packet behavior.
- The 1.12.2 viability gate is a real Forge server/client test: apply a skin, verify another client sees it, reconnect, restart the server, and verify persistence.
- Keep per-player JSON unless measured requirements justify a database. Make writes atomic and handle corrupt records before changing storage technology.

## Porting policy

- First target: `1.12.2`. Consider `1.7.10`, `1.16.5`, or `1.18.2` only after user demand and maintenance cost are measured.
- Do not restore `1.13`-`1.15`, `1.17.1`, `1.19.x`, or `1.20.2` merely because historical branches exist.
- Keep portable fixes in narrow commits and cherry-pick them between supported branches. Use `Backport-of: <commit>` when the implementation must differ.
- Do not merge incompatible Minecraft-version branches. Their Java, Gradle, mappings, Forge APIs, packet classes, metadata, and Mixin targets differ.
- Introduce shared modules or preprocessing only after at least two supported targets demonstrate enough repeated code and porting cost to justify it. Architectury solves modern loader portability, not legacy Minecraft-version compatibility.

## Pre-commit quality gate

- The repo uses `aislop` as a tracked pre-commit hook at `.githooks/pre-commit`, wired via `git config core.hooksPath .githooks`. After cloning, run `git config core.hooksPath .githooks` once to activate.
- The hook runs `aislop scan --staged` and surfaces findings (god-files, god-functions, deeply nested branches, swallowed exceptions, narrative comments, hardcoded URLs/IDs, FizzBuzz-Enterprise-style overengineering) at commit time. Warnings do not block commits by default; treat them as review signals.
- `aislop` is fetched on demand by `bunx` (fallback `npx`). No `package.json` or `node_modules` in the repo. If neither runner is on PATH, the hook logs and exits 0.
- Use `aislop fix` to auto-apply mechanical fixes (unused imports, narrative comments, formatter drift). Use `aislop rules` for the full rule catalog. Use `git commit --no-verify` only when intentionally bypassing the gate.
- See `IMPLEMENTATION_PLAN.md` for the phased refactor and 1.12.2 port plan.
