/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger.requests.mineskin;

import levosilimo.everlastingskins.enums.SkinVariant;


public record MineSkinUrlRequest(SkinVariant variant, String name, Integer visibility, String url) {
}
