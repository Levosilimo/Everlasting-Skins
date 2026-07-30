/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger.responses.mineskin;

import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.util.CustomSkinProperty;

import javax.annotation.Nullable;
import java.util.Objects;

public final class MineSkinResponse {
    private final CustomSkinProperty property;
    private final String mineSkinId;
    private final SkinVariant requestedVariant;
    private final SkinVariant generatedVariant;

    public MineSkinResponse(CustomSkinProperty property, @Nullable String mineSkinId,
                            @Nullable SkinVariant requestedVariant, @Nullable SkinVariant generatedVariant) {
        this.property = property;
        this.mineSkinId = mineSkinId;
        this.requestedVariant = requestedVariant;
        this.generatedVariant = generatedVariant;
    }

    public CustomSkinProperty property() {
        return property;
    }

    @Nullable
    public String mineSkinId() {
        return mineSkinId;
    }

    @Nullable
    public SkinVariant requestedVariant() {
        return requestedVariant;
    }

    @Nullable
    public SkinVariant generatedVariant() {
        return generatedVariant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MineSkinResponse that = (MineSkinResponse) o;
        return Objects.equals(property, that.property) && Objects.equals(mineSkinId, that.mineSkinId)
            && requestedVariant == that.requestedVariant && generatedVariant == that.generatedVariant;
    }

    @Override
    public int hashCode() {
        return Objects.hash(property, mineSkinId, requestedVariant, generatedVariant);
    }

    @Override
    public String toString() {
        return "MineSkinResponse[property=" + property + ", mineSkinId=" + mineSkinId
            + ", requestedVariant=" + requestedVariant + ", generatedVariant=" + generatedVariant + "]";
    }
}
