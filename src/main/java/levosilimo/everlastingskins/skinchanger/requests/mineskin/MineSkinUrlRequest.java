/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MineSkinUrlRequest that = (MineSkinUrlRequest) o;
        return Objects.equals(variant, that.variant) && Objects.equals(name, that.name)
            && Objects.equals(visibility, that.visibility) && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variant, name, visibility, url);
    }

    @Override
    public String toString() {
        return "MineSkinUrlRequest[variant=" + variant + ", name=" + name
            + ", visibility=" + visibility + ", url=" + url + "]";
    }
}
