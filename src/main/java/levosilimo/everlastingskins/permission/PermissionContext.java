package levosilimo.everlastingskins.permission;

import java.util.UUID;

/**
 * Lightweight context for permission checks. Decouples the permission system
 * from Minecraft's ServerPlayer/EntityPlayerMP, which can't be instantiated
 * in unit tests due to EntityDataSerializers static init.
 */
public record PermissionContext(UUID uuid, boolean isOp) {
    public static PermissionContext of(UUID uuid, boolean isOp) {
        return new PermissionContext(uuid, isOp);
    }
}
