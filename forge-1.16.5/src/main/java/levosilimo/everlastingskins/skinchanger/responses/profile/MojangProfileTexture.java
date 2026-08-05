/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger.responses.profile;

import levosilimo.everlastingskins.util.EndpointsConfig;

import java.util.regex.Pattern;

public record MojangProfileTexture(String url, MojangProfileTextureMeta metadata) {
    public static final Pattern URL_STRIP_PATTERN = EndpointsConfig.getUrlPattern("pattern.texture.strip");
    public String getStrippedUrl() {
        return URL_STRIP_PATTERN.matcher(url).replaceAll("");
    }
}
