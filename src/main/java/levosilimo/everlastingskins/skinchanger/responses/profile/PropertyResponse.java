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

public final class PropertyResponse {
    private final String name;
    private final String value;
    private final String signature;

    PropertyResponse(String name, String value, String signature) {
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
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        PropertyResponse that = (PropertyResponse) obj;
        return Objects.equals(this.name, that.name) &&
                Objects.equals(this.value, that.value) &&
                Objects.equals(this.signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, signature);
    }

    @Override
    public String toString() {
        return "PropertyResponse[" +
                "name=" + name + ", " +
                "value=" + value + ", " +
                "signature=" + signature + ']';
    }

}
