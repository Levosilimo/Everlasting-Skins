/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

/**
 * Test-only bridge into SkinCommand's package-private injection points.
 * Integration tests live in levosilimo.everlastingskins.integration and cannot
 * call setMojangAPI/setMineSkinAPI/resetAPIs directly.
 */
public final class SkinCommandTestAccess {

    private SkinCommandTestAccess() {
    }

    public static void setMojangAPI(MojangAPI api) {
        SkinCommand.setMojangAPI(api);
    }

    public static void setMineSkinAPI(MineSkinAPI api) {
        SkinCommand.setMineSkinAPI(api);
    }

    public static void resetAPIs() {
        SkinCommand.resetAPIs();
    }
}
