/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission.forge;

import levosilimo.everlastingskins.permission.IPermissionService;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.src.EntityPlayerMP;

import java.util.UUID;

/**
 * 1.6.4 permission adapter.
 *
 * <p>1.6.4 has no Forge PermissionAPI and no per-player permission-node
 * registry; the ops model is the vanilla Configuration op-permission-level
 * list, surfaced by
 * {@link EntityPlayerMP#canCommandSenderUseCommand(int, String)} (the same
 * method 1.7.10/1.8.9 use). The shared {@code hasPermission(UUID, int, String)}
 * contract is mapped by node suffix: the node's required op level is checked
 * against the player's actual op level via {@code canCommandSenderUseCommand}.
 *
 * <p>Skin storage is keyed by UUID only (memory #1123), and 1.6.4 has no
 * player-resolvable account UUID — the lane's username bridge
 * ({@link SkinRestorer#uuidOf(String)}) derives the key from
 * {@code getCommandSenderName()} at the binding boundary. The player is
 * resolved by iterating the online list
 * ({@code ServerConfigurationManager.playerEntityList}) and matching the
 * derived UUID; 1.6.4 has no {@code getPlayerByUUID} (func_152612_a is 1.7.10+).
 *
 * <p>Fail-open pre-boot: when no player context exists (no server, unit
 * tests, pre-login), the check falls back to the vanilla per-node op levels
 * instead of failing closed; {@link PermissionServiceManager} itself is
 * fail-closed until a backend is registered.
 */
public class ForgePermissionService implements IPermissionService {

    /** Test seam: replaces the static {@link MinecraftServer#getServer()} lookup. */
    private static volatile MinecraftServer serverOverride;

    /** Required op level for a node (0 = any player, 2 = op), mirroring 1.7.10. */
    public static int requiredOpLevel(String permissionNode) {
        switch (permissionNode) {
            case "everlastingskins.command.skin":
            case "everlastingskins.command.skin.clear":
            case "everlastingskins.command.skin.random":
                return 0;
            case "everlastingskins.command.skin.url":
            case "everlastingskins.command.skin.other":
            case "everlastingskins.command.metrics":
            case "everlastingskins.command.metrics.reset":
                return 2;
            default:
                return 0;
        }
    }

    public static void register() {
        PermissionServiceManager.registerService(new ForgePermissionService());
    }

    @Override
    public boolean hasPermission(UUID uuid, int opLevel, String permissionNode) {
        if (permissionNode.endsWith(".source")) return true;
        EntityPlayerMP player = resolvePlayer(uuid);
        if (player == null) {
            // No server/player context (unit tests, pre-boot): keep the
            // vanilla per-node op levels instead of failing closed.
            return opLevel >= requiredOpLevel(permissionNode);
        }
        return player.canCommandSenderUseCommand(
            requiredOpLevel(permissionNode), permissionNode);
    }

    private EntityPlayerMP resolvePlayer(UUID uuid) {
        try {
            MinecraftServer server = serverOverride != null ? serverOverride : MinecraftServer.getServer();
            if (server == null || server.getConfigurationManager() == null) return null;
            for (Object o : server.getConfigurationManager().playerEntityList) {
                if (o instanceof EntityPlayerMP) {
                    EntityPlayerMP candidate = (EntityPlayerMP) o;
                    if (uuid.equals(SkinRestorer.uuidOf(candidate.getCommandSenderName()))) {
                        return candidate;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Test seam — mirrors the mc1.12.2 SkinRestorer.setServer pattern. */
    static void setServerOverride(MinecraftServer server) {
        serverOverride = server;
    }

    @Override
    public String getActiveBackendName() {
        return "Forge ops (1.6.4)";
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
