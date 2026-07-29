package levosilimo.everlastingskins.skinchanger.responses.profile;

import java.util.Objects;

public final class MojangProfileTextureMeta {
    private final String model;

    public MojangProfileTextureMeta(String model) {
        this.model = model;
    }

    public String model() {
        return model;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MojangProfileTextureMeta that = (MojangProfileTextureMeta) o;
        return Objects.equals(model, that.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(model);
    }

    @Override
    public String toString() {
        return "MojangProfileTextureMeta[model=" + model + "]";
    }
}
