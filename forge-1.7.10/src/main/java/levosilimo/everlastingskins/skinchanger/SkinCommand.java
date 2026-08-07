/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.GameProfile;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The 1.7.10 {@code /skin} command.
 *
 * <p>1.7.10 ICommand surface (MCP stable_12): {@code getCommandName} /
 * {@code getCommandAliases} / {@code getCommandUsage} / {@code processCommand}
 * / {@code canCommandSenderUseCommand} — there is no
 * {@code LiteralArgumentBuilder} (1.13+) and no {@code getName} (that is
 * 1.8+; 1.7.10's accessor is {@code getCommandName}). Sender chat goes
 * through {@link ICommandSender#addChatMessage}.
 *
 * <p>Permission gating is delegated to {@link PermissionServiceManager} (the
 * Forge ops backend resolves the player by UUID — memory #1123: UUID-only
 * keying, never the player object — and maps the node's required op level
 * onto {@code canCommandSenderUseCommand}). The manager fails closed until a
 * backend is registered.
 */
public class SkinCommand implements ICommand {

    private static final String NODE_PREFIX = "everlastingskins.command";
    private static final String NODE_SKIN = NODE_PREFIX + ".skin";
    private static final String NODE_SKIN_CLEAR = NODE_PREFIX + ".skin.clear";
    private static final String NODE_SKIN_SOURCE = NODE_PREFIX + ".skin.source";

    private static volatile MojangAPI mojangApi = new MojangApiHttpImpl();
    private static volatile MinecraftServer serverOverride;

    private final SkinStorageProvider provider;

    public SkinCommand(SkinStorageProvider provider) {
        this.provider = provider;
    }

    @Override
    public String getCommandName() {
        return "skin";
    }

    @Override
    public List getCommandAliases() {
        return Arrays.asList("skins", "setskin");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/skin <set <username>|clear|source> [player]";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        // Dispatch-time gating happens in processCommand via the permission
        // manager (fail-closed); the 1.7.10 ICommand hook is a pre-filter
        // only, mirroring the sibling lanes.
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }
        String action = args[0];
        EntityPlayerMP target = resolveTarget(sender, args);
        if (target == null) {
            sender.addChatMessage(new ChatComponentText("Player not found."));
            return;
        }
        UUID uuid = target.getGameProfile().getId();
        GameProfile profile = target.getGameProfile();

        switch (action) {
            case "clear":
                if (!checkPermission(sender, uuid, NODE_SKIN_CLEAR)) return;
                provider.clearSkin(profile, uuid);
                sender.addChatMessage(new ChatComponentText("Skin cleared."));
                break;
            case "source":
                if (!checkPermission(sender, uuid, NODE_SKIN_SOURCE)) return;
                String source = provider.getSource(uuid);
                sender.addChatMessage(new ChatComponentText(
                    source != null ? "Skin source: " + source : "No custom skin stored."));
                break;
            case "set":
                if (!checkPermission(sender, uuid, NODE_SKIN)) return;
                if (args.length < 2) {
                    sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
                    return;
                }
                setSkin(sender, profile, uuid, args[1]);
                break;
            default:
                sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
        }
    }

    private void setSkin(ICommandSender sender, GameProfile profile, UUID uuid, String username) {
        // 1.7.10 has no per-player permission nodes beyond the op model; the
        // vanilla Mojang lookup is the only authoritative source (no
        // MineSkin/URL generation on this legacy surface).
        MojangAPI api = mojangApi;
        if (api == null) {
            sender.addChatMessage(new ChatComponentText("Skin resolver unavailable."));
            return;
        }
        Optional<MojangSkinDataResult> result = api.getSkin(username);
        if (!result.isPresent()) {
            sender.addChatMessage(new ChatComponentText("Could not resolve a skin for '" + username + "'."));
            return;
        }
        CustomSkinProperty skin = result.get().skinProperty();
        provider.applySkin(profile, uuid, skin);
        sender.addChatMessage(new ChatComponentText("Skin applied."));
    }

    private boolean checkPermission(ICommandSender sender, UUID uuid, String node) {
        int opLevel = sender instanceof EntityPlayerMP ? 4 : 0;
        if (!PermissionServiceManager.hasPermission(uuid, opLevel, node)) {
            sender.addChatMessage(new ChatComponentText("You do not have permission to use this command."));
            return false;
        }
        return true;
    }

    private EntityPlayerMP resolveTarget(ICommandSender sender, String[] args) {
        if (args.length >= 2 && !args[1].isEmpty()) {
            MinecraftServer server = serverOverride != null ? serverOverride : MinecraftServer.getServer();
            if (server != null && server.getConfigurationManager() != null) {
                for (Object o : server.getConfigurationManager().playerEntityList) {
                    if (o instanceof EntityPlayerMP) {
                        EntityPlayerMP p = (EntityPlayerMP) o;
                        if (p.getCommandSenderName().equalsIgnoreCase(args[1])) return p;
                    }
                }
            }
            return null;
        }
        return sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return index == 1;
    }

    @Override
    public int compareTo(Object o) {
        return getCommandName().compareTo(((ICommand) o).getCommandName());
    }

    /** Test seam — deterministic fakes only (memory #1115; no live HTTP). */
    static void setMojangApiForTest(MojangAPI api) {
        mojangApi = api;
    }

    /** Test seam — mirrors the mc1.12.2 SkinRestorer.setServer pattern. */
    static void setServerOverrideForTest(MinecraftServer server) {
        serverOverride = server;
    }
}
