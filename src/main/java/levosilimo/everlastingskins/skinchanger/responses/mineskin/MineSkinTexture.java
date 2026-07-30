/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger.responses.mineskin;

import java.util.Objects;

public final class MineSkinTexture {
    private final String value;
    private final String signature;
    private final String url;

    public MineSkinTexture(String value, String signature, String url) {
        this.value = value;
        this.signature = signature;
        this.url = url;
    }

    public String value() {
        return value;
    }

    public String signature() {
        return signature;
    }

    public String url() {
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MineSkinTexture that = (MineSkinTexture) o;
        return Objects.equals(value, that.value) && Objects.equals(signature, that.signature) && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, signature, url);
    }

    @Override
    public String toString() {
        return "MineSkinTexture[value=" + value + ", signature=" + signature + ", url=" + url + "]";
    }
}
