/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerList;
import net.minecraft.server.management.UserListOps;
import net.minecraft.server.management.UserListOpsEntry;

import java.util.Objects;
import java.util.UUID;

public final class PermissionContext {
    private final UUID uuid;
    private final int opLevel;

    public PermissionContext(UUID uuid, int opLevel) {
        this.uuid = Objects.requireNonNull(uuid);
        if (opLevel < 0 || opLevel > 4) {
            throw new IllegalArgumentException("opLevel must be 0-4, got " + opLevel);
        }
        this.opLevel = opLevel;
    }

    public UUID uuid() { return uuid; }
    public int opLevel() { return opLevel; }

    public static PermissionContext of(UUID uuid, int opLevel) {
        return new PermissionContext(uuid, opLevel);
    }

    public static PermissionContext of(UUID uuid, EntityPlayerMP player) {
        return new PermissionContext(uuid, effectiveOpLevel(player));
    }

    private static int effectiveOpLevel(EntityPlayerMP player) {
        if (player == null || player.mcServer == null) return 0;
        PlayerList playerList = player.mcServer.getPlayerList();
        if (playerList == null) return 0;
        UserListOps ops = playerList.getOppedPlayers();
        if (ops == null) return 0;
        UserListOpsEntry entry = ops.getEntry(player.getGameProfile());
        return entry != null ? entry.getPermissionLevel() : 0;
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
        return "PermissionContext{uuid=" + uuid + ", opLevel=" + opLevel + "}";
    }
}
