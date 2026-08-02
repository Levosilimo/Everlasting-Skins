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
| [1.21](https://github.com/Levosilimo/Everlasting-Skins/tree/1.21) | 1.21 | 51.0.24 | 21 | Active |
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
| `/skin set web <classic\|slim> <url>` | any | Apply a skin from an image URL (MineSkin, requires config) |
| `/skin set random` | any | Apply a random skin |
| `/skin clear` | any | Restore your Mojang skin or reset to default |
| `/skin source` | any | Show your current skin source |
| `/skin metrics [human\|json\|players\|cleanup\|reset]` | admin | View skin metrics (view commands need `everlastingskins.command.metrics`; `cleanup`/`reset` need `everlastingskins.command.metrics.reset`) |

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

## 🌐 External Services

| Service | Required | Used For |
|---------|----------|----------|
| Mojang Session Server | No (offline-mode supported) | Resolving usernames to skin data |
| MineSkin API | No | Converting image URLs to skin textures |

## 💾 Storage

Skins are stored as one JSON file per player in `world/EverlastingSkins/<uuid>.json`. Writes are atomic (drain-coalesce async writer with a 50ms debounce). Files with corrupt JSON are quarantined as `.corrupt-<timestamp>` and a fresh entry is created on next save.

Mojang profile lookups are cached in-memory (MojangProfileCache, TTL 1h, cap 1000) to avoid rate limits; the cache is not persisted and needs no configuration.

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
2. Configure the channel ID in the mod config: `discordsrv_channel_id = "123456789"`
3. Set `discordsrv_enabled = true` in the mod config
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
