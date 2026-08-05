/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins;

import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.skinchanger.MineSkinApiHttpImpl;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.InMemoryCommentedFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Config defaults, toggle behavior, and MineSkin API key threading.
 * <p>
 * Config uses ForgeConfigSpec with static initialisation; each test
 * reads the current spec values directly.
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
        @DisplayName("Default message keys resolve to canonical English text")
        void defaultMessageKeys() {
            assertEquals("Skin change queued", Config.MESSAGES_CHANGE.get());
            assertEquals("Permission denied", Config.MESSAGES_PERMISSION_DENIED.get());
            assertEquals("Please wait %ds before using /skin again", Config.MESSAGES_COOLDOWN.get());
            assertEquals("No skin found for \"%s\"", Config.MESSAGES_NO_SKIN_FOUND.get());
            assertEquals("Metrics cleanup: pruned %d stale player entries", Config.MESSAGES_METRICS_CLEANUP.get());
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
    }

    /* ================================================================== */
    /*  MineSkin API key threading                                         */
    /* ================================================================== */

    @Nested
    @DisplayName("MineSkin API key threading")
    class MineSkinApiKeyThreading {

        @Test
        @DisplayName("With API key, request includes Authorization header")
        void apiKeyIncludedInHeaders() {
            var httpClient = new FakeHttpClient();
            var api = new MineSkinApiHttpImpl(httpClient, "test-key");

            // Register a 403 so the request completes (terminal, no retry loop)
            httpClient.addResponse(
                    levosilimo.everlastingskins.util.EndpointsConfig.getURI("endpoint.mineskin.generate"),
                    403,
                    "{\"errorCode\":\"invalid_api_key\",\"error\":\"Invalid API Key\"}"
            );

            api.genSkin("https://example.com/skin.png", SkinVariant.CLASSIC);

            var headers = httpClient.getLastCapturedHeaders();
            assertNotNull(headers, "Headers should be captured");
            assertEquals("Bearer test-key", headers.get("Authorization"));
        }

        @Test
        @DisplayName("With empty API key, no Authorization header sent")
        void emptyApiKeyOmitsAuthHeader() {
            var httpClient = new FakeHttpClient();
            var api = new MineSkinApiHttpImpl(httpClient, "");

            httpClient.addResponse(
                    levosilimo.everlastingskins.util.EndpointsConfig.getURI("endpoint.mineskin.generate"),
                    403,
                    "{\"errorCode\":\"invalid_api_key\",\"error\":\"Invalid API Key\"}"
            );

            api.genSkin("https://example.com/skin.png", SkinVariant.CLASSIC);

            var headers = httpClient.getLastCapturedHeaders();
            assertNotNull(headers, "Headers should be captured");
            assertNull(headers.get("Authorization"),
                    "Authorization header should not be present when API key is empty");
        }

        @Test
        @DisplayName("API key is read at construction time (not lazily)")
        void apiKeyReadAtConstruction() {
            ForgeConfigSpec.ConfigValue<String> keySpec = Config.MINESKIN_API_KEY;
            String original = keySpec.get();
            try {
                keySpec.set("key-from-config");
                // Construct a new instance using the 1-arg constructor (reads Config directly)
                var httpClient = new FakeHttpClient();
                var api = new MineSkinApiHttpImpl(httpClient);

                httpClient.addResponse(
                        levosilimo.everlastingskins.util.EndpointsConfig.getURI("endpoint.mineskin.generate"),
                        403,
                        "{\"errorCode\":\"invalid_api_key\",\"error\":\"Invalid API Key\"}"
                );

                api.genSkin("https://example.com/skin.png", SkinVariant.CLASSIC);

                var headers = httpClient.getLastCapturedHeaders();
                assertNotNull(headers);
                assertEquals("Bearer key-from-config", headers.get("Authorization"));
            } finally {
                keySpec.set(original);
            }
        }
    }

    /* ================================================================== */
    /*  Mojang cache config (lib-7 gap: #143 keys untested)                */
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

        @Test
        @DisplayName("cache keys can be toggled off and read back")
        void mojangCacheToggledOff() {
            boolean origEnabled = Config.MOJANG_CACHE_ENABLED.get();
            long origTtl = Config.MOJANG_CACHE_TTL_MS.get();
            try {
                Config.MOJANG_CACHE_ENABLED.set(false);
                Config.MOJANG_CACHE_TTL_MS.set(0L);
                assertFalse(Config.MOJANG_CACHE_ENABLED.get());
                assertEquals(0L, Config.MOJANG_CACHE_TTL_MS.get());
            } finally {
                Config.MOJANG_CACHE_ENABLED.set(origEnabled);
                Config.MOJANG_CACHE_TTL_MS.set(origTtl);
            }
        }
    }
}
