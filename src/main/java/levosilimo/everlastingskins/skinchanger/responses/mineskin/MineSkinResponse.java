/*
 * SkinsRestorer
 * Copyright (C) 2024  SkinsRestorer Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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

    public MineSkinResponse(CustomSkinProperty property, @Nullable String mineSkinId, @Nullable SkinVariant requestedVariant, @Nullable SkinVariant generatedVariant) {
        this.property = property;
        this.mineSkinId = mineSkinId;
        this.requestedVariant = requestedVariant;
        this.generatedVariant = generatedVariant;
    }

    public CustomSkinProperty property() {
        return property;
    }

    public String mineSkinId() {
        return mineSkinId;
    }

    public SkinVariant requestedVariant() {
        return requestedVariant;
    }

    public SkinVariant generatedVariant() {
        return generatedVariant;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        MineSkinResponse that = (MineSkinResponse) obj;
        return Objects.equals(this.property, that.property) &&
                Objects.equals(this.mineSkinId, that.mineSkinId) &&
                Objects.equals(this.requestedVariant, that.requestedVariant) &&
                Objects.equals(this.generatedVariant, that.generatedVariant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(property, mineSkinId, requestedVariant, generatedVariant);
    }

    @Override
    public String toString() {
        return "MineSkinResponse[" +
                "property=" + property + ", " +
                "mineSkinId=" + mineSkinId + ", " +
                "requestedVariant=" + requestedVariant + ", " +
                "generatedVariant=" + generatedVariant + ']';
    }

}
