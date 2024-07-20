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

public final class DecodedTextureProperty {
    private final long timestamp;
    private final String profileId;
    private final String profileName;
    private final boolean signatureRequired;
    private final MojangProfileTextures textures;

    DecodedTextureProperty(long timestamp, String profileId, String profileName, boolean signatureRequired, MojangProfileTextures textures) {
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
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        DecodedTextureProperty that = (DecodedTextureProperty) obj;
        return this.timestamp == that.timestamp &&
                Objects.equals(this.profileId, that.profileId) &&
                Objects.equals(this.profileName, that.profileName) &&
                this.signatureRequired == that.signatureRequired &&
                Objects.equals(this.textures, that.textures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, profileId, profileName, signatureRequired, textures);
    }

    @Override
    public String toString() {
        return "DecodedTextureProperty[" +
                "timestamp=" + timestamp + ", " +
                "profileId=" + profileId + ", " +
                "profileName=" + profileName + ", " +
                "signatureRequired=" + signatureRequired + ", " +
                "textures=" + textures + ']';
    }

}
