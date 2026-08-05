/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse;

import javax.annotation.Nullable;

public interface MineSkinAPI {

    /**
     * Source discriminator persisted with skins generated via MineSkin
     * (moved here from the per-version command layer so the HTTP impls can
     * stay pure-Java).
     */
    String SOURCE_MINESKIN = "MineSkin";

    /**
     * Generate a skin property from an image URL via MineSkin API.
     *
     * @param url         Image URL to upload to MineSkin
     * @param variant     Skin variant (CLASSIC, SLIM, or null for auto-detect)
     * @return the generated skin response, or null if all retries exhausted
     */
    @Nullable
    MineSkinResponse genSkin(String url, @Nullable SkinVariant variant);
}
