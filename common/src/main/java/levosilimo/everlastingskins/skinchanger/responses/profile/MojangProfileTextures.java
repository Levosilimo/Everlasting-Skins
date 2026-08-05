/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger.responses.profile;

import java.util.Objects;

public final class MojangProfileTextures {
    private final MojangProfileTexture SKIN;
    private final DecodedTextureProperty CAPE;

    public MojangProfileTextures(MojangProfileTexture SKIN, DecodedTextureProperty CAPE) {
        this.SKIN = SKIN;
        this.CAPE = CAPE;
    }

    public MojangProfileTexture SKIN() {
        return SKIN;
    }

    public DecodedTextureProperty CAPE() {
        return CAPE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MojangProfileTextures that = (MojangProfileTextures) o;
        return Objects.equals(SKIN, that.SKIN) && Objects.equals(CAPE, that.CAPE);
    }

    @Override
    public int hashCode() {
        return Objects.hash(SKIN, CAPE);
    }

    @Override
    public String toString() {
        return "MojangProfileTextures[SKIN=" + SKIN + ", CAPE=" + CAPE + "]";
    }
}
