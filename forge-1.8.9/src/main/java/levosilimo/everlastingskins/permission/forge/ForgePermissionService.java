/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission.forge;

import com.mojang.authlib.GameProfile;
import levosilimo.everlastingskins.permission.IPermissionService;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.UserListOps;
import net.minecraft.server.management.UserListOpsEntry;

import java.util.UUID;

/**
 * 1.8.9 permission adapter over the legacy op surface.
 *
 * Command-node path: the lane's {@code ICommand} gates through
 * {@link net.minecraft.command.ICommandSender#canCommandSenderUseCommand(int, String)}
 * (the 1.8.9 op-level check), which feeds the {@code opLevel} half of the
 * {@code (UUID, opLevel)} contract. This service resolves the player's
 * actual op level from the server's {@link UserListOps} (the Ops list) via
 * {@code MinecraftServer.getServer().getConfigurationManager().getOppedPlayers()}
 * — {@link UserListOps#getEntry(GameProfile)} is keyed by the profile UUID
 * ({@code getObjectKey} returns the UUID string), so a name-less
 * {@code GameProfile(uuid, null)} lookup works.
 *
 * No-op-server fallback (unit tests, pre-boot): the check degrades to the
 * vanilla op-level gate (required level 0 grants, level &gt; 0 denies).
 */
public class ForgePermissionService implements IPermissionService {

    private static final String NODE_PREFIX = "everlastingskins.command";
    public static final String SKIN_NODE = NODE_PREFIX + ".skin";
    public static final String SKIN_OTHER_NODE = NODE_PREFIX + ".skin.other";
    public static final String SKIN_URL_NODE = NODE_PREFIX + ".skin.url";
    public static final String SKIN_CLEAR_NODE = NODE_PREFIX + ".skin.clear";
    public static final String SKIN_SOURCE_NODE = NODE_PREFIX + ".skin.source";
    public static final String BYPASS_COOLDOWN_NODE = "everlastingskins.bypass.cooldown";
    public static final String METRICS_NODE = NODE_PREFIX + ".metrics";
    public static final String METRICS_RESET_NODE = NODE_PREFIX + ".metrics.reset";

    /** Register this backend with the fail-closed manager (highest priority wins). */
    public static void register() {
        PermissionServiceManager.registerService(new ForgePermissionService());
    }

    @Override
    public boolean hasPermission(UUID uuid, int opLevel, String permissionNode) {
        if (permissionNode.endsWith(".source")) return true;
        int playerOpLevel = resolveOpLevel(uuid);
        if (playerOpLevel < 0) {
            // No server context (unit tests, pre-boot): vanilla op-level gate.
            return opLevel == 0;
        }
        return playerOpLevel >= opLevel;
    }

    /**
     * Player op level from the Ops list, or -1 when no server context is
     * available. Protected seam: tests subclass and override this so the
     * gating logic is unit-testable without a live server.
     */
    protected int resolveOpLevel(UUID uuid) {
        try {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null || server.getConfigurationManager() == null) return -1;
            UserListOps ops = server.getConfigurationManager().getOppedPlayers();
            if (ops == null) return -1;
            UserListOpsEntry entry = ops.getEntry(new GameProfile(uuid, null));
            return entry != null ? entry.getPermissionLevel() : 0;
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public String getActiveBackendName() {
        return "Forge ops (1.8.9)";
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
