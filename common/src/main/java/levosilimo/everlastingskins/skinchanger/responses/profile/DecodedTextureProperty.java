/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger.responses.profile;

import java.util.Objects;

public final class DecodedTextureProperty {
    private final long timestamp;
    private final String profileId;
    private final String profileName;
    private final boolean signatureRequired;
    private final MojangProfileTextures textures;

    public DecodedTextureProperty(long timestamp, String profileId, String profileName,
                                   boolean signatureRequired, MojangProfileTextures textures) {
        this.timestamp = timestamp;
        this.profileId = profileId;
        this.profileName = profileName;
        this.signatureRequired = signatureRequired;
        this.textures = textures;
    }

    public long timestamp() {
        return timestamp;
    }

    public String profileId() {
        return profileId;
    }

    public String profileName() {
        return profileName;
    }

    public boolean signatureRequired() {
        return signatureRequired;
    }

    public MojangProfileTextures textures() {
        return textures;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DecodedTextureProperty that = (DecodedTextureProperty) o;
        return timestamp == that.timestamp && signatureRequired == that.signatureRequired
            && Objects.equals(profileId, that.profileId) && Objects.equals(profileName, that.profileName)
            && Objects.equals(textures, that.textures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, profileId, profileName, signatureRequired, textures);
    }

    @Override
    public String toString() {
        return "DecodedTextureProperty[timestamp=" + timestamp + ", profileId=" + profileId
            + ", profileName=" + profileName + ", signatureRequired=" + signatureRequired
            + ", textures=" + textures + "]";
    }
}
