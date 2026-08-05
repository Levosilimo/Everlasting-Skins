/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.permission.TestConfigSupport;
import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.HttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Request-count contract for the Mojang lookup chain: after the Eclipse stub
 * removal, one UUID lookup and one profile lookup must each issue exactly one
 * HTTP request. A counting client records every execute() per URL so a
 * duplicated request (e.g. an aliased endpoint hit twice) fails the count.
 */
class MojangApiRequestCountTest {

    private static final String PLAYER_NAME = "Notch";
    private static final UUID PLAYER_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final String NO_DASH_UUID = PLAYER_UUID.toString().replace("-", "");

    @BeforeAll
    static void loadConfig() {
        TestConfigSupport.loadDefaults();
    }

    private static final MojangEndpoints TEST_ENDPOINTS = new MojangEndpoints(
            "http://test.local/uuid/mojang/%playerName%",
            "http://test.local/uuid/minetools/%playerName%",
            "http://test.local/profile/mojang/%uuid%",
            "http://test.local/profile/minetools/%uuid%"
    );

    private final CountingHttpClient httpClient = new CountingHttpClient();
    private final MojangApiHttpImpl api = new MojangApiHttpImpl(TEST_ENDPOINTS, httpClient);

    @Test
    @DisplayName("getUUID(Notch) issues exactly 1 request")
    void getUUID_issuesExactlyOneRequest() {
        URI mojangUri = URI.create("http://test.local/uuid/mojang/" + PLAYER_NAME);
        httpClient.register(mojangUri, 200,
                "{\"name\":\"" + PLAYER_NAME + "\",\"id\":\"" + NO_DASH_UUID + "\"}");

        Optional<UUID> result = api.getUUID(PLAYER_NAME);

        assertTrue(result.isPresent(), "UUID lookup must succeed against the Mojang endpoint");
        assertEquals(PLAYER_UUID, result.get());
        assertEquals(1, httpClient.requestCount(mojangUri),
                "Mojang UUID endpoint must be hit exactly once");
        assertEquals(1, httpClient.totalRequests(),
                "getUUID must issue exactly 1 HTTP request, got " + httpClient.totalRequests());
    }

    @Test
    @DisplayName("getProfile(lookup) issues exactly 1 request")
    void getProfile_issuesExactlyOneRequest() {
        URI mojangUri = URI.create("http://test.local/profile/mojang/" + NO_DASH_UUID);
        httpClient.register(mojangUri, 200,
                "{\"id\":\"x\",\"name\":\"x\",\"properties\":[{\"name\":\"textures\",\"value\":\"val\",\"signature\":\"sig\"}]}");

        Optional<CustomSkinProperty> result = api.getProfile(new ProfileLookup(PLAYER_NAME, PLAYER_UUID));

        assertTrue(result.isPresent(), "profile lookup must succeed against the Mojang endpoint");
        assertEquals("val", result.get().getOriginalProperty().value());
        assertEquals(1, httpClient.requestCount(mojangUri),
                "Mojang profile endpoint must be hit exactly once");
        assertEquals(1, httpClient.totalRequests(),
                "getProfile must issue exactly 1 HTTP request, got " + httpClient.totalRequests());
    }

    /**
     * Counts every execute() per URI. Unregistered URIs answer 404 so the
     * fallback chain falls through instead of failing the lookup.
     */
    private static final class CountingHttpClient implements HttpClient {

        private final Map<URI, HttpResponse> responses = new HashMap<>();
        private final Map<URI, Long> counts = new ConcurrentHashMap<>();

        void register(URI uri, int statusCode, String body) {
            responses.put(uri, new HttpResponse(statusCode, body, Map.of()));
        }

        long requestCount(URI uri) {
            return counts.getOrDefault(uri, 0L);
        }

        long totalRequests() {
            return counts.values().stream().mapToLong(Long::longValue).sum();
        }

        @Override
        public HttpResponse execute(URI uri, RequestBody requestBody, HttpType accepts,
                                    String userAgent, HttpMethod method,
                                    Map<String, String> headers, int timeout) throws IOException {
            counts.merge(uri, 1L, Long::sum);
            HttpResponse response = responses.get(uri);
            if (response == null) {
                return new HttpResponse(404, "", Map.of());
            }
            return response;
        }
    }
}
