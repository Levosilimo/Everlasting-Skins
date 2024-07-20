package levosilimo.everlastingskins.skinchanger.responses.mojang;

import levosilimo.everlastingskins.util.CustomSkinProperty;

import java.util.Objects;
import java.util.UUID;

public final class MojangSkinDataResult {
    private final UUID uniqueId;
    private final CustomSkinProperty skinProperty;

    public MojangSkinDataResult(UUID uniqueId, CustomSkinProperty skinProperty) {
        this.uniqueId = uniqueId;
        this.skinProperty = skinProperty;
    }

    public UUID uniqueId() {
        return uniqueId;
    }

    public CustomSkinProperty skinProperty() {
        return skinProperty;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        MojangSkinDataResult that = (MojangSkinDataResult) obj;
        return Objects.equals(this.uniqueId, that.uniqueId) &&
                Objects.equals(this.skinProperty, that.skinProperty);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uniqueId, skinProperty);
    }

    @Override
    public String toString() {
        return "MojangSkinDataResult[" +
                "uniqueId=" + uniqueId + ", " +
                "skinProperty=" + skinProperty + ']';
    }

}
