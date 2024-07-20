package levosilimo.everlastingskins.skinchanger.responses.profile;

import java.util.Objects;

public final class MojangProfileTextures {
    private final MojangProfileTexture SKIN;
    private final DecodedTextureProperty CAPE;

    MojangProfileTextures(MojangProfileTexture SKIN, DecodedTextureProperty CAPE) {
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
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        MojangProfileTextures that = (MojangProfileTextures) obj;
        return Objects.equals(this.SKIN, that.SKIN) &&
                Objects.equals(this.CAPE, that.CAPE);
    }

    @Override
    public int hashCode() {
        return Objects.hash(SKIN, CAPE);
    }

    @Override
    public String toString() {
        return "MojangProfileTextures[" +
                "SKIN=" + SKIN + ", " +
                "CAPE=" + CAPE + ']';
    }

}
