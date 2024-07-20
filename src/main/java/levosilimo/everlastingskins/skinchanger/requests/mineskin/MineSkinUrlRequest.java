package levosilimo.everlastingskins.skinchanger.requests.mineskin;

import levosilimo.everlastingskins.enums.SkinVariant;

import java.util.Objects;


public final class MineSkinUrlRequest {
    private final SkinVariant variant;
    private final String name;
    private final Integer visibility;
    private final String url;

    public MineSkinUrlRequest(SkinVariant variant, String name, Integer visibility, String url) {
        this.variant = variant;
        this.name = name;
        this.visibility = visibility;
        this.url = url;
    }

    public SkinVariant variant() {
        return variant;
    }

    public String name() {
        return name;
    }

    public Integer visibility() {
        return visibility;
    }

    public String url() {
        return url;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        MineSkinUrlRequest that = (MineSkinUrlRequest) obj;
        return Objects.equals(this.variant, that.variant) &&
                Objects.equals(this.name, that.name) &&
                Objects.equals(this.visibility, that.visibility) &&
                Objects.equals(this.url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variant, name, visibility, url);
    }

    @Override
    public String toString() {
        return "MineSkinUrlRequest[" +
                "variant=" + variant + ", " +
                "name=" + name + ", " +
                "visibility=" + visibility + ", " +
                "url=" + url + ']';
    }

}
