/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.forge26_1.permission;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;

import java.util.Objects;
import java.util.UUID;

/**
 * Lightweight context for permission checks. Decouples the permission system
 * from Minecraft's ServerPlayer/EntityPlayerMP, which can't be instantiated
 * in unit tests due to EntityDataSerializers static init.
 *
 * @param uuid    the player
 * @param opLevel the player's effective op level (0-4, 0 = not an op)
 */
public record PermissionContext(UUID uuid, int opLevel) {

    public PermissionContext {
        Objects.requireNonNull(uuid, "uuid");
        if (opLevel < 0 || opLevel > 4) {
            throw new IllegalArgumentException("opLevel must be 0-4, got " + opLevel);
        }
    }

    public static PermissionContext of(UUID uuid, int opLevel) {
        return new PermissionContext(uuid, opLevel);
    }

    public static PermissionContext of(UUID uuid, ServerPlayer player) {
        return new PermissionContext(uuid, effectiveOpLevel(player));
    }

    /**
     * Derives the effective op level (0-4) from the player's 26.2
     * {@link PermissionSet}. Level-based sets (server ops config) expose the
     * level directly; any other set is probed highest-command-level-first via
     * {@link Permission.HasCommandLevel} so non-op grants are still honored.
     */
    private static int effectiveOpLevel(ServerPlayer player) {
        if (player == null) return 0;
        PermissionSet permissions = player.permissions();
        if (permissions instanceof LevelBasedPermissionSet levelSet) {
            return levelSet.level().id();
        }
        for (int level = 4; level >= 1; level--) {
            if (permissions.hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)))) {
                return level;
            }
        }
        return 0;
    }
}
