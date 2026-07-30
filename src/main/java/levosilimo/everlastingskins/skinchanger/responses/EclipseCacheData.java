/*
 * SPDX-License-Identifier: MIT
 */

package levosilimo.everlastingskins.skinchanger.responses;

import java.util.Objects;

public final class EclipseCacheData {
    private final CacheState state;
    private final long createdAt;

    EclipseCacheData(
            CacheState state,
            long createdAt
    ) {
        this.state = state;
        this.createdAt = createdAt;
    }

    public CacheState state() {
        return state;
    }

    public long createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        EclipseCacheData that = (EclipseCacheData) obj;
        return Objects.equals(this.state, that.state) &&
                this.createdAt == that.createdAt;
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, createdAt);
    }

    @Override
    public String toString() {
        return "EclipseCacheData[" +
                "state=" + state + ", " +
                "createdAt=" + createdAt + ']';
    }

    public enum CacheState {
        HIT,
        MISS
    }
}
