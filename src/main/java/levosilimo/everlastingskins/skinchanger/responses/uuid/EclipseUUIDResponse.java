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
package levosilimo.everlastingskins.skinchanger.responses.uuid;

import levosilimo.everlastingskins.skinchanger.responses.EclipseCacheData;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

public final class EclipseUUIDResponse {
    private final EclipseCacheData cacheData;
    private final boolean exists;
    private final UUID uuid;

    EclipseUUIDResponse(EclipseCacheData cacheData, boolean exists,  @Nullable UUID uuid) {
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

    public UUID uuid() {
        return uuid;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        EclipseUUIDResponse that = (EclipseUUIDResponse) obj;
        return Objects.equals(this.cacheData, that.cacheData) &&
                this.exists == that.exists &&
                Objects.equals(this.uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cacheData, exists, uuid);
    }

    @Override
    public String toString() {
        return "EclipseUUIDResponse[" +
                "cacheData=" + cacheData + ", " +
                "exists=" + exists + ", " +
                "uuid=" + uuid + ']';
    }

}
