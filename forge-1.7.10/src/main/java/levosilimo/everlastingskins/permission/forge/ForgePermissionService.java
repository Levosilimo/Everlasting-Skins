/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission.forge;

import levosilimo.everlastingskins.permission.IPermissionService;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.VanillaPermissionService;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * 1.7.10 permission adapter.
 *
 * <p>1.7.10 has no Forge PermissionAPI, no {@code UserListOps} (1.8+) and no
 * per-player permission-node registry; the ops model is the vanilla
 * Configuration op-permission-level list, surfaced by
 * {@link EntityPlayerMP#canCommandSenderUseCommand(int, String)} (the same
 * method 1.8.9 uses). The shared {@code hasPermission(UUID, int, String)}
 * contract is therefore mapped by node suffix: the node's required op level
 * (from {@link VanillaPermissionService#requiredOpLevel}) is checked against
 * the player's actual op level via {@code canCommandSenderUseCommand}.
 *
 * <p>Skin storage is keyed by UUID only (memory #1123): the player is
 * resolved by UUID at the binding boundary and never passed into :common.
 * 1.7.10 has no {@code getPlayerByUUID} — {@code func_152612_a} is
 * {@code getPlayerByUsername(String)} — so the online list
 * ({@code ServerConfigurationManager.playerEntityList}) is iterated and
 * matched on {@code getUniqueID()} (MCP stable_12).
 *
 * <p>Fail-open pre-boot: when no player context exists (no server, unit
 * tests, pre-login), the check falls back to the vanilla per-node op levels
 * instead of failing closed; {@link PermissionServiceManager} itself is
 * fail-closed until a backend is registered.
 */
public class ForgePermissionService implements IPermissionService {

    /** Test seam: replaces the static {@link MinecraftServer#getServer()} lookup. */
    private static volatile MinecraftServer serverOverride;

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
            return new VanillaPermissionService().hasPermission(uuid, opLevel, permissionNode);
        }
        return player.canCommandSenderUseCommand(
            VanillaPermissionService.requiredOpLevel(permissionNode), permissionNode);
    }

    private EntityPlayerMP resolvePlayer(UUID uuid) {
        try {
            MinecraftServer server = serverOverride != null ? serverOverride : MinecraftServer.getServer();
            if (server == null || server.getConfigurationManager() == null) return null;
            for (Object o : server.getConfigurationManager().playerEntityList) {
                if (o instanceof EntityPlayerMP) {
                    EntityPlayerMP candidate = (EntityPlayerMP) o;
                    if (uuid.equals(candidate.getUniqueID())) return candidate;
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
        return "Forge ops (1.7.10)";
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
