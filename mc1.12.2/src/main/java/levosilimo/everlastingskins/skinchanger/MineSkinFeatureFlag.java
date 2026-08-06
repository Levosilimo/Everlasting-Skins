/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.Config;

public final class MineSkinFeatureFlag {
    public static boolean isEnabled() { return Config.MINESKIN_ENABLED; }
    private MineSkinFeatureFlag() {}
}
