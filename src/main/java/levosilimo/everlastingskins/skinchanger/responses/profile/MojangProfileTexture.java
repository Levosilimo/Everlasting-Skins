package levosilimo.everlastingskins.skinchanger.responses.profile;

import levosilimo.everlastingskins.util.EndpointsConfig;

import java.util.Objects;
import java.util.regex.Pattern;

public final class MojangProfileTexture {
    public static final Pattern URL_STRIP_PATTERN = EndpointsConfig.getUrlPattern("pattern.texture.strip");

    private final String url;
    private final MojangProfileTextureMeta metadata;

    public MojangProfileTexture(String url, MojangProfileTextureMeta metadata) {
        this.url = url;
        this.metadata = metadata;
    }

    public String url() {
        return url;
    }

    public MojangProfileTextureMeta metadata() {
        return metadata;
    }

    public String getStrippedUrl() {
        return URL_STRIP_PATTERN.matcher(url).replaceAll("");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MojangProfileTexture that = (MojangProfileTexture) o;
        return Objects.equals(url, that.url) && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, metadata);
    }

    @Override
    public String toString() {
        return "MojangProfileTexture[url=" + url + ", metadata=" + metadata + "]";
    }
}
