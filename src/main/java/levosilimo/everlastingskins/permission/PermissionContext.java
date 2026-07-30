package levosilimo.everlastingskins.permission;

import java.util.Objects;
import java.util.UUID;

public final class PermissionContext {
    private final UUID uuid;
    private final boolean isOp;

    public PermissionContext(UUID uuid, boolean isOp) {
        this.uuid = Objects.requireNonNull(uuid);
        this.isOp = isOp;
    }

    public UUID uuid() { return uuid; }
    public boolean isOp() { return isOp; }

    public static PermissionContext of(UUID uuid, boolean isOp) {
        return new PermissionContext(uuid, isOp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PermissionContext)) return false;
        PermissionContext that = (PermissionContext) o;
        return isOp == that.isOp && uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, isOp);
    }

    @Override
    public String toString() {
        return "PermissionContext{uuid=" + uuid + ", isOp=" + isOp + "}";
    }
}
