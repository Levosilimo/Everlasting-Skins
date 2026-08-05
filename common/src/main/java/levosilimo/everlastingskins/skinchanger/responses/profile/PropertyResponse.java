/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger.responses.profile;

import java.util.Objects;

public final class PropertyResponse {
    private final String name;
    private final String value;
    private final String signature;

    public PropertyResponse(String name, String value, String signature) {
        this.name = name;
        this.value = value;
        this.signature = signature;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    public String signature() {
        return signature;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyResponse that = (PropertyResponse) o;
        return Objects.equals(name, that.name) && Objects.equals(value, that.value) && Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, signature);
    }

    @Override
    public String toString() {
        return "PropertyResponse[name=" + name + ", value=" + value + ", signature=" + signature + "]";
    }
}
