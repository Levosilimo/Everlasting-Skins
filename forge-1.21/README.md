# EverlastingSkins

Persistent player skin management for Minecraft Forge servers.

[![CI](https://github.com/Levosilimo/Everlasting-Skins/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Levosilimo/Everlasting-Skins/actions/workflows/ci.yml?query=branch%3Amain)
[![Release](https://img.shields.io/github/v/release/Levosilimo/Everlasting-Skins?include_prereleases&label=latest)](https://github.com/Levosilimo/Everlasting-Skins/releases)
[![License](https://img.shields.io/github/license/Levosilimo/Everlasting-Skins)](LICENSE)
[![CurseForge](https://cf.way2muchnoise.eu/versions/538149.svg)](https://www.curseforge.com/minecraft/mc-mods/everlasting-skins)
[![Modrinth](https://img.shields.io/modrinth/dt/everlasting-skins?label=Modrinth)](https://modrinth.com/mod/everlasting-skins)
[![Java](https://img.shields.io/badge/java-21%20%7C%208-blue)](https://adoptium.net/)

This repository is a **Gradle monorepo** (`main` is the default branch): every
supported Minecraft version is a lane under one root build instead of an
isolated git branch. All lanes share the version-independent `:common` module.

## Lanes

| Lane | Minecraft | Forge | Java | Notes |
|------|-----------|-------|------|-------|
| [:common](https://github.com/Levosilimo/Everlasting-Skins/tree/main/common) | — | — | 8 (`--release 8`) | version-independent shared module |
| [:forge-1.21](https://github.com/Levosilimo/Everlasting-Skins/tree/main/forge-1.21) | 1.21 | 51.0.8 | 21 | root subproject (Gradle 9.3.1) |
| [:forge-1.21.1](https://github.com/Levosilimo/Everlasting-Skins/tree/main/forge-1.21.1) | 1.21.1 | 52.1.16 | 21 | root subproject |
| [:forge-1.21.4](https://github.com/Levosilimo/Everlasting-Skins/tree/main/forge-1.21.4) | 1.21.4 | 54.1.18 | 21 | root subproject |
| [:forge-1.21.8](https://github.com/Levosilimo/Everlasting-Skins/tree/main/forge-1.21.8) | 1.21.8 | 58.1.21 | 21 | root subproject |
| [forge-1.16.5/](https://github.com/Levosilimo/Everlasting-Skins/tree/main/forge-1.16.5) | 1.16.5 | 36.2.34 | 8 | own wrapper (Gradle 7.6.4, FG 5.1.77) |
| [forge-1.20.1/](https://github.com/Levosilimo/Everlasting-Skins/tree/main/forge-1.20.1) | 1.20.1 | 47.4.10 | 21 | own wrapper (Gradle 8.7, FG 6.0.54) |
| [mc1.12.2/](https://github.com/Levosilimo/Everlasting-Skins/tree/main/mc1.12.2) | 1.12.2 | 14.23.5.2847 | 8 | own wrapper (Gradle 4.10.3, FG 2.3.4) |

The `1.21` and `mc1.12.2` branches still exist on GitHub but are **archived
stable aliases** — frozen snapshots of the old per-branch layout, not active
development targets. See [REPOSITORY-STRUCTURE.md](../REPOSITORY-STRUCTURE.md)
for the full layout.

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
2. Download `everlastingskins-1.21-2.1.0.jar` from the [Releases page](https://github.com/Levosilimo/Everlasting-Skins/releases).
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

## ⚙️ Configuration

Config file: `world/serverconfig/everlastingskins-server.toml` (auto-generated on first run).

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `Messages.localization` | String | `en` | Language for mod messages |
| `Messages.display` | Boolean | `true` | Show skin application messages in chat |
| `Messages.key` | String | (empty) | MineSkin API key (optional; raises rate limits for `/skin set web`) |
| `Integration.discordsrv_enabled` | Boolean | `false` | Enable DiscordSRV skin change announcements |
| `Integration.discordsrv_channel_id` | String | (empty) | Discord channel ID for announcements |
| `RateLimit.cooldown_seconds` | Integer | `3` | Cooldown between `/skin` commands (seconds) |
| `RateLimit.rate_limit_enabled` | Boolean | `true` | Enable `/skin` rate limiting |
| `RateLimit.max_commands_per_minute` | Integer | `5` | Max `/skin` commands per minute (per player) |
| `Broadcast.dimension_scoped_broadcast` | Boolean | `false` | Restrict refresh broadcasts to the target's dimension |
| `Broadcast.broadcast_use_bundle` | Boolean | `false` | Send REMOVE + ADD_PLAYER broadcast as one bundle packet |
| `Broadcast.debounce_millis` | Integer | `100` | Per-player refresh debounce window (milliseconds) |
| `Broadcast.refresh_via_entity_tracker` | Boolean | `true` | Untrack/re-track the target entity so observers re-fetch the updated profile (fixes stale skin renders on remote clients) |
| `Metrics.metrics_enabled` | Boolean | `true` | Enable in-process metrics and periodic `metrics.json` dump |
| `Metrics.metrics_dump_interval_seconds` | Integer | `60` | Interval between `metrics.json` dumps (0 disables the dump) |
| `Http.http_client_version` | String | `HTTP_2` | JDK HTTP client version (`HTTP_2` or `HTTP_1_1`) |
| `Http.http_connect_timeout_seconds` | Integer | `5` | Connection timeout for provider requests (seconds) |
| `MojangCache.mojang_profile_cache_enabled` | Boolean | `true` | Enable the in-process Mojang profile cache |
| `MojangCache.mojang_profile_cache_ttl_ms` | Long | `3600000` | Mojang profile cache entry lifetime (milliseconds; `0` disables caching) |
| `MojangCache.mojang_profile_cache_max_size` | Integer | `1000` | Max Mojang profile cache entries (oldest evicted first) |
| `DefaultSkins.enabled` | Boolean | `false` | Apply a default skin from `list` to players without a saved custom skin |
| `DefaultSkins.applyForPremium` | Boolean | `false` | Also apply the default skin to players WITH a saved custom skin (display-only override; their stored custom skin is preserved) |
| `DefaultSkins.list` | String[] | `Steve, <random>` | Default skins list: Mojang usernames or the literal `<random>` token (random Mojang username on each login) |
| `Security.urlAllowlistEnabled` | Boolean | `false` | Enable URL domain allowlist for `/skin set web` (empty list = deny all) |
| `Security.urlAllowlistDomains` | String[] | 9 default domains | Domains allowed for `/skin set web` (eTLD+1 suffix match; one entry covers all subdomains) |
| `Permissions.op_level.mojang` | Integer | `0` | Required op level for `/skin set <mojang>` |
| `Permissions.op_level.url` | Integer | `2` | Required op level for `/skin set web` |
| `Permissions.op_level.clear` | Integer | `0` | Required op level for `/skin clear` |
| `Permissions.op_level.random` | Integer | `0` | Required op level for `/skin set random` |
| `Permissions.op_level.other` | Integer | `2` | Required op level for changing another player's skin |
| `Permissions.op_level.metrics` | Integer | `2` | Required op level for `/skin metrics` |
| `Permissions.op_level.metrics_reset` | Integer | `2` | Required op level for `/skin metrics cleanup/reset` |

All message strings are customizable. Defaults (keys under the `Messages` section):

| Key | Default |
|-----|---------|
| `Messages.messages_change` | `Skin change queued` |
| `Messages.messages_fulfilled` | `Skin has been applied.` |
| `Messages.messages_timeout` | `Skin fetch timed out.` |
| `Messages.messages_error` | `Skin fetch failed.` |
| `Messages.messages_restored_from` | `Skin restored from %s` |
| `Messages.messages_cleared_no_profile` | `Skin cleared (no Mojang profile found)` |
| `Messages.messages_no_source` | `No source available` |
| `Messages.messages_player_only` | `Player only command` |
| `Messages.messages_permission_denied` | `Permission denied` |
| `Messages.messages_cooldown` | `Please wait %ds before using /skin again` |
| `Messages.messages_rate_limited` | `Too many /skin commands. Try again later.` |
| `Messages.messages_no_skin_found` | `No skin found for "%s"` |
| `Messages.messages_no_skin_found_plain` | `No skin found` |
| `Messages.messages_mineskin_rejected` | `MineSkin rejected the URL` |
| `Messages.messages_no_random_username` | `No random username available` |
| `Messages.messages_provider_no_result` | `Provider returned no result` |
| `Messages.messages_metrics_top_players` | `Top players by refresh count:` |
| `Messages.messages_metrics_refreshes` | ` refreshes` |
| `Messages.messages_metrics_no_refreshes` | `(no refreshes recorded)` |
| `Messages.messages_metrics_cleanup` | `Metrics cleanup: pruned %d stale player entries` |
| `Messages.messages_metrics_reset` | `Metrics reset` |
| `Messages.messages_discord_announce` | `**%s** changed their skin to: \`%s\`` |

Message keys are configurable per-locale via I18nUtils. The Custom messages tree (#144) added 22 keys with per-server config defaults — see [CHANGELOG.md](CHANGELOG.md) for the full list.

### Permissions

The 8 registered permission nodes. EverlastingSkins uses a
multi-plugin abstraction layer:

1. **LuckPerms** (soft-detected): if installed and loaded, its nodes
   take priority.
2. **Forge PermissionAPI**: registered nodes use Forge's defaults.
3. **Vanilla fallback** (via VanillaPermissionService): reads the
   per-command `Permissions.op_level.*` config values when no
   backend is available.

Permission nodes are listed below with their **Forge PermissionAPI
default** (ALL = true, OP = false) and the **vanilla op level
fallback** if Forge isn't present.

- `everlastingskins.command.skin` (default ALL)
- `everlastingskins.command.skin.other` (default OP)
- `everlastingskins.command.skin.url` (default ALL, op 2 via vanilla fallback)
- `everlastingskins.command.skin.clear` (default ALL)
- `everlastingskins.command.skin.source` (default ALL)
- `everlastingskins.command.metrics` (default OP)
- `everlastingskins.command.metrics.reset` (default OP)
- `everlastingskins.bypass.cooldown` (default OP)

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

### Per-Player Locale

Each player sees mod messages in their own Minecraft client language (e.g., a player with French `fr_fr` sees French translations). The locale is read automatically from `clientInformation().language()` and normalized (`en_us` -> `en`); locales not among the 11 built-ins fall back to `Messages.localization` (default `en`).

Configurable in `world/serverconfig/everlastingskins-server.toml` under the `Messages` section.

## 🌐 External Services

| Service | Required | Used For |
|---------|----------|----------|
| Mojang Session Server | No (offline-mode supported) | Resolving usernames to skin data |
| MineSkin API | No (key optional; raises rate limits) | Converting image URLs to skin textures |

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
2. Configure the channel ID in the mod config: `Integration.discordsrv_channel_id = "123456789"`
3. Set `Integration.discordsrv_enabled = true` in the mod config
4. Skin changes will be announced to the configured Discord channel

## 🔨 Building from Source

Requires JDK 21 and Gradle (via the wrapper):

```bash
git clone https://github.com/Levosilimo/Everlasting-Skins
cd Everlasting-Skins
./gradlew :forge-1.21:build
```

Output: `forge-1.21/build/libs/everlastingskins-1.21-2.1.0.jar`

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
