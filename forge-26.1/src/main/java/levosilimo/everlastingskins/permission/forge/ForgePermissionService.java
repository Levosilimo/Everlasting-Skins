/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.permission.forge;

import levosilimo.everlastingskins.permission.IPermissionService;
import levosilimo.everlastingskins.forge26_1.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.forge26_1.permission.VanillaPermissionService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import java.util.UUID;

public class ForgePermissionService implements IPermissionService {

    public static final PermissionNode<Boolean> SKIN_NODE =
        new PermissionNode<>("everlastingskins", "command.skin",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> true);

    public static final PermissionNode<Boolean> SKIN_OTHER_NODE =
        new PermissionNode<>("everlastingskins", "command.skin.other",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && hasCommandLevel(player, 2));

    public static final PermissionNode<Boolean> SKIN_URL_NODE =
        new PermissionNode<>("everlastingskins", "command.skin.url",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> true);

    public static final PermissionNode<Boolean> SKIN_CLEAR_NODE =
        new PermissionNode<>("everlastingskins", "command.skin.clear",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> true);

    public static final PermissionNode<Boolean> METRICS_NODE =
        new PermissionNode<>("everlastingskins", "command.metrics",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && hasCommandLevel(player, 2));

    public static final PermissionNode<Boolean> METRICS_RESET_NODE =
        new PermissionNode<>("everlastingskins", "command.metrics.reset",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && hasCommandLevel(player, 2));

    public static final PermissionNode<Boolean> SOURCE_NODE =
        new PermissionNode<>("everlastingskins", "command.skin.source",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> true);

    public static final PermissionNode<Boolean> BYPASS_COOLDOWN_NODE =
        new PermissionNode<>("everlastingskins", "bypass.cooldown",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && hasCommandLevel(player, 2));

    public static void registerNodes() {
        PermissionServiceManager.registerService(new ForgePermissionService());
    }

    public static void onPermissionGather(PermissionGatherEvent.Nodes event) {
        event.addNodes(SKIN_NODE, SKIN_OTHER_NODE, SKIN_URL_NODE, SKIN_CLEAR_NODE,
                METRICS_NODE, METRICS_RESET_NODE, SOURCE_NODE, BYPASS_COOLDOWN_NODE);
    }

    /**
     * 26.2 permission model: the player's {@code PermissionSet} is probed
     * with a {@code HasCommandLevel} permission instead of the removed
     * {@code hasPermissions(int)}.
     */
    static boolean hasCommandLevel(ServerPlayer player, int level) {
        return player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }

    @Override
    public boolean hasPermission(UUID uuid, int opLevel, String permissionNode) {
        PermissionNode<Boolean> node;
        if (permissionNode.endsWith(".skin.other")) {
            node = SKIN_OTHER_NODE;
        } else if (permissionNode.endsWith(".skin.url")) {
            node = SKIN_URL_NODE;
        } else if (permissionNode.endsWith(".skin.clear")) {
            node = SKIN_CLEAR_NODE;
        } else if (permissionNode.endsWith(".skin.source")) {
            node = SOURCE_NODE;
        } else if (permissionNode.endsWith(".bypass.cooldown")) {
            node = BYPASS_COOLDOWN_NODE;
        } else if (permissionNode.endsWith(".metrics.reset")) {
            node = METRICS_RESET_NODE;
        } else if (permissionNode.endsWith(".metrics")) {
            node = METRICS_NODE;
        } else {
            node = SKIN_NODE;
        }
        // Prefer the live permission of the online player (PermissionAPI consults
        // the registered handler, so non-op grants are honored — not just ops).
        ServerPlayer player = resolvePlayer(uuid);
        try {
            if (player != null) {
                return PermissionAPI.getPermission(player, node);
            }
            return PermissionAPI.getOfflinePermission(uuid, node);
        } catch (Exception e) {
            return new VanillaPermissionService().hasPermission(uuid, opLevel, permissionNode);
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
        return "Forge PermissionAPI (26.2)";
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
