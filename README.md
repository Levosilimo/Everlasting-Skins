# EverlastingSkins

Persistent player skin management for Minecraft Forge servers.

[![CI (1.21)](https://github.com/Levosilimo/Everlasting-Skins/actions/workflows/ci.yml/badge.svg?branch=1.21)](https://github.com/Levosilimo/Everlasting-Skins/actions/workflows/ci.yml?query=branch%3A1.21)
[![CI (mc1.12.2)](https://github.com/Levosilimo/Everlasting-Skins/actions/workflows/ci.yml/badge.svg?branch=mc1.12.2)](https://github.com/Levosilimo/Everlasting-Skins/actions/workflows/ci.yml?query=branch%3Amc1.12.2)
[![Release](https://img.shields.io/github/v/release/Levosilimo/Everlasting-Skins?include_prereleases&label=latest)](https://github.com/Levosilimo/Everlasting-Skins/releases)
[![License](https://img.shields.io/github/license/Levosilimo/Everlasting-Skins)](LICENSE)
[![CurseForge](https://cf.way2muchnoise.eu/versions/538149.svg)](https://www.curseforge.com/minecraft/mc-mods/everlasting-skins)
[![Modrinth](https://img.shields.io/modrinth/dt/everlasting-skins?label=Modrinth)](https://modrinth.com/mod/everlasting-skins)
[![Java](https://img.shields.io/badge/java-21%20%7C%208-blue)](https://adoptium.net/)

This repository uses **git branches** to target different Minecraft versions.
Each branch is isolated with its own toolchain, Forge version, and Java runtime.

## Branches

| Branch | Minecraft | Forge | Java | Status |
|--------|-----------|-------|------|--------|
| [1.21](https://github.com/Levosilimo/Everlasting-Skins/tree/1.21) | 1.21 | 51.0.24 | 21 | Active |
| [mc1.12.2](https://github.com/Levosilimo/Everlasting-Skins/tree/mc1.12.2) | 1.12.2 | 14.23.5.2860 | 8 | Active |

Each branch has its own README with version-specific installation instructions, config paths, and command documentation.

## ✨ Features

- `/skin set mojang <name>` — apply any Mojang-registered username's skin
- `/skin set web <classic|slim> <url>` — generate a skin from an image URL (MineSkin integration, config-gated)
- `/skin set random` — apply a random skin
- `/skin clear` — restore your Mojang-registered skin (or your UUID-hash default if offline)
- `/skin source` — show which username/source your current skin is from
- `/skin metrics` — view per-player skin metrics (admin-only)
- Skins persist across server restarts (per-player JSON files)
- Server-side only — no client-side install needed

## 📦 Installation

1. Install [Forge for Minecraft 1.21](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.21.html).
2. Download `EverlastingSkins-1.21-2.1.0.jar` from the [Releases page](https://github.com/Levosilimo/Everlasting-Skins/releases).
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

Config file: `world/serverconfig/everlastingskins-server.toml` (auto-generated on first run).

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `messages.localization` | String | `en` | Language for mod messages |
| `messages.display` | Boolean | `true` | Show skin application messages in chat |
| `messages.key` | String | (empty) | MineSkin API key (required for `/skin set web`) |
| `integration.discordsrv_enabled` | Boolean | `false` | Enable DiscordSRV skin change announcements |
| `integration.discordsrv_channel_id` | String | (empty) | Discord channel ID for announcements |
| `ratelimit.cooldown_seconds` | Integer | `3` | Cooldown between `/skin` commands (seconds) |
| `ratelimit.rate_limit_enabled` | Boolean | `true` | Enable `/skin` rate limiting |
| `ratelimit.max_commands_per_minute` | Integer | `5` | Max `/skin` commands per minute (per player) |
| `broadcast.dimension_scoped_broadcast` | Boolean | `false` | Restrict refresh broadcasts to the target's dimension |
| `broadcast.broadcast_use_bundle` | Boolean | `false` | Send REMOVE + ADD_PLAYER broadcast as one bundle packet |
| `broadcast.debounce_millis` | Integer | `100` | Per-player refresh debounce window (milliseconds) |
| `broadcast.refresh_via_entity_tracker` | Boolean | `true` | Untrack/re-track the target entity so observers re-fetch the updated profile (fixes stale skin renders on remote clients) |
| `metrics.metrics_enabled` | Boolean | `true` | Enable in-process metrics and periodic `metrics.json` dump |
| `metrics.metrics_dump_interval_seconds` | Integer | `60` | Interval between `metrics.json` dumps (0 disables the dump) |
| `http.http_client_version` | String | `HTTP_2` | JDK HTTP client version (`HTTP_2` or `HTTP_1_1`) |
| `http.http_connect_timeout_seconds` | Integer | `5` | Connection timeout for provider requests (seconds) |
| `mojang_cache.mojang_profile_cache_enabled` | Boolean | `true` | Enable the in-process Mojang profile cache |
| `mojang_cache.mojang_profile_cache_ttl_ms` | Long | `3600000` | Mojang profile cache entry lifetime (milliseconds; `0` disables caching) |
| `mojang_cache.mojang_profile_cache_max_size` | Integer | `1000` | Max Mojang profile cache entries (oldest evicted first) |
| `default_skins.enabled` | Boolean | `false` | Apply a default skin from `list` to players without a saved custom skin |
| `default_skins.applyForPremium` | Boolean | `false` | Also apply the default skin to players WITH a saved custom skin (display-only override; their stored custom skin is preserved) |
| `default_skins.list` | String[] | `Steve, <random>` | Default skins list: Mojang usernames or the literal `<random>` token (random Mojang username on each login) |
| `security.urlAllowlistEnabled` | Boolean | `false` | Enable URL domain allowlist for `/skin set web` (empty list = deny all) |
| `security.urlAllowlistDomains` | String[] | 9 default domains | Domains allowed for `/skin set web` (eTLD+1 suffix match; one entry covers all subdomains) |
| `permissions.op_level.mojang` | Integer | `0` | Required op level for `/skin set <mojang>` |
| `permissions.op_level.url` | Integer | `2` | Required op level for `/skin set web` |
| `permissions.op_level.clear` | Integer | `0` | Required op level for `/skin clear` |
| `permissions.op_level.random` | Integer | `0` | Required op level for `/skin set random` |
| `permissions.op_level.other` | Integer | `2` | Required op level for changing another player's skin |
| `permissions.op_level.metrics` | Integer | `2` | Required op level for `/skin metrics` |
| `permissions.op_level.metrics_reset` | Integer | `2` | Required op level for `/skin metrics cleanup/reset` |

All message strings are customizable. Defaults (keys under the `Messages` section):

| Key | Default |
|-----|---------|
| `messages.messages_change` | `Skin change queued` |
| `messages.messages_fulfilled` | `Skin has been applied.` |
| `messages.messages_timeout` | `Skin fetch timed out.` |
| `messages.messages_error` | `Skin fetch failed.` |
| `messages.messages_restored_from` | `Skin restored from %s` |
| `messages.messages_cleared_no_profile` | `Skin cleared (no Mojang profile found)` |
| `messages.messages_no_source` | `No source available` |
| `messages.messages_player_only` | `Player only command` |
| `messages.messages_permission_denied` | `Permission denied` |
| `messages.messages_cooldown` | `Please wait %ds before using /skin again` |
| `messages.messages_rate_limited` | `Too many /skin commands. Try again later.` |
| `messages.messages_no_skin_found` | `No skin found for "%s"` |
| `messages.messages_no_skin_found_plain` | `No skin found` |
| `messages.messages_mineskin_rejected` | `MineSkin rejected the URL` |
| `messages.messages_no_random_username` | `No random username available` |
| `messages.messages_provider_no_result` | `Provider returned no result` |
| `messages.messages_metrics_top_players` | `Top players by refresh count:` |
| `messages.messages_metrics_refreshes` | ` refreshes` |
| `messages.messages_metrics_no_refreshes` | `(no refreshes recorded)` |
| `messages.messages_metrics_cleanup` | `Metrics cleanup: pruned %d stale player entries` |
| `messages.messages_metrics_reset` | `Metrics reset` |
| `messages.messages_discord_announce` | `**%s** changed their skin to: \`%s\`` |

## 🌐 External Services

| Service | Required | Used For |
|---------|----------|----------|
| Mojang Session Server | No (offline-mode supported) | Resolving usernames to skin data |
| MineSkin API | No | Converting image URLs to skin textures |

## 💾 Storage

Skins are stored as one JSON file per player in `world/EverlastingSkins/<uuid>.json`. Writes are atomic. Files with corrupt JSON are quarantined as `.corrupt-<timestamp>` and a fresh entry is created on next save.

## ⚠️ Compatibility

- Forge 1.21 only. Not compatible with NeoForge or Fabric.
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

Requires JDK 21 and Gradle (via the wrapper):

```bash
git clone -b 1.21 https://github.com/Levosilimo/Everlasting-Skins
cd Everlasting-Skins
./gradlew build
```

Output: `build/libs/EverlastingSkins-1.21-2.1.0.jar`

## ❓ FAQ

**Q: Why does my skin not change?**
A: If `/skin set mojang <name>` reports "No skin found", the username may not exist on Mojang (typo, unverified account, or offline-mode username).

**Q: Can I use this on a singleplayer (integrated) server?**
A: Yes — the mod loads on integrated servers too. Open to LAN to test with others.

**Q: Does this work offline (no internet, online-mode=false)?**
A: Yes. The mod uses the player's UUID-hash default skin. `/skin set mojang <name>` will fail if no internet, but the mod doesn't crash.

**Q: How do I report a bug?**
A: Open an issue at https://github.com/Levosilimo/Everlasting-Skins/issues with your Minecraft version, Forge version, mod version, and relevant server logs.

## 📄 License

MIT License. See [LICENSE](LICENSE) for details.

## 🤝 Contributing

See [CONTRIBUTING.md](.github/CONTRIBUTING.md) for development setup and contribution guidelines.

## 💖 Support

If you find this mod useful, consider sponsoring development via [GitHub Sponsors](https://github.com/sponsors/Levosilimo).
