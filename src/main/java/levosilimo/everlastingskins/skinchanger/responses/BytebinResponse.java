package levosilimo.everlastingskins.skinchanger.responses;

import java.util.Objects;

public final class BytebinResponse {
    private final String key;

    public BytebinResponse(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BytebinResponse that = (BytebinResponse) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "BytebinResponse[key=" + key + "]";
    }
}
