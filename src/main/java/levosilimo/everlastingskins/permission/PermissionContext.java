/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.permission;

import net.minecraft.server.level.ServerPlayer;

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

    private static int effectiveOpLevel(ServerPlayer player) {
        if (player == null) return 0;
        return player.server.getProfilePermissions(player.getGameProfile());
    }
}
