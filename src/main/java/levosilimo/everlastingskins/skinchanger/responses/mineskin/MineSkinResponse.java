/*
 * SPDX-License-Identifier: MIT
 */

package levosilimo.everlastingskins.skinchanger.responses.mineskin;

import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.util.CustomSkinProperty;

import javax.annotation.Nullable;

public record MineSkinResponse(CustomSkinProperty property, @Nullable String mineSkinId, @Nullable SkinVariant requestedVariant, @Nullable SkinVariant generatedVariant) {
}
