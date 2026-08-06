/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins;

import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Config defaults and toggle behavior for the 1.16.5 lane's own
 * {@link ForgeConfigSpec} definition (per-lane binding code; the MineSkin
 * API-key threading covered by the 1.21 {@code ConfigTest} lives in
 * {@code :common} and is covered there).
 */
class ConfigTest {

    @BeforeAll
    static void initConfig() {
        // ForgeConfigSpec.ConfigValue.get() requires the spec to be loaded.
        // Use an in-memory file config so defaults are served by the spec.
        Config.COMMON_CONFIG.setConfig(
                InMemoryCommentedFormat.defaultInstance().createConfig(java.util.HashMap::new));
    }

    /* ================================================================== */
    /*  Config defaults                                                    */
    /* ================================================================== */

    @Nested
    @DisplayName("Config defaults")
    class ConfigDefaults {

        @Test
        @DisplayName("Default language is English")
        void defaultLanguageIsEnglish() {
            assertEquals("en", Config.LANGUAGE.get());
        }

        @Test
        @DisplayName("Default toggle is true")
        void defaultToggleIsTrue() {
            assertTrue(Config.TOGGLE.get());
        }

        @Test
        @DisplayName("Default MineSkin API key is empty")
        void defaultMineskinApiKeyIsEmpty() {
            assertEquals("", Config.MINESKIN_API_KEY.get());
        }

        @Test
        @DisplayName("Default DiscordSRV enabled is false")
        void defaultDiscordSrvEnabledIsFalse() {
            assertFalse(Config.DISCORDSRV_ENABLED.get());
        }

        @Test
        @DisplayName("Default DiscordSRV channel ID is empty")
        void defaultDiscordSrvChannelIdIsEmpty() {
            assertEquals("", Config.DISCORDSRV_CHANNEL_ID.get());
        }

        @Test
        @DisplayName("Broadcast flags default: dim-scoped off, bundle off, entity tracker on")
        void defaultBroadcastFlags() {
            assertFalse(Config.DIMENSION_SCOPED_BROADCAST.get());
            // BROADCAST_USE_BUNDLE is inert on 1.16.5 (no ClientboundBundlePacket),
            // but the key must still default off.
            assertFalse(Config.BROADCAST_USE_BUNDLE.get());
            assertTrue(Config.REFRESH_VIA_ENTITY_TRACKER.get());
        }

        @Test
        @DisplayName("Default permission op levels (mojang/clear/random all, url/other/metrics op)")
        void defaultPermissionOpLevels() {
            assertEquals(0, Config.PERMISSIONS_OP_LEVEL_MOJANG.get());
            assertEquals(2, Config.PERMISSIONS_OP_LEVEL_URL.get());
            assertEquals(0, Config.PERMISSIONS_OP_LEVEL_CLEAR.get());
            assertEquals(0, Config.PERMISSIONS_OP_LEVEL_RANDOM.get());
            assertEquals(2, Config.PERMISSIONS_OP_LEVEL_OTHER.get());
            assertEquals(2, Config.PERMISSIONS_OP_LEVEL_METRICS.get());
            assertEquals(2, Config.PERMISSIONS_OP_LEVEL_METRICS_RESET.get());
        }

        @Test
        @DisplayName("Default message keys resolve to canonical English text")
        void defaultMessageKeys() {
            assertEquals("Skin change queued", Config.MESSAGES_CHANGE.get());
            assertEquals("Permission denied", Config.MESSAGES_PERMISSION_DENIED.get());
            assertEquals("Please wait %ds before using /skin again", Config.MESSAGES_COOLDOWN.get());
            assertEquals("No skin found for \"%s\"", Config.MESSAGES_NO_SKIN_FOUND.get());
            assertEquals("Metrics cleanup: pruned %d stale player entries", Config.MESSAGES_METRICS_CLEANUP.get());
        }

        @Test
        @DisplayName("Default rate-limit window is 5 commands per minute")
        void defaultRateLimit() {
            assertEquals(3, Config.COOLDOWN_SECONDS.get());
            assertTrue(Config.RATE_LIMIT_ENABLED.get());
            assertEquals(5, Config.MAX_COMMANDS_PER_MINUTE.get());
        }
    }

    /* ================================================================== */
    /*  Toggle behavior                                                    */
    /* ================================================================== */

    @Nested
    @DisplayName("Toggle behavior")
    class ToggleBehavior {

        @Test
        @DisplayName("TOGGLE can be set to false and read back")
        void toggleCanBeSetFalse() {
            ForgeConfigSpec.BooleanValue toggle = Config.TOGGLE;
            boolean original = toggle.get();
            try {
                toggle.set(false);
                assertFalse(toggle.get());
            } finally {
                toggle.set(original);
            }
        }

        @Test
        @DisplayName("DIMENSION_SCOPED_BROADCAST can be toggled and read back")
        void dimScopedCanBeToggled() {
            ForgeConfigSpec.BooleanValue flag = Config.DIMENSION_SCOPED_BROADCAST;
            boolean original = flag.get();
            try {
                flag.set(true);
                assertTrue(flag.get());
            } finally {
                flag.set(original);
            }
        }
    }

    /* ================================================================== */
    /*  Mojang cache config                                                */
    /* ================================================================== */

    @Nested
    @DisplayName("Mojang cache config")
    class MojangCacheConfig {

        @Test
        @DisplayName("MOJANG_CACHE_ENABLED defaults to true")
        void mojangCacheEnabledDefault() {
            assertTrue(Config.MOJANG_CACHE_ENABLED.get());
        }

        @Test
        @DisplayName("MOJANG_CACHE_TTL_MS defaults to 1 hour")
        void mojangCacheTtlDefault() {
            assertEquals(3600000L, Config.MOJANG_CACHE_TTL_MS.get());
        }

        @Test
        @DisplayName("MOJANG_CACHE_MAX_SIZE defaults to 1000")
        void mojangCacheMaxSizeDefault() {
            assertEquals(1000, Config.MOJANG_CACHE_MAX_SIZE.get());
        }

        @Test
        @DisplayName("TTL can be set to 0 to disable caching")
        void mojangCacheTtlZeroDisablesCaching() {
            long orig = Config.MOJANG_CACHE_TTL_MS.get();
            try {
                Config.MOJANG_CACHE_TTL_MS.set(0L);
                assertEquals(0L, Config.MOJANG_CACHE_TTL_MS.get());
            } finally {
                Config.MOJANG_CACHE_TTL_MS.set(orig);
            }
        }
    }
}
