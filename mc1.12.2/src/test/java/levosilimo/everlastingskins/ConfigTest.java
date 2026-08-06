/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

    /* ================================================================== */
    /*  Defaults (lib-7 gap: no default-value coverage on mc1.12.2)        */
    /* ================================================================== */

    @Nested
    @DisplayName("Config defaults")
    class ConfigDefaults {

        @Test
        @DisplayName("Mojang profile cache defaults to enabled, 1h TTL, 1000 entries")
        void mojangProfileCacheDefaults() {
            assertTrue(Config.mojangProfileCacheEnabled);
            assertEquals(3600000L, Config.mojangProfileCacheTtlMs);
            assertEquals(1000, Config.mojangProfileCacheMaxSize);
        }

        @Test
        @DisplayName("URL allowlist defaults to disabled with the curated domain list")
        void urlAllowlistDefaults() {
            assertFalse(Config.urlAllowlistEnabled);
            List<String> domains = Arrays.asList(Config.urlAllowlistDomains);
            assertTrue(domains.contains("imgur.com"));
            assertTrue(domains.contains("textures.minecraft.net"));
            assertTrue(domains.contains("namemc.com"));
            assertTrue(domains.contains("mc-heads.net"));
            assertEquals(9, domains.size());
        }

        @Test
        @DisplayName("Default skins default to disabled with Steve + <random> list")
        void defaultSkinsDefaults() {
            assertFalse(Config.DEFAULT_SKINS_ENABLED);
            assertFalse(Config.DEFAULT_SKINS_APPLY_FOR_PREMIUM);
            assertArrayEquals(new String[]{"Steve", "<random>"}, Config.DEFAULT_SKINS_LIST);
        }

        @Test
        @DisplayName("Permission op levels default to 0 for self commands, 2 for elevated ones")
        void permissionOpLevelDefaults() {
            assertEquals(0, Config.permissionsOpLevelMojang);
            assertEquals(0, Config.permissionsOpLevelClear);
            assertEquals(0, Config.permissionsOpLevelRandom);
            assertEquals(2, Config.permissionsOpLevelUrl);
            assertEquals(2, Config.permissionsOpLevelOther);
            assertEquals(2, Config.permissionsOpLevelMetrics);
            assertEquals(2, Config.permissionsOpLevelMetricsReset);
        }
    }
}
