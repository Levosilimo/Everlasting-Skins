/*
 * SPDX-License-Identifier: MIT
 */

package levosilimo.everlastingskins.skinchanger.responses.profile;

public record DecodedTextureProperty(long timestamp, String profileId, String profileName, boolean signatureRequired, MojangProfileTextures textures) {
}
