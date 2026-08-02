/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConfigTest {

    @Test
    @DisplayName("load() reads the MojangCache keys from the .cfg file")
    void load_readsMojangCacheKeys() throws Exception {
        File cfg = File.createTempFile("everlastingskins-config-test", ".cfg");
        cfg.deleteOnExit();
        String content = String.join("\n",
            "MojangCache {",
            "    # Mojang profile cache TTL in milliseconds (default 1h, max 7 days)",
            "    I:mojangProfileCacheTtlMs=5000",
            "    # Mojang profile cache max entries (default 1000, max 1M)",
            "    I:mojangProfileCacheMaxSize=50",
            "    # Enable Mojang profile cache (recommended for production servers; reduces Mojang API hits)",
            "    B:mojangProfileCacheEnabled=false",
            "}");
        Files.write(cfg.toPath(), content.getBytes());

        boolean origEnabled = Config.mojangProfileCacheEnabled;
        long origTtl = Config.mojangProfileCacheTtlMs;
        int origMax = Config.mojangProfileCacheMaxSize;
        try {
            Config.load(cfg);

            assertFalse(Config.mojangProfileCacheEnabled);
            assertEquals(5000, Config.mojangProfileCacheTtlMs);
            assertEquals(50, Config.mojangProfileCacheMaxSize);
        } finally {
            Config.mojangProfileCacheEnabled = origEnabled;
            Config.mojangProfileCacheTtlMs = origTtl;
            Config.mojangProfileCacheMaxSize = origMax;
        }
    }
}
