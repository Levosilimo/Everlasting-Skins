/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger.responses.uuid;

import java.util.Objects;

public final class MineToolsUUIDResponse {
    private final String id;
    private final String name;
    private final String status;

    public MineToolsUUIDResponse(String id, String name, String status) {
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MineToolsUUIDResponse that = (MineToolsUUIDResponse) o;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name) && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, status);
    }

    @Override
    public String toString() {
        return "MineToolsUUIDResponse[id=" + id + ", name=" + name + ", status=" + status + "]";
    }
}
