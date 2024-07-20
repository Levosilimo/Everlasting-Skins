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

public final class MineSkinTexture {
    private final String value;
    private final String signature;
    private final String url;

    MineSkinTexture(String value, String signature, String url) {
        this.value = value;
        this.signature = signature;
        this.url = url;
    }

    public String value() {
        return value;
    }

    public String signature() {
        return signature;
    }

    public String url() {
        return url;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        MineSkinTexture that = (MineSkinTexture) obj;
        return Objects.equals(this.value, that.value) &&
                Objects.equals(this.signature, that.signature) &&
                Objects.equals(this.url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, signature, url);
    }

    @Override
    public String toString() {
        return "MineSkinTexture[" +
                "value=" + value + ", " +
                "signature=" + signature + ", " +
                "url=" + url + ']';
    }

}
