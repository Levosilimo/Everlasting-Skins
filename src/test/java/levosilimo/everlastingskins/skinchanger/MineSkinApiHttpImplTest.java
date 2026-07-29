package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.FakeHttpClient;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse;
import levosilimo.everlastingskins.util.EndpointsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

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

    private FakeHttpClient httpClient;
    private MineSkinApiHttpImpl api;

    @BeforeEach
    void setUp() {
        httpClient = new FakeHttpClient();
        api = new MineSkinApiHttpImpl(httpClient, "");
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
    /*  JSON body helpers                                                  */
    /* ================================================================== */

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
