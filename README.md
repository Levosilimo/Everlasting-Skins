# EverlastingSkins

<!-- Badges: shield.io patterns. Live status unknown until CI runs first time; use static placeholder for now. -->

Server-side Minecraft Forge mod for persistent custom skins. Players change their skin with `/skin` — no client mod required. Skins survive server restarts.

## ✨ Features

- `/skin set mojang <name>` — apply any Mojang-registered username's skin
- `/skin set web <classic|slim> <url>` — generate a skin from an image URL (MineSkin integration, config-gated)
- `/skin set random` — apply a random skin
- `/skin clear` — restore your Mojang-registered skin (or your UUID-hash default if offline)
- `/skin source` — show which username/source your current skin is from
- Skins persist across server restarts (per-player JSON files)
- Server-side only — no client-side install needed

## 📦 Installation

1. Install [Forge for Minecraft 1.21](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.21.html).
2. Download `EverlastingSkins-1.21-2.0.0.jar` from the [Releases page](https://github.com/Levosilimo/Everlasting-Skins/releases).
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

## ⚙️ Configuration

Config file: `world/serverconfig/everlastingskins-server.toml` (auto-generated on first run).

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `messages.localization` | String | `en` | Language for mod messages |
| `messages.display` | Boolean | `true` | Show skin application messages in chat |
| `mineskin.key` | String | (empty) | MineSkin API key (required for `/skin set web`) |
| `mineskin.enabled` | Boolean | `false` | Enable MineSkin URL-based skin generation |

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

Output: `build/libs/EverlastingSkins-1.21-2.0.0.jar`

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


