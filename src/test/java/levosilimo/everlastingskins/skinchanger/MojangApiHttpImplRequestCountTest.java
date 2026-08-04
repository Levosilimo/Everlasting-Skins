/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.FakeHttpClient;
import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract: exactly one HTTP request per Mojang lookup. The dead Eclipse
 * provider stub previously added a second request to every UUID/profile
 * lookup (a wasted call against the defunct skinsrestorer.net host before
 * the real Mojang call ran), so a counting client must observe a single
 * request against the Mojang URL and none against the Eclipse URL.
 */
class MojangApiHttpImplRequestCountTest {

    private static final String PLAYER_NAME = "Notch";
    private static final UUID PLAYER_UUID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final String NO_DASH_UUID = "069a79f444e94726a5befca90e38aaf5";

    private static final MojangEndpoints TEST_ENDPOINTS = new MojangEndpoints(
            "http://test.local/uuid/eclipse/%playerName%",
            "http://test.local/uuid/mojang/%playerName%",
            "http://test.local/uuid/minetools/%playerName%",
            "http://test.local/profile/eclipse/%uuid%",
            "http://test.local/profile/mojang/%uuid%",
            "http://test.local/profile/minetools/%uuid%"
    );

    private CountingHttpClient httpClient;
    private MojangApiHttpImpl api;

    @BeforeEach
    void setUp() {
        httpClient = new CountingHttpClient();
        api = new MojangApiHttpImpl(TEST_ENDPOINTS, httpClient);
    }

    @Test
    @DisplayName("getUUID(Notch) issues exactly 1 request (Mojang, no Eclipse)")
    void getUuidIssuesExactlyOneRequest() {
        URI mojangUuidUri = URI.create("http://test.local/uuid/mojang/" + PLAYER_NAME);
        httpClient.addResponse(mojangUuidUri, 200,
                "{\"name\":\"" + PLAYER_NAME + "\",\"id\":\"" + NO_DASH_UUID + "\"}");

        Optional<UUID> result = api.getUUID(PLAYER_NAME);

        assertTrue(result.isPresent());
        assertEquals(PLAYER_UUID, result.get());
        assertEquals(1, httpClient.totalRequests());
        assertEquals(1, httpClient.requestsTo(mojangUuidUri));
        assertEquals(0, httpClient.requestsTo(URI.create("http://test.local/uuid/eclipse/" + PLAYER_NAME)));
    }

    @Test
    @DisplayName("getProfile(lookup) issues exactly 1 request (Mojang, no Eclipse)")
    void getProfileIssuesExactlyOneRequest() {
        URI mojangProfileUri = URI.create("http://test.local/profile/mojang/" + NO_DASH_UUID);
        httpClient.addResponse(mojangProfileUri, 200,
                "{\"id\":\"x\",\"name\":\"x\",\"properties\":[{\"name\":\"textures\",\"value\":\"v\",\"signature\":\"s\"}]}");

        Optional<CustomSkinProperty> result = api.getProfile(new ProfileLookup(PLAYER_NAME, PLAYER_UUID));

        assertTrue(result.isPresent());
        assertEquals("v", result.get().getOriginalProperty().getValue());
        assertEquals(1, httpClient.totalRequests());
        assertEquals(1, httpClient.requestsTo(mojangProfileUri));
        assertEquals(0, httpClient.requestsTo(URI.create("http://test.local/profile/eclipse/" + PLAYER_UUID)));
    }

    /** FakeHttpClient that records every executed URI so per-URL counts are assertable. */
    private static final class CountingHttpClient extends FakeHttpClient {

        private final List<URI> requests = new ArrayList<URI>();

        @Override
        public HttpResponse execute(URI uri, HttpClient.RequestBody requestBody, HttpClient.HttpType accepts,
                String userAgent, HttpClient.HttpMethod method, Map<String, String> headers,
                int timeout) throws IOException {
            requests.add(uri);
            return super.execute(uri, requestBody, accepts, userAgent, method, headers, timeout);
        }

        int totalRequests() {
            return requests.size();
        }

        int requestsTo(URI uri) {
            int count = 0;
            for (URI request : requests) {
                if (request.equals(uri)) {
                    count++;
                }
            }
            return count;
        }
    }
}
