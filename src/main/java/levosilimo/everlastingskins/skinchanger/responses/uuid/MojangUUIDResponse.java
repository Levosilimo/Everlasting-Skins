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

public final class MojangUUIDResponse {
    private final String name;
    private final String id;
    private final String error;
    private final String errorMessage;

    MojangUUIDResponse(String name, String id, String error, String errorMessage) {
        this.name = name;
        this.id = id;
        this.error = error;
        this.errorMessage = errorMessage;
    }

    public String name() {
        return name;
    }

    public String id() {
        return id;
    }

    public String error() {
        return error;
    }

    public String errorMessage() {
        return errorMessage;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        MojangUUIDResponse that = (MojangUUIDResponse) obj;
        return Objects.equals(this.name, that.name) &&
                Objects.equals(this.id, that.id) &&
                Objects.equals(this.error, that.error) &&
                Objects.equals(this.errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, id, error, errorMessage);
    }

    @Override
    public String toString() {
        return "MojangUUIDResponse[" +
                "name=" + name + ", " +
                "id=" + id + ", " +
                "error=" + error + ", " +
                "errorMessage=" + errorMessage + ']';
    }

}
