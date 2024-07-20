package levosilimo.everlastingskins.skinchanger.responses.profile;

import java.util.Objects;
import java.util.regex.Pattern;

public final class MojangProfileTexture {
    private final String url;
    private final MojangProfileTextureMeta metadata;

    MojangProfileTexture(String url, MojangProfileTextureMeta metadata) {
        this.url = url;
        this.metadata = metadata;
    }

    public String url() {
        return url;
    }

    public MojangProfileTextureMeta metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        MojangProfileTexture that = (MojangProfileTexture) obj;
        return Objects.equals(this.url, that.url) &&
                Objects.equals(this.metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, metadata);
    }

    @Override
    public String toString() {
        return "MojangProfileTexture[" +
                "url=" + url + ", " +
                "metadata=" + metadata + ']';
    }

    public static final Pattern URL_STRIP_PATTERN = Pattern.compile("^https?://textures\\.minecraft\\.net/texture/");

    public String getStrippedUrl() {
        return URL_STRIP_PATTERN.matcher(url).replaceAll("");
    }
}
