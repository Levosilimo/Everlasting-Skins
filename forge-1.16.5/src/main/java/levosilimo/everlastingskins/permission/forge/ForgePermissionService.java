/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.permission.forge;

import com.mojang.authlib.GameProfile;
import levosilimo.everlastingskins.permission.IPermissionService;
import levosilimo.everlastingskins.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.VanillaPermissionService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;

import java.util.UUID;

/**
 * Forge permission backend for 1.16.5. 1.16.5's Forge ships the legacy
 * string-node permission API (registerNode + hasPermission); the
 * PermissionNode API (PermissionGatherEvent.Nodes, PermissionTypes) only
 * exists from 1.17 onward, so this is a rewrite of the 1.21 backend rather
 * than a name-only port.
 */
public class ForgePermissionService implements IPermissionService {

    private static final String SKIN_NODE = "everlastingskins.command.skin";
    private static final String SKIN_OTHER_NODE = "everlastingskins.command.skin.other";
    private static final String SKIN_URL_NODE = "everlastingskins.command.skin.url";
    private static final String SKIN_CLEAR_NODE = "everlastingskins.command.skin.clear";
    private static final String METRICS_NODE = "everlastingskins.command.metrics";
    private static final String METRICS_RESET_NODE = "everlastingskins.command.metrics.reset";
    private static final String SOURCE_NODE = "everlastingskins.command.skin.source";
    private static final String BYPASS_COOLDOWN_NODE = "everlastingskins.bypass.cooldown";

    public static void registerNodes() {
        PermissionAPI.registerNode(SKIN_NODE, DefaultPermissionLevel.ALL, "Skin command");
        PermissionAPI.registerNode(SKIN_OTHER_NODE, DefaultPermissionLevel.OP, "Skin command for other players");
        PermissionAPI.registerNode(SKIN_URL_NODE, DefaultPermissionLevel.ALL, "Skin URL command");
        PermissionAPI.registerNode(SKIN_CLEAR_NODE, DefaultPermissionLevel.ALL, "Skin clear command");
        PermissionAPI.registerNode(METRICS_NODE, DefaultPermissionLevel.OP, "Metrics command");
        PermissionAPI.registerNode(METRICS_RESET_NODE, DefaultPermissionLevel.OP, "Metrics reset command");
        PermissionAPI.registerNode(SOURCE_NODE, DefaultPermissionLevel.ALL, "Skin source command");
        PermissionAPI.registerNode(BYPASS_COOLDOWN_NODE, DefaultPermissionLevel.OP, "Bypass skin cooldown");
        PermissionServiceManager.registerService(new ForgePermissionService());
    }

    @Override
    public boolean hasPermission(PermissionContext context, String permissionNode) {
        ServerPlayer player = resolvePlayer(context.uuid());
        try {
            if (player != null) {
                return PermissionAPI.hasPermission(player, permissionNode);
            }
            // Offline path: 1.16.5's API takes a GameProfile (the player
            // does not have to be online) and an optional context.
            return PermissionAPI.hasPermission(new GameProfile(context.uuid(), ""), permissionNode, null);
        } catch (Exception e) {
            return new VanillaPermissionService().hasPermission(context, permissionNode);
        }
    }

    private ServerPlayer resolvePlayer(UUID uuid) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return null;
            return server.getPlayerList().getPlayer(uuid);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getActiveBackendName() {
        return "Forge PermissionAPI (1.16.5)";
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
