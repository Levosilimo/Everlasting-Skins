/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.permission;

import com.electronwill.nightconfig.core.CommentedConfig;
import levosilimo.everlastingskins.Config;

/** Loads the common config in unit tests so Config values are readable without FML boot. */
public final class TestConfigSupport {

    private TestConfigSupport() {
    }

    public static void loadDefaults() {
        Config.COMMON_CONFIG.setConfig(CommentedConfig.inMemory());
    }
}
