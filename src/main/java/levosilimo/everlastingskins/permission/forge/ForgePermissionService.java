/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.permission.forge;

import levosilimo.everlastingskins.permission.IPermissionService;
import levosilimo.everlastingskins.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
            (player, uuid, context) -> player != null && player.hasPermissions(2));

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
            (player, uuid, context) -> player != null && player.hasPermissions(2));

    public static final PermissionNode<Boolean> METRICS_RESET_NODE =
        new PermissionNode<>("everlastingskins", "command.metrics.reset",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && player.hasPermissions(2));

    public static void registerNodes() {
        PermissionServiceManager.registerService(new ForgePermissionService());
    }

    public static void onPermissionGather(PermissionGatherEvent.Nodes event) {
        event.addNodes(SKIN_NODE, SKIN_OTHER_NODE, SKIN_URL_NODE, SKIN_CLEAR_NODE,
                METRICS_NODE, METRICS_RESET_NODE);
    }

    @Override
    public boolean hasPermission(PermissionContext context, String permissionNode) {
        PermissionNode<Boolean> node;
        if (permissionNode.endsWith(".skin.other")) {
            node = SKIN_OTHER_NODE;
        } else if (permissionNode.endsWith(".skin.url")) {
            node = SKIN_URL_NODE;
        } else if (permissionNode.endsWith(".skin.clear")) {
            node = SKIN_CLEAR_NODE;
        } else if (permissionNode.endsWith(".skin.source")) {
            return true;
        } else if (permissionNode.endsWith(".metrics.reset")) {
            node = METRICS_RESET_NODE;
        } else if (permissionNode.endsWith(".metrics")) {
            node = METRICS_NODE;
        } else {
            node = SKIN_NODE;
        }
        // Prefer the live permission of the online player (PermissionAPI consults
        // the registered handler, so non-op grants are honored — not just ops).
        ServerPlayer player = resolvePlayer(context.uuid());
        if (player != null) {
            return PermissionAPI.getPermission(player, node);
        }
        try {
            return PermissionAPI.getOfflinePermission(context.uuid(), node);
        } catch (Exception e) {
            return context.isOp();
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
        return "Forge PermissionAPI (1.21)";
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
