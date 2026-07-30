/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger.responses.mojang;

import levosilimo.everlastingskins.skinchanger.responses.profile.PropertyResponse;

import java.util.Arrays;
import java.util.Objects;

public final class MojangProfileResponse {
    private final String id;
    private final String name;
    private final PropertyResponse[] properties;

    public MojangProfileResponse(String id, String name, PropertyResponse[] properties) {
        this.id = id;
        this.name = name;
        this.properties = properties;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MojangProfileResponse that = (MojangProfileResponse) o;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name) && Arrays.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, name);
        result = 31 * result + Arrays.hashCode(properties);
        return result;
    }

    @Override
    public String toString() {
        return "MojangProfileResponse[id=" + id + ", name=" + name + ", properties=" + Arrays.toString(properties) + "]";
    }
}
