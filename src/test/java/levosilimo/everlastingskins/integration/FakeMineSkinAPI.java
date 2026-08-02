/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.skinchanger.MineSkinAPI;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse;
import levosilimo.everlastingskins.util.CustomSkinProperty;

import javax.annotation.Nullable;

/**
 * Deterministic MineSkin stub: records the last URL/variant it was asked to
 * generate and returns a canned property.
 */
public class FakeMineSkinAPI implements MineSkinAPI {

    private final CustomSkinProperty property;
    private String lastUrl;
    private SkinVariant lastVariant;
    private int calls;

    public FakeMineSkinAPI(CustomSkinProperty property) {
        this.property = property;
    }

    @Override
    @Nullable
    public MineSkinResponse genSkin(String url, @Nullable SkinVariant variant) {
        lastUrl = url;
        lastVariant = variant;
        calls++;
        return new MineSkinResponse(property, "test-mineskin-id", variant, variant);
    }

    public String lastUrl() {
        return lastUrl;
    }

    public SkinVariant lastVariant() {
        return lastVariant;
    }

    public int calls() {
        return calls;
    }
}
