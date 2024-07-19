package levosilimo.everlastingskins.skinchanger.responses.profile;

import java.util.regex.Pattern;

public record MojangProfileTexture(String url, MojangProfileTextureMeta metadata) {
    public static final Pattern URL_STRIP_PATTERN = Pattern.compile("^https?://textures\\.minecraft\\.net/texture/");
    public String getStrippedUrl() {
        return URL_STRIP_PATTERN.matcher(url).replaceAll("");
    }
}
