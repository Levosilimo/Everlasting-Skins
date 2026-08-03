# EverlastingSkins

Persistent player skin management for Minecraft Forge 1.12.2 servers.

[![CI (1.21)](https://github.com/Levosilimo/Everlasting-Skins/actions/workflows/ci.yml/badge.svg?branch=1.21)](https://github.com/Levosilimo/Everlasting-Skins/actions/workflows/ci.yml?query=branch%3A1.21)
[![CI (mc1.12.2)](https://github.com/Levosilimo/Everlasting-Skins/actions/workflows/ci.yml/badge.svg?branch=mc1.12.2)](https://github.com/Levosilimo/Everlasting-Skins/actions/workflows/ci.yml?query=branch%3Amc1.12.2)
[![Release](https://img.shields.io/github/v/release/Levosilimo/Everlasting-Skins?include_prereleases&label=latest)](https://github.com/Levosilimo/Everlasting-Skins/releases)
[![License](https://img.shields.io/github/license/Levosilimo/Everlasting-Skins)](LICENSE)
[![CurseForge](https://cf.way2muchnoise.eu/versions/538149.svg)](https://www.curseforge.com/minecraft/mc-mods/everlasting-skins)
[![Modrinth](https://img.shields.io/modrinth/dt/everlasting-skins?label=Modrinth)](https://modrinth.com/mod/everlasting-skins)
[![Java](https://img.shields.io/badge/java-8-blue)](https://adoptium.net/)

This repository uses **git branches** to target different Minecraft versions.
Each branch is isolated with its own toolchain, Forge version, and Java runtime.

## Branches

| Branch | Minecraft | Forge | Java | Status |
|--------|-----------|-------|------|--------|
| [1.21](https://github.com/Levosilimo/Everlasting-Skins/tree/1.21) | 1.21 | 51.0.8 | 21 | Active |
| [mc1.12.2](https://github.com/Levosilimo/Everlasting-Skins/tree/mc1.12.2) | 1.12.2 | 14.23.5.2847 | 8 | Active |

Each branch has its own README with version-specific installation instructions, config paths, and command documentation.

## ✨ Features

- `/skin set mojang <name>` — apply any Mojang-registered username's skin
- `/skin set web <classic|slim> <url>` — generate a skin from an image URL (MineSkin integration, config-gated)
- `/skin set random` — apply a random skin
- `/skin clear` — restore your Mojang-registered skin (or your UUID-hash default if offline)
- `/skin source` — show which username/source your current skin is from
- `/skin metrics` — view per-player skin metrics (admin-only, config-gated)
- Skins persist across server restarts (per-player JSON files)
- Server-side only — no client-side install needed

## 📦 Installation

1. Install [Forge for Minecraft 1.12.2](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html) (14.23.5.2847 or later 1.12.2 build).
2. Download `everlastingskins-1.12.2-2.1.0.jar` from the [Releases page](https://github.com/Levosilimo/Everlasting-Skins/releases).
3. Place the JAR in your server's `mods/` folder.
4. Restart the server.

## 🎮 Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/skin set mojang <name>` | any | Apply a Mojang-registered skin by username |
| `/skin set web <classic\|slim> <url>` | op 2 | Apply a skin from an image URL (MineSkin, requires config) |
| `/skin set random` | any | Apply a random skin |
| `/skin clear` | any | Restore your Mojang skin or reset to default |
| `/skin source` | any | Show your current skin source |
| `/skin metrics [human\|json\|players\|cleanup\|reset]` | admin | View skin metrics (view commands need `everlastingskins.command.metrics`; `cleanup`/`reset` need `everlastingskins.command.metrics.reset`) |

Permission nodes: `everlastingskins.command.skin`, `everlastingskins.command.skin.url`, `everlastingskins.command.skin.clear`, `everlastingskins.command.skin.random`, `everlastingskins.command.skin.other`, `everlastingskins.command.metrics`, `everlastingskins.command.metrics.reset`, and `everlastingskins.bypass.cooldown` (skips the `/skin` cooldown).

## ⚙️ Configuration

Config file: `config/everlastingskins.cfg` (auto-generated on first run).

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `Messages.localization` | String | `en` | Language for mod messages |
| `Messages.display` | Boolean | `true` | Show skin application messages in chat |
| `Messages.key` | String | (empty) | MineSkin API key (required for `/skin set web`) |
| `MineSkin.enabled` | Boolean | `false` | Enable MineSkin URL-based skin generation |
| `Integration.discordsrv_enabled` | Boolean | `false` | Enable DiscordSRV skin change announcements (hybrid servers only) |
| `Integration.discordsrv_channel_id` | String | (empty) | Discord channel ID for skin change announcements |
| `everlastingskins.metricsEnabled` | Boolean | `true` | Enable in-process skin metrics |
| `everlastingskins.metricsDumpIntervalSeconds` | Integer | `60` | Metrics dump interval (seconds) |
| `everlastingskins.refreshViaEntityTracker` | Boolean | `true` | Force EntityTracker untrack/re-track on refresh for observer entity re-render |
| `everlastingskins.rate_limit_enabled` | Boolean | `true` | Enable `/skin` rate limiting |
| `everlastingskins.cooldown_seconds` | Integer | `3` | Cooldown between `/skin` commands (seconds) |
| `everlastingskins.max_commands_per_minute` | Integer | `5` | Max `/skin` commands per minute (per player) |
| `everlastingskins.debounce_millis` | Integer | `100` | Per-player refresh debounce window (milliseconds) |
| `everlastingskins.mojangProfileCacheEnabled` | Boolean | `true` | Enable the in-process Mojang profile cache |
| `everlastingskins.mojangProfileCacheTtlMs` | Long | `3600000` | Mojang profile cache entry lifetime (milliseconds; `0` disables caching) |
| `everlastingskins.mojangProfileCacheMaxSize` | Integer | `1000` | Max Mojang profile cache entries (oldest evicted first) |
| `DefaultSkins.enabled` | Boolean | `false` | Apply a default skin from `list` to players without a saved custom skin |
| `DefaultSkins.applyForPremium` | Boolean | `false` | Also apply the default skin to players WITH a saved custom skin (display-only override) |
| `DefaultSkins.list` | String[] | `Steve, <random>` | Default skins list: Mojang usernames or the literal `<random>` token |
| `Security.urlAllowlistEnabled` | Boolean | `false` | Enable URL domain allowlist for `/skin set web` (empty list = deny all) |
| `Security.urlAllowlistDomains` | String[] | 9 default domains | Domains allowed for `/skin set web` (eTLD+1 suffix match; one entry covers all subdomains) |
| `Permissions.op_level.mojang` | Integer | `0` | Required op level for `/skin set <mojang>` |
| `Permissions.op_level.url` | Integer | `2` | Required op level for `/skin set web` |
| `Permissions.op_level.clear` | Integer | `0` | Required op level for `/skin clear` |
| `Permissions.op_level.random` | Integer | `0` | Required op level for `/skin set random` |
| `Permissions.op_level.other` | Integer | `2` | Required op level for changing another player's skin |
| `Permissions.op_level.metrics` | Integer | `2` | Required op level for `/skin metrics` |
| `Permissions.op_level.metrics_reset` | Integer | `2` | Required op level for `/skin metrics cleanup/reset` |

> **Per-player locale**: when a message has no custom default, the mod resolves the player's client language (`en_us`, etc.) via `PlayerLanguage` (AT-exposed `EntityPlayerMP.language` field, `META-INF/everlastingskins_at.cfg`) and falls back to `Messages.localization` if the field is unavailable. The 1.21 branch provides the same behavior via `ServerPlayer.clientInformation().language()`.

## 🌐 Languages

Built-in locales (11 total) — set `Messages.localization` to one of:

| Locale | Language |
|--------|----------|
| `en` | English |
| `ru` | Russian |
| `uk` | Ukrainian |
| `de_de` | German |
| `es_es` | Spanish |
| `fr_fr` | French |
| `it_it` | Italian |
| `ja_jp` | Japanese |
| `ko_kr` | Korean |
| `pt_br` | Portuguese (Brazil) |
| `zh_cn` | Chinese (Simplified) |

## 🌐 External Services

| Service | Required | Used For |
|---------|----------|----------|
| Mojang Session Server | No (offline-mode supported) | Resolving usernames to skin data |
| MineSkin API | No | Converting image URLs to skin textures |

## 💾 Storage

Skins are stored as one JSON file per player in `world/EverlastingSkins/<uuid>.json`. Writes are atomic (drain-coalesce async writer with a 50ms debounce). Files with corrupt JSON are quarantined as `.corrupt-<timestamp>` and a fresh entry is created on next save.

Mojang profile lookups are cached in-memory (MojangProfileCache, TTL 1h, cap 1000) to avoid rate limits; the cache is not persisted. Its behavior is controlled by the `everlastingskins.mojangProfileCache*` keys (see Configuration above), which are read from `Config.load()` since #160.

## ⚠️ Compatibility

- Forge 1.12.2 only. Compatible with major 1.12.2 modpacks (e.g., FTB, ATLauncher packs that use Forge 1.12.2). Tested with Forge 14.23.5.2847. Not compatible with NeoForge or Fabric.
- Server-side only — players do not need to install the mod.
- Other skin mods that modify player GameProfiles are incompatible.
- Anti-cheat plugins may need to whitelist skin-related packet sequences.

## 🧩 Hybrid Server Compatibility

The PlaceholderAPI and DiscordSRV integrations only activate on hybrid Forge+Bukkit
servers (Mohist, Magma, Arclight, CatServer). On pure Forge servers (the primary
target), these integrations are non-functional.

To enable PlaceholderAPI on a hybrid server:
1. Install PlaceholderAPI on the Bukkit side
2. Place this mod (EverlastingSkins) in the mods folder
3. Placeholders like `%everlastingskins_skin_source%` will resolve to the player's
   current skin source

To enable DiscordSRV announcements:
1. Install DiscordSRV on the Bukkit side
2. Configure the channel ID in the mod config: `Integration.discordsrv_channel_id = "123456789"`
3. Set `Integration.discordsrv_enabled = true` in the mod config
4. Skin changes will be announced to the configured Discord channel

## 🔨 Building from Source

Requires JDK 8 and Gradle 4.10.3 (via the wrapper). Uses ForgeGradle 2.3 and MCP mappings `snapshot_20171003`.

```bash
git clone -b mc1.12.2 https://github.com/Levosilimo/Everlasting-Skins
cd Everlasting-Skins
./gradlew build
```

Output: `build/libs/everlastingskins-1.12.2-2.1.0.jar`

## ❓ FAQ

**Q: Why does my skin not change?**
A: If `/skin set mojang <name>` reports "No skin found", the username may not exist on Mojang (typo, unverified account, or offline-mode username).

**Q: Can I use this on a singleplayer (integrated) server?**
A: Yes — the mod loads on integrated servers too. Open to LAN to test with others.

**Q: Does this work offline (no internet, online-mode=false)?**
A: Yes. The mod uses the player's UUID-hash default skin. `/skin set mojang <name>` will fail if no internet, but the mod doesn't crash.

**Q: What about other 1.12.2 skin mods?**
A: EverlastingSkins is the only server-side Forge 1.12.2 skin mod (per research from July 2026). SkinsRestorer for Bukkit doesn't run on pure Forge. If you need Bukkit/Spigot compatibility, use SkinsRestorer.

**Q: How do I report a bug?**
A: Open an issue at https://github.com/Levosilimo/Everlasting-Skins/issues with your Minecraft version, Forge version, mod version, and relevant server logs.

## 📄 License

MIT License. See [LICENSE](LICENSE) for details.

## 🤝 Contributing

See [CONTRIBUTING.md](.github/CONTRIBUTING.md) for development setup and contribution guidelines.

## 💖 Support

If you find this mod useful, consider sponsoring development via [GitHub Sponsors](https://github.com/sponsors/Levosilimo).
