# Everlasting Skins

Server-side persistent custom skins for Forge — no client mod on 1.7.10+;
on 1.4.7–1.6.4 install the same JAR on the client too.

[![CurseForge](https://cf.way2muchnoise.eu/versions/538149.svg)](https://www.curseforge.com/minecraft/mc-mods/everlasting-skins)
[![Modrinth](https://img.shields.io/modrinth/dt/everlasting-skins?label=Modrinth)](https://modrinth.com/mod/everlasting-skins)
[![Release](https://img.shields.io/github/v/release/Levosilimo/Everlasting-Skins?include_prereleases&label=latest)](https://github.com/Levosilimo/Everlasting-Skins/releases)
[![License](https://img.shields.io/github/license/Levosilimo/Everlasting-Skins)](LICENSE)

## What it does

Everlasting Skins gives players custom skins that persist on pure Forge
servers. A player sets a skin once; the server stores it and re-applies it on
every login and across server restarts. **On 1.7.10 and newer it is
server-side only — players do not need to install anything on their client.**
On 1.4.7–1.6.4 (pre-1.8) the same mod JAR must also be installed on the
client (see [Pre-1.8 client install](#pre-18-client-install)).

## Install

1. Pick your Minecraft version from the table below.
2. Download the matching `everlastingskins-<mc>-<version>.jar` from
   [CurseForge], [Modrinth], or [Releases].
3. Drop the JAR into your server's `mods/` folder.
4. Restart the server.

The server must already run Forge for your Minecraft version. That is all —
no client mod, no plugins — on 1.7.10 and newer. Pre-1.8 versions
(1.4.7–1.6.4) additionally need the same JAR in the client's `mods/` folder;
see [Pre-1.8 client install](#pre-18-client-install).

### Supported versions

| Minecraft | Forge | Status | Get it |
|---|---|---|---|
| 1.21 / 1.21.1 / 1.21.4 / 1.21.8 | 51–58 | Stable | [CurseForge] · [Modrinth] |
| 26.1 / 26.2 | 62 / 65 | Stable | [CurseForge] · [Modrinth] |
| 1.16.5 / 1.18.2 / 1.20.1 | 36 / 40 / 47 | Supported | [CurseForge] · [Modrinth] |
| 1.12.2, 1.10.2, 1.8.9, 1.7.10, 1.6.4, 1.5.2, 1.4.7 | legacy | Legacy | [CurseForge] · [Releases] |

Legacy Forge builds: 14.23.5 (1.12.2), 12.18.3.2511 (1.10.2), 11.15.1.2318
(1.8.9), 10.13.4.1614 (1.7.10), 9.11.1.1345 (1.6.4), 7.8.1.738 (1.5.2),
6.6.2.534 (1.4.7). 1.5.2 ships as a beta and is on Modrinth + GitHub Releases
only (not yet on CurseForge).

Need a version you do not see? Open an [issue].

## First use

In-game, type:

```
/skin set mojang <yourMinecraftName>
```

Your skin is applied immediately and stays applied on every login, server
restart, and even if the server runs in offline mode.

## Commands

Every supported version shares the same command surface:

| Command | Description |
|---|---|
| `/skin set mojang <name> [targets]` | Apply a Mojang account's skin by username |
| `/skin set web classic\|slim <url> [targets]` | Generate a skin from an image URL (MineSkin) |
| `/skin set random [cape] [variant] [targets]` | Apply a random skin (optionally with a cape or a specific variant) |
| `/skin source [target]` | Show where your current skin comes from |
| `/skin clear [targets]` | Restore your Mojang-registered skin (or your default if offline) |
| `/skin metrics [json\|players\|cleanup\|reset]` | Per-player skin metrics (admin) |

`[targets]` is one or more player names and requires the "other" permission
(see below). There is no `/skin reset` — `clear` is the command.

### Version notes

- **1.7.10 and newer** (1.8.9, 1.10.2, 1.12.2, 1.16.5, 1.18.2, 1.20.1, 1.21.x,
  26.x): the full surface above. Skins render through GameProfile textures —
  no client mod needed.
- **1.4.7 – 1.6.4 (pre-1.8):** the same surface minus one era limitation:
  `/skin set web` is rejected with an explanation (URL-based skin generation
  does not exist on this line). `/skin set random` picks and stores a random
  skin like everywhere else. Rendering needs the mod on the client too
  ([below](#pre-18-client-install)) — a vanilla client sees the default
  Steve skin, because the custom channel is dropped.

### Pre-1.8 client install

These versions have no GameProfile textures, so the server alone cannot make
custom skins appear. Install the same `everlastingskins-<mc>-<version>.jar`
in the client's `mods/` folder as well — the joint client mod then renders
stored skins through its own channel (cape rendering is in progress). The
server still stores and clears skins without the client JAR, but players
without it see the default Steve skin.

## Permissions

- By default every player can use `/skin set mojang`, `/skin set random`,
  `/skin clear`, and `/skin source`.
- Operator level 2 (or the matching node) is required for changing another
  player's skin (`[targets]`), `/skin set web`, and `/skin metrics`.
- Permission nodes: `everlastingskins.command.skin`,
  `everlastingskins.command.skin.other`, `everlastingskins.command.skin.url`,
  `everlastingskins.command.skin.clear`, `everlastingskins.command.skin.source`,
  `everlastingskins.command.metrics`, `everlastingskins.command.metrics.reset`,
  `everlastingskins.bypass.cooldown`.
- [LuckPerms](https://luckperms.net/) is detected automatically; without a
  permission plugin the mod falls back to op levels. The per-command op levels
  are configurable in `world/serverconfig/everlastingskins-server.toml`
  (generated on first run). On 1.4.7–1.6.4 there are no per-player nodes —
  the op model is the only gate.

## Offline mode

The mod works with `online-mode=false`. Players without a saved custom skin
get their UUID-hash default skin. `/skin set mojang`, `web`, and `random`
need an internet connection to fetch skins; in a fully offline environment
only `clear` and `source` are guaranteed to respond.

## Troubleshooting

- **Skin not showing:** another skin mod that modifies player GameProfiles is
  incompatible — remove it. Anti-cheat plugins may also need the skin packet
  sequences whitelisted.
- **`/skin set mojang <name>` reports "No skin found":** the username does not
  exist on Mojang (typo, unverified account, or an offline-mode-only username).
- **Mod not loading:** check the server log for `EverlastingSkins`; the config
  file `world/serverconfig/everlastingskins-server.toml` is only generated
  after the mod loads successfully.

## Uninstall

Remove the JAR from `mods/` and restart the server. Stored skins remain in
`world/EverlastingSkins/` — delete that folder if you want the data gone.

## Links

[CurseForge] · [Modrinth] · [Releases] · [Issues] · [Source]

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Build the shared core with
`./gradlew :common:build`; the full lane matrix, conventions, and CI setup
live in [AGENTS.md](AGENTS.md).

MIT License — see [LICENSE](LICENSE).

[CurseForge]: https://www.curseforge.com/minecraft/mc-mods/everlasting-skins
[Modrinth]: https://modrinth.com/mod/everlasting-skins
[Releases]: https://github.com/Levosilimo/Everlasting-Skins/releases
[Issues]: https://github.com/Levosilimo/Everlasting-Skins/issues
[Source]: https://github.com/Levosilimo/Everlasting-Skins
