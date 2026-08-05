/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.FakeHttpClient;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse;
import levosilimo.everlastingskins.util.EndpointsConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MineSkin retry classification: invalid API key, rate-limit with delay,
 * terminal failures, and the success path.
 * <p>
 * Tests exercise the response-switch logic in
 * {@link MineSkinApiHttpImpl#genSkinInternal} via a
 * {@link FakeHttpClient}. No live MineSkin endpoint is contacted.
 */
class MineSkinApiHttpImplTest {

    private static final URI MINESKIN_URI = EndpointsConfig.getURI("endpoint.mineskin.generate");
    private static final String IMAGE_URL = "https://example.com/skin.png";
    private static final String VALID_VALUE = "dGV4dHVyZXMgeyBTS0lOIHsgdXJsOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS90ZXN0IiB9IH0=";
    private static final String VALID_SIG = "signature==";

    /** Texture value shared by the V1/V2 fixture files. */
    private static final String FIXTURE_TEXTURE_VALUE = "eyJ0aW1lc3RhbXAiOjE3MzAwMDAwMDAwMDAsInByb2ZpbGVJZCI6IjU1MGU4NDAwZTI5YjQxZDRhNzE2NDQ2NjU1NDQwMDAwIiwicHJvZmlsZU5hbWUiOiJMZXZvc2lsaW1vIiwic2lnbmF0dXJlUmVxdWlyZWQiOnRydWUsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9hYmMxMjNkZWY0NTYifX19";
    private static final String FIXTURE_SIGNATURE = "TQLAykIrTfFeo4eO7uNx0aj4eCy0eR9qAAqXr8CWjZ9NAsDKQitN8V6jh47u43HRqPh4LLR5H2oACpevwJaNn00CwMpCK03xXqOHju7jcdGo+HgstHkfagAKl6/Alo2fTQLAykIrTfFeo4eO7uNx0aj4eCy0eR9qAAqXr8CWjZ9NAsDKQitN8V6jh47u43HRqPh4LLR5H2oACpevwJaNn00CwMpCK03xXqOHju7jcdGo+HgstHkfagAKl6/Alo2fTQLAykIrTfFeo4eO7uNx0aj4eCy0eR9qAAqXr8CWjZ9NAsDKQitN8V6jh47u43HRqPh4LLR5H2oACpevwJaNnw==";

    private FakeHttpClient httpClient;
    private MineSkinApiHttpImpl api;

    @BeforeAll
    static void initConfig() {
        // genSkinInternal reads URL_ALLOWLIST_* from the Forge config spec.
        // Serve defaults from an in-memory config so this class runs standalone
        // instead of depending on ConfigTest having run first.
        Config.COMMON_CONFIG.setConfig(
                InMemoryCommentedFormat.defaultInstance().createConfig(java.util.HashMap::new));
    }

    @BeforeEach
    void setUp() {
        httpClient = new FakeHttpClient();
        // A configured key keeps the retry-classification tests off the
        // empty-key config-warning path (covered separately in ConfigWarnings).
        api = new MineSkinApiHttpImpl(httpClient, "test-api-key");
    }

    @Nested
    @DisplayName("Success path")
    class Success {

        @Test
        @DisplayName("200 OK with valid body → returns MineSkinResponse")
        void validResponse() {
            httpClient.addResponse(MINESKIN_URI, 200, validMineSkinJson());

            MineSkinResponse result = api.genSkin(IMAGE_URL, SkinVariant.CLASSIC);

            assertNotNull(result);
            assertNotNull(result.property());
            assertEquals(VALID_VALUE, result.property().getOriginalProperty().value());
        }
    }

    @Nested
    @DisplayName("Invalid API key (403)")
    class InvalidApiKey {

        @Test
        @DisplayName("403 with invalid_api_key error → returns null (terminal)")
        void invalidApiKeyTerminal() {
            httpClient.addResponse(MINESKIN_URI, 403,
                    "{\"errorCode\":\"invalid_api_key\",\"error\":\"Invalid API Key\"}");

            // genSkinInternal returns Optional.empty() for 403, and genSkin()
            // loops MAX_RETRIES (5) before returning null.
            MineSkinResponse result = api.genSkin(IMAGE_URL, SkinVariant.CLASSIC);

            assertNull(result);
        }

        @Test
        @DisplayName("403 with unknown error code → returns null (terminal via genSkin loop)")
        void unknown403() {
            httpClient.addResponse(MINESKIN_URI, 403,
                    "{\"errorCode\":\"some_other_code\",\"error\":\"Unknown\"}");

            MineSkinResponse result = api.genSkin(IMAGE_URL, SkinVariant.CLASSIC);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Rate-limit (429)")
    class RateLimit {

        @Test
        @DisplayName("429 with delay=0 → returns empty (retry), eventually null")
        void rateLimitWithZeroDelay() {
            httpClient.addResponse(MINESKIN_URI, 429,
                    "{\"error\":\"rate_limit\",\"delay\":0}");

            MineSkinResponse result = api.genSkin(IMAGE_URL, SkinVariant.CLASSIC);
            // After MAX_RETRIES attempts with Optional.empty(), genSkin returns null
            assertNull(result);
        }

        @Test
        @DisplayName("429 with past nextRequest → returns empty (retry, no sleep)")
        void rateLimitWithPastNextRequest() {
            httpClient.addResponse(MINESKIN_URI, 429,
                    "{\"error\":\"rate_limit\",\"nextRequest\":0}");

            MineSkinResponse result = api.genSkin(IMAGE_URL, SkinVariant.CLASSIC);
            assertNull(result);
        }
    }

    @Nested
    class TerminalFailures {

        @Test
        @DisplayName("500 with unknown error code → returns null (terminal)")
        void serverErrorUnknown() {
            httpClient.addResponse(MINESKIN_URI, 500,
                    "{\"errorCode\":\"internal_error\",\"error\":\"Something broke\"}");

            MineSkinResponse result = api.genSkin(IMAGE_URL, SkinVariant.CLASSIC);
            assertNull(result);
        }

        @Test
        @DisplayName("400 with unknown error code → returns null (terminal)")
        void badRequestUnknown() {
            httpClient.addResponse(MINESKIN_URI, 400,
                    "{\"errorCode\":\"bad_request\",\"error\":\"Bad request\"}");

            MineSkinResponse result = api.genSkin(IMAGE_URL, SkinVariant.CLASSIC);
            assertNull(result);
        }
    }

    /* ================================================================== */
    /*  genSkinInternal direct tests (package-private access)              */
    /* ================================================================== */

    @Nested
    @DisplayName("genSkinInternal direct response classification")
    class GenSkinInternal {

        @Test
        @DisplayName("200 OK → Optional with MineSkinResponse")
        void success() throws Exception {
            httpClient.addResponse(MINESKIN_URI, 200, validMineSkinJson());

            Optional<MineSkinResponse> result = api.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertTrue(result.isPresent());
            assertNotNull(result.get().property());
        }

        @Test
        @DisplayName("403 invalid_api_key → Optional.empty() (no sleep)")
        void invalidApiKey() throws Exception {
            httpClient.addResponse(MINESKIN_URI, 403,
                    "{\"errorCode\":\"invalid_api_key\",\"error\":\"Invalid API Key\"}");

            Optional<MineSkinResponse> result = api.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("500 unknown error → null (terminal, no retry)")
        void serverErrorTerminal() throws Exception {
            httpClient.addResponse(MINESKIN_URI, 500,
                    "{\"errorCode\":\"internal_error\",\"error\":\"broke\"}");

            Optional<MineSkinResponse> result = api.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertNull(result);
        }

        @Test
        @DisplayName("400 unknown error → null (terminal)")
        void badRequestTerminal() throws Exception {
            httpClient.addResponse(MINESKIN_URI, 400,
                    "{\"errorCode\":\"bad_request\",\"error\":\"bad\"}");

            Optional<MineSkinResponse> result = api.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertNull(result);
        }

        @Test
        @DisplayName("429 with delay → Optional.empty() (retry)")
        void rateLimit() throws Exception {
            httpClient.addResponse(MINESKIN_URI, 429,
                    "{\"error\":\"rate_limit\",\"delay\":0}");

            Optional<MineSkinResponse> result = api.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Default/unexpected status code → Optional.empty()")
        void unexpectedStatusCode() throws Exception {
            httpClient.addResponse(MINESKIN_URI, 418, "{}");

            Optional<MineSkinResponse> result = api.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertTrue(result.isEmpty());
        }
    }

    /* ================================================================== */
    /*  Contract fixtures: V1 shape (current /generate/url)                 */
    /* ================================================================== */

    @Nested
    @DisplayName("V1 contract fixtures")
    class V1ContractFixtures {

        @Test
        @DisplayName("200 OK valid V1 body → parses texture.value and idStr")
        void validBody() throws Exception {
            httpClient.addResponse(MINESKIN_URI, 200, fixture("v1-200-valid.json"));

            Optional<MineSkinResponse> result = api.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertTrue(result.isPresent());
            assertEquals(FIXTURE_TEXTURE_VALUE, result.get().property().getOriginalProperty().value());
            assertEquals(FIXTURE_SIGNATURE, result.get().property().getOriginalProperty().signature());
            assertEquals("12345", result.get().mineSkinId());
        }

        @Test
        @DisplayName("200 OK V1 body with empty texture → empty (no result, no crash)")
        void emptyTexture() throws Exception {
            httpClient.addResponse(MINESKIN_URI, 200, fixture("v1-200-empty-texture.json"));

            Optional<MineSkinResponse> result = api.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("429 V1 delayInfo → recorded waitMs honors the provider delay")
        void rateLimitDelay() throws Exception {
            long before = SkinMetrics.INSTANCE.snapshot().mineSkinDelayTotalMs();
            httpClient.addResponse(MINESKIN_URI, 429, fixture("v1-429-delay.json"));

            Optional<MineSkinResponse> result = api.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertTrue(result.isEmpty());
            long recorded = SkinMetrics.INSTANCE.snapshot().mineSkinDelayTotalMs() - before;
            assertEquals(1000, recorded);
        }
    }

    /* ================================================================== */
    /*  Contract fixtures: V2 shape (api.mineskin.org/v2) — F2             */
    /*  The V2 body nests value/signature under skin.texture.data, which    */
    /*  the V1 parser (data.texture.value) reads as empty. These tests      */
    /*  pin that a V2 body must parse or fail loudly — never silent empty.  */
    /* ================================================================== */

    @Nested
    @DisplayName("V2 contract fixtures")
    class V2ContractFixtures {

        @Test
        @DisplayName("200 OK V2 body → parses skin.texture.data.value (no silent empty)")
        void validBody() throws Exception {
            httpClient.addResponse(MINESKIN_URI, 200, fixture("v2-200-valid.json"));

            Optional<MineSkinResponse> result = api.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertTrue(result.isPresent());
            assertEquals(FIXTURE_TEXTURE_VALUE, result.get().property().getOriginalProperty().value());
            assertEquals(FIXTURE_SIGNATURE, result.get().property().getOriginalProperty().signature());
            assertEquals("c891dfac-4cd2-47a2-a557-43e7e82ce76f", result.get().mineSkinId());
        }

        @Test
        @DisplayName("200 OK V2 body with empty texture → empty (no result)")
        void emptyTexture() throws Exception {
            httpClient.addResponse(MINESKIN_URI, 200, fixture("v2-200-empty-texture.json"));

            Optional<MineSkinResponse> result = api.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("429 V2 rateLimit.next → recorded waitMs from relative delay")
        void rateLimit() throws Exception {
            long before = SkinMetrics.INSTANCE.snapshot().mineSkinDelayTotalMs();
            httpClient.addResponse(MINESKIN_URI, 429, fixture("v2-429-ratelimit.json"));

            Optional<MineSkinResponse> result = api.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertTrue(result.isEmpty());
            long recorded = SkinMetrics.INSTANCE.snapshot().mineSkinDelayTotalMs() - before;
            assertEquals(250, recorded);
        }
    }

    /* ================================================================== */
    /*  Config warnings: 401/403/404 with an unset MINESKIN_API_KEY         */
    /* ================================================================== */

    @Nested
    @DisplayName("Config warnings (MINESKIN_API_KEY)")
    class ConfigWarnings {

        private final List<String> warnings = new ArrayList<>();
        private Consumer<String> originalSink;
        private MineSkinApiHttpImpl unkeyedApi;

        @BeforeEach
        void captureWarnings() {
            originalSink = MineSkinApiHttpImpl.warningSink;
            MineSkinApiHttpImpl.warningSink = warnings::add;
            unkeyedApi = new MineSkinApiHttpImpl(httpClient, "");
        }

        @AfterEach
        void restoreSink() {
            MineSkinApiHttpImpl.warningSink = originalSink;
        }

        @Test
        @DisplayName("401 with no API key → warning mentions MINESKIN_API_KEY")
        void unauthorizedWithoutKey() throws Exception {
            httpClient.addResponse(MINESKIN_URI, 401, "{\"errorCode\":\"unauthorized\"}");

            unkeyedApi.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("MINESKIN_API_KEY"));
        }

        @Test
        @DisplayName("403 with no API key → warning mentions MINESKIN_API_KEY")
        void forbiddenWithoutKey() throws Exception {
            httpClient.addResponse(MINESKIN_URI, 403, "{\"errorCode\":\"invalid_api_key\"}");

            unkeyedApi.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("MINESKIN_API_KEY"));
        }

        @Test
        @DisplayName("404 with no API key → warning mentions MINESKIN_API_KEY")
        void missingResourceWithoutKey() throws Exception {
            httpClient.addResponse(MINESKIN_URI, 404, "{}");

            unkeyedApi.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("MINESKIN_API_KEY"));
        }

        @Test
        @DisplayName("403 with API key configured → no config warning")
        void forbiddenWithKey() throws Exception {
            MineSkinApiHttpImpl keyedApi = new MineSkinApiHttpImpl(httpClient, "test-api-key");
            httpClient.addResponse(MINESKIN_URI, 403, "{\"errorCode\":\"invalid_api_key\"}");

            keyedApi.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertTrue(warnings.isEmpty());
        }

        @Test
        @DisplayName("Warning emitted at most once per impl instance")
        void warnedOnce() throws Exception {
            httpClient.addResponse(MINESKIN_URI, 401, "{}");

            unkeyedApi.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);
            unkeyedApi.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC);

            assertEquals(1, warnings.size());
        }
    }

    /* ================================================================== */
    /*  JSON body helpers                                                  */
    /* ================================================================== */

    /** Reads a contract fixture from src/test/resources/fixtures/mineskin/. */
    private static String fixture(String name) throws IOException {
        try (InputStream in = MineSkinApiHttpImplTest.class.getResourceAsStream("/fixtures/mineskin/" + name)) {
            if (in == null) {
                throw new IOException("missing fixture: " + name);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        }
    }

    private static String validMineSkinJson() {
        return """
                {
                  "id": 12345,
                  "idStr": "12345",
                  "uuid": "550e8400-e29b-41d4-a716-446655440000",
                  "name": "Test",
                  "variant": "classic",
                  "data": {
                    "uuid": "550e8400-e29b-41d4-a716-446655440000",
                    "texture": {
                      "value": "%s",
                      "signature": "%s",
                      "url": "https://example.com/skin"
                    }
                  },
                  "timestamp": 1234567890,
                  "duration": 100,
                  "account": 1,
                  "server": "server1",
                  "private": false,
                  "views": 0,
                  "nextRequest": 0,
                  "duplicate": false
                }
                """.formatted(VALID_VALUE, VALID_SIG);
    }
}
