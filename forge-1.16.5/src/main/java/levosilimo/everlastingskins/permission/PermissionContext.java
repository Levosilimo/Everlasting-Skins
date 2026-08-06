/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.permission;

import net.minecraft.entity.player.ServerPlayerEntity;

import java.util.Objects;
import java.util.UUID;

/**
 * Lightweight context for permission checks. Decouples the permission system
 * from Minecraft's ServerPlayer/EntityPlayerMP, which can't be instantiated
 * in unit tests due to EntityDataSerializers static init.
 *
 * <p>Java 8 port note: this is the 1.21 {@code record} flattened to a
 * final class — the 1.16.5 lane compiles at source level 8.
 *
 * @param uuid    the player
 * @param opLevel the player's effective op level (0-4, 0 = not an op)
 */
public final class PermissionContext {

    private final UUID uuid;
    private final int opLevel;

    public PermissionContext(UUID uuid, int opLevel) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        if (opLevel < 0 || opLevel > 4) {
            throw new IllegalArgumentException("opLevel must be 0-4, got " + opLevel);
        }
        this.opLevel = opLevel;
    }

    public UUID uuid() {
        return uuid;
    }

    public int opLevel() {
        return opLevel;
    }

    public static PermissionContext of(UUID uuid, int opLevel) {
        return new PermissionContext(uuid, opLevel);
    }

    public static PermissionContext of(UUID uuid, ServerPlayerEntity player) {
        return new PermissionContext(uuid, effectiveOpLevel(player));
    }

    private static int effectiveOpLevel(ServerPlayerEntity player) {
        if (player == null) return 0;
        return player.getServer().getProfilePermissions(player.getGameProfile());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PermissionContext)) return false;
        PermissionContext that = (PermissionContext) o;
        return opLevel == that.opLevel && uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, opLevel);
    }

    @Override
    public String toString() {
        return "PermissionContext{uuid=" + uuid + ", opLevel=" + opLevel + '}';
    }
}
