package levosilimo.everlastingskins.skinchanger.responses.uuid;

import levosilimo.everlastingskins.skinchanger.responses.EclipseCacheData;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

public final class EclipseUUIDResponse {
    private final EclipseCacheData cacheData;
    private final boolean exists;
    private final UUID uuid;

    public EclipseUUIDResponse(EclipseCacheData cacheData, boolean exists, @Nullable UUID uuid) {
        this.cacheData = cacheData;
        this.exists = exists;
        this.uuid = uuid;
    }

    public EclipseCacheData cacheData() {
        return cacheData;
    }

    public boolean exists() {
        return exists;
    }

    @Nullable
    public UUID uuid() {
        return uuid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EclipseUUIDResponse that = (EclipseUUIDResponse) o;
        return exists == that.exists && Objects.equals(cacheData, that.cacheData) && Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cacheData, exists, uuid);
    }

    @Override
    public String toString() {
        return "EclipseUUIDResponse[cacheData=" + cacheData + ", exists=" + exists + ", uuid=" + uuid + "]";
    }
}
