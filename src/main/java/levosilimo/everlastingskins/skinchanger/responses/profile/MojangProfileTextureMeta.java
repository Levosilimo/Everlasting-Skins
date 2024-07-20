package levosilimo.everlastingskins.skinchanger.responses.profile;

import java.util.Objects;

public final class MojangProfileTextureMeta {
    private final String model;

    MojangProfileTextureMeta(String model) {
        this.model = model;
    }

    public String model() {
        return model;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        MojangProfileTextureMeta that = (MojangProfileTextureMeta) obj;
        return Objects.equals(this.model, that.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(model);
    }

    @Override
    public String toString() {
        return "MojangProfileTextureMeta[" +
                "model=" + model + ']';
    }

}
