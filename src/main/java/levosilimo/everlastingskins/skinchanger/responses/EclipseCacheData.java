/*
 * SkinsRestorer
 * Copyright (C) 2024  SkinsRestorer Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
