/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission.forge;

import levosilimo.everlastingskins.permission.IPermissionService;
import levosilimo.everlastingskins.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;

import java.util.UUID;

public class ForgePermissionService implements IPermissionService {

    private static final String NODE_PREFIX = "everlastingskins.command";
    private static final String SKIN_NODE = NODE_PREFIX + ".skin";
    private static final String SKIN_OTHER_NODE = NODE_PREFIX + ".skin.other";
    private static final String SKIN_URL_NODE = NODE_PREFIX + ".skin.url";
    private static final String SKIN_CLEAR_NODE = NODE_PREFIX + ".skin.clear";
    private static final String METRICS_NODE = NODE_PREFIX + ".metrics";
    private static final String METRICS_RESET_NODE = NODE_PREFIX + ".metrics.reset";
    private static boolean registered = false;

    /** Must run during INIT — Forge's PermissionAPI rejects node registration before FMLInitializationEvent. */
    public static void registerNodes(FMLInitializationEvent event) {
        if (registered) return;
        PermissionAPI.registerNode(SKIN_NODE, DefaultPermissionLevel.ALL, "Change own skin");
        PermissionAPI.registerNode(SKIN_OTHER_NODE, DefaultPermissionLevel.OP, "Change another player's skin");
        PermissionAPI.registerNode(SKIN_URL_NODE, DefaultPermissionLevel.ALL, "Set own skin from URL");
        PermissionAPI.registerNode(SKIN_CLEAR_NODE, DefaultPermissionLevel.ALL, "Clear own skin");
        PermissionAPI.registerNode(METRICS_NODE, DefaultPermissionLevel.OP, "View skin metrics");
        PermissionAPI.registerNode(METRICS_RESET_NODE, DefaultPermissionLevel.OP, "Reset or clean up skin metrics");
        registered = true;
        PermissionServiceManager.registerService(new ForgePermissionService());
    }

    @Override
    public boolean hasPermission(PermissionContext context, String permissionNode) {
        if (permissionNode.endsWith(".source")) return true;
        EntityPlayerMP player = resolvePlayer(context.uuid());
        if (player == null) {
            // No server/player context (e.g. unit tests, pre-boot): keep the
            // vanilla op semantics instead of failing closed.
            return context.isOp();
        }
        return PermissionAPI.hasPermission(player, permissionNode);
    }

    private EntityPlayerMP resolvePlayer(UUID uuid) {
        try {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) return null;
            return server.getPlayerList().getPlayerByUUID(uuid);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getActiveBackendName() {
        return "Forge PermissionAPI (1.12)";
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
