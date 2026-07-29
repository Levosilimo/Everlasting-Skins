# EverlastingSkins

Server-side Minecraft Forge mod for persistent custom skins. Players can set their skin from a Mojang username, a URL via MineSkin, or a random selection — no client mod required.

## Supported versions

| Branch | Minecraft | Forge | Java | Status |
|--------|-----------|-------|------|--------|
| `1.21` | 1.21 | 51.0.8 | 21 | Current reference implementation |

Planned: `mc1.12.2` (Forge 1.12.2, Java 8). Earlier versions are not supported; see [porting policy](AGENTS.md#porting-policy).

## Installation

1. Download the compiled JAR from [GitHub Releases](https://github.com/Levosilimo/EverlastingSkins/releases).
2. Place it in your Forge server's `mods/` directory.
3. Restart the server.
4. (Optional) Configure a [MineSkin API key](#mineskin-api-key) in the server config file at `server/config/everlastingskins-common.toml`.

No client-side installation is required. The mod operates entirely on the server.

## Commands

All commands use the `/skin` root. Arguments in `<>` are required; `[cape]` and `[variant]` are optional.

| Command | Permission | Description |
|---------|------------|-------------|
| `/skin set mojang <name> [targets…]` | 0 (self) / 3 (others) | Copy the skin from an existing Mojang account by username |
| `/skin set web <classic\|slim> <url> [targets…]` | 0 (self) / 3 (others) | Generate a skin from an image URL via MineSkin |
| `/skin set random [cape] [variant] [targets…]` | 0 (self) / 3 (others) | Apply a random skin from Mojang profiles |
| `/skin clear [targets…]` | 0 (self) / 3 (others) | Reset to the default skin |
| `/skin source [player]` | 0 | Show the origin of the current skin (Mojang username, URL, or "Skin is not set") |

**Variants:** `classic` (64×64, 4px arms), `slim` (64×64, 3px arms), `all` (either).

When an operator targets other players (`targets`), the command notifies each target with a message.

## External services

The mod contacts the following services over HTTPS:

| Service | Purpose | Required |
|---------|---------|----------|
| `sessionserver.mojang.com` | Fetch profile textures by UUID (Mojang session server) | Yes |
| `api.ashcon.app` (Eclipse) | UUID/username resolution and profile lookups (primary) | Yes |
| `api.minetools.eu` | UUID/username resolution and profile lookups (fallback) | Yes |
| `api.mineskin.org` | Convert uploaded image URLs into Minecraft skin textures | Only for `/skin set web` |
| `mskins.net` | Random skin selection and cape lookup (HTML scraping) | No — feature-conditional |

Provider fallback order for UUID/name resolution: Eclipse → Mojang → MineTools.

## MineSkin API key

`/skin set web` requires a MineSkin API key to avoid rate limits. Keys are free and can be obtained at [https://mineskin.org/apikeys](https://mineskin.org/apikeys).

Set the key in `server/config/everlastingskins-common.toml`:

```toml
[Messages]
key = "your-mineskin-api-key"
```

Leaving the key empty (`""`) uses the unauthenticated MineSkin endpoint, which is heavily rate-limited.

## Storage

### Skin data

Per-player skin data is stored as individual JSON files:

```
<server-directory>/EverlastingSkins/<uuid>.json
```

Each file contains the player's texture property, signature, source string, and variant. Writes are not yet atomic — in-progress writes may produce partial files on crash.

### Localization overrides

Built-in localizations are provided for English (`en`), Russian (`ru`), and Ukrainian (`uk`). Override any string by placing a properties file at:

```
<server-directory>/config/EverlastingSkins/<locale-code>
```

Where `<locale-code>` matches one of `en`, `ru`, `uk`. The mod creates these files automatically on first run with the default values; edit them to override.

The server language is set in the common config:

```toml
[Messages]
localization = "en"
```

## Configuration

All config values are in `server/config/everlastingskins-common.toml`:

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `Messages.localization` | String | `"en"` | Language code (`en`, `ru`, `uk`) |
| `Messages.display` | Boolean | `true` | Whether to show mod feedback messages in chat |
| `Messages.key` | String | `""` | MineSkin API key for URL-based skin generation |

## Building from source

Requires JDK 21 and a network connection.

```bash
# Clone
git clone https://github.com/Levosilimo/EverlastingSkins.git
cd EverlastingSkins

# The gradlew wrapper currently has CRLF line endings and is not executable
# on Linux. Fix before first build:
sed -i 's/\r$//' gradlew && chmod +x gradlew

# Build
./gradlew build
```

The compiled JAR is at `build/libs/EverlastingSkins-1.21-1.0.jar`.

**Known issue:** The `gradlew` wrapper has CRLF line endings and a missing executable bit on checkout. This is tracked in Phase 0 of [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

## License

MIT — see [gradle.properties](gradle.properties). (Note: `mods.toml` currently lists "All rights reserved"; this is a metadata inconsistency being reconciled.)

## Contributing

See [AGENTS.md](AGENTS.md) for the product direction and porting policy, and [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for the phased development plan.

This repository uses a pre-commit hook that runs `aislop scan --staged`. After cloning, run:

```bash
git config core.hooksPath .githooks
```

See [AGENTS.md#pre-commit-quality-gate](AGENTS.md#pre-commit-quality-gate) for details.
