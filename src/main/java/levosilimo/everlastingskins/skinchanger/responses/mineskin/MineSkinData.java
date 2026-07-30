/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger.responses.mineskin;

import java.util.Objects;

public final class MineSkinData {
    private final String uuid;
    private final MineSkinTexture texture;

    public MineSkinData(String uuid, MineSkinTexture texture) {
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MineSkinData that = (MineSkinData) o;
        return Objects.equals(uuid, that.uuid) && Objects.equals(texture, that.texture);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, texture);
    }

    @Override
    public String toString() {
        return "MineSkinData[uuid=" + uuid + ", texture=" + texture + "]";
    }
}
