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
package levosilimo.everlastingskins.skinchanger.responses.profile;

import java.util.Objects;

public final class MineToolsProfileResponse {
    private final Raw raw;

    MineToolsProfileResponse(Raw raw) {
        this.raw = raw;
    }

    public Raw raw() {
        return raw;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        MineToolsProfileResponse that = (MineToolsProfileResponse) obj;
        return Objects.equals(this.raw, that.raw);
    }

    @Override
    public int hashCode() {
        return Objects.hash(raw);
    }

    @Override
    public String toString() {
        return "MineToolsProfileResponse[" +
                "raw=" + raw + ']';
    }

    public final class Raw {
        private final String id;
        private final String name;
        private final PropertyResponse[] properties;
        private final String status;

        Raw(String id, String name, PropertyResponse[] properties, String status) {
            this.id = id;
            this.name = name;
            this.properties = properties;
            this.status = status;
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        public PropertyResponse[] properties() {
            return properties;
        }

        public String status() {
            return status;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            Raw that = (Raw) obj;
            return Objects.equals(this.id, that.id) &&
                    Objects.equals(this.name, that.name) &&
                    Objects.equals(this.properties, that.properties) &&
                    Objects.equals(this.status, that.status);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name, properties, status);
        }

        @Override
        public String toString() {
            return "Raw[" +
                    "id=" + id + ", " +
                    "name=" + name + ", " +
                    "properties=" + properties + ", " +
                    "status=" + status + ']';
        }

    }
}
