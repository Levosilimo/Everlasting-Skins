/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger.responses.profile;

import java.util.Arrays;
import java.util.Objects;

public final class MineToolsProfileResponse {
    private final Raw raw;

    public MineToolsProfileResponse(Raw raw) {
        this.raw = raw;
    }

    public Raw raw() {
        return raw;
    }

    public static final class Raw {
        private final String id;
        private final String name;
        private final PropertyResponse[] properties;
        private final String status;

        public Raw(String id, String name, PropertyResponse[] properties, String status) {
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
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Raw raw = (Raw) o;
            return Objects.equals(id, raw.id) && Objects.equals(name, raw.name)
                && Arrays.equals(properties, raw.properties) && Objects.equals(status, raw.status);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(id, name, status);
            result = 31 * result + Arrays.hashCode(properties);
            return result;
        }

        @Override
        public String toString() {
            return "Raw[id=" + id + ", name=" + name + ", properties=" + Arrays.toString(properties) + ", status=" + status + "]";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MineToolsProfileResponse that = (MineToolsProfileResponse) o;
        return Objects.equals(raw, that.raw);
    }

    @Override
    public int hashCode() {
        return Objects.hash(raw);
    }

    @Override
    public String toString() {
        return "MineToolsProfileResponse[raw=" + raw + "]";
    }
}
