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

import java.util.Objects;

public final class MineToolsUUIDResponse {
    private final String id;
    private final String name;
    private final String status;

    MineToolsUUIDResponse(String id, String name, String status) {
        this.id = id;
        this.name = name;
        this.status = status;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String status() {
        return status;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        MineToolsUUIDResponse that = (MineToolsUUIDResponse) obj;
        return Objects.equals(this.id, that.id) &&
                Objects.equals(this.name, that.name) &&
                Objects.equals(this.status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, status);
    }

    @Override
    public String toString() {
        return "MineToolsUUIDResponse[" +
                "id=" + id + ", " +
                "name=" + name + ", " +
                "status=" + status + ']';
    }

}
