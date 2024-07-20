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
package levosilimo.everlastingskins.skinchanger.responses.mineskin;

import java.util.Objects;

public final class MineSkinData {
    private final String uuid;
    private final MineSkinTexture texture;

    MineSkinData(String uuid, MineSkinTexture texture) {
        this.uuid = uuid;
        this.texture = texture;
    }

    public String uuid() {
        return uuid;
    }

    public MineSkinTexture texture() {
        return texture;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        MineSkinData that = (MineSkinData) obj;
        return Objects.equals(this.uuid, that.uuid) &&
                Objects.equals(this.texture, that.texture);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, texture);
    }

    @Override
    public String toString() {
        return "MineSkinData[" +
                "uuid=" + uuid + ", " +
                "texture=" + texture + ']';
    }

}
