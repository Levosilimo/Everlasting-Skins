/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins;

import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.skinchanger.MineSkinApiHttpImpl;
import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import levosilimo.everlastingskins.util.HttpClient;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.InMemoryCommentedFormat;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Config defaults, toggle behavior, and MineSkin API key threading.
 * <p>
 * Config uses ForgeConfigSpec with static initialisation; each test
 * reads the current spec values directly.
 */
class ConfigTest {

    /** Valid MineSkin v1 response body; 200 completes genSkin in a single attempt. */
    private static final String MINESKIN_OK_JSON = """
            {
              "id": 12345,
              "idStr": "12345",
              "uuid": "550e8400-e29b-41d4-a716-446655440000",
              "name": "Test",
              "variant": "classic",
              "data": {
                "uuid": "550e8400-e29b-41d4-a716-446655440000",
                "texture": {
                  "value": "dGV4dHVyZXM=",
                  "signature": "signature==",
                  "url": "https://example.com/skin"
                }
              },
              "delay": 0,
              "nextRequest": 0
            }
            """;

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
        void apiKeyIncludedInHeaders() throws Exception {
            HttpClient httpClient = mock(HttpClient.class);
            var api = new MineSkinApiHttpImpl(httpClient, "test-key");

            // 200 with a valid body completes the request in one attempt.
            URI uri = levosilimo.everlastingskins.util.EndpointsConfig.getURI("endpoint.mineskin.generate");
            when(httpClient.execute(eq(uri), any(), any(), any(), any(), any(), anyInt()))
                    .thenReturn(new HttpResponse(200, MINESKIN_OK_JSON, Map.of()));

            api.genSkin("https://example.com/skin.png", SkinVariant.CLASSIC);

            Map<String, String> headers = capturedHeaders(httpClient);
            assertNotNull(headers, "Headers should be captured");
            assertEquals("Bearer test-key", headers.get("Authorization"));
        }

        @Test
        @DisplayName("With empty API key, no Authorization header sent")
        void emptyApiKeyOmitsAuthHeader() throws Exception {
            HttpClient httpClient = mock(HttpClient.class);
            var api = new MineSkinApiHttpImpl(httpClient, "");

            URI uri = levosilimo.everlastingskins.util.EndpointsConfig.getURI("endpoint.mineskin.generate");
            when(httpClient.execute(eq(uri), any(), any(), any(), any(), any(), anyInt()))
                    .thenReturn(new HttpResponse(200, MINESKIN_OK_JSON, Map.of()));

            api.genSkin("https://example.com/skin.png", SkinVariant.CLASSIC);

            Map<String, String> headers = capturedHeaders(httpClient);
            assertNotNull(headers, "Headers should be captured");
            assertNull(headers.get("Authorization"),
                    "Authorization header should not be present when API key is empty");
        }

        @Test
        @DisplayName("API key from Config is threaded through at construction time")
        void apiKeyReadAtConstruction() throws Exception {
            ForgeConfigSpec.ConfigValue<String> keySpec = Config.MINESKIN_API_KEY;
            String original = keySpec.get();
            try {
                keySpec.set("key-from-config");
                // The per-version bootstrap injects the Config value; the impl
                // captures it at construction time (never re-reads the config).
                HttpClient httpClient = mock(HttpClient.class);
                var api = new MineSkinApiHttpImpl(httpClient, Config.MINESKIN_API_KEY.get());

                URI uri = levosilimo.everlastingskins.util.EndpointsConfig.getURI("endpoint.mineskin.generate");
                when(httpClient.execute(eq(uri), any(), any(), any(), any(), any(), anyInt()))
                        .thenReturn(new HttpResponse(200, MINESKIN_OK_JSON, Map.of()));

                api.genSkin("https://example.com/skin.png", SkinVariant.CLASSIC);

                Map<String, String> headers = capturedHeaders(httpClient);
                assertNotNull(headers);
                assertEquals("Bearer key-from-config", headers.get("Authorization"));
            } finally {
                keySpec.set(original);
            }
        }

        @SuppressWarnings("unchecked")
        private static Map<String, String> capturedHeaders(HttpClient httpClient) throws Exception {
            ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
            verify(httpClient).execute(any(), any(), any(), any(), any(), captor.capture(), anyInt());
            return captor.getValue();
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
