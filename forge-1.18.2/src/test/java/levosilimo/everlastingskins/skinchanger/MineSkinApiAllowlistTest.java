/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse;
import levosilimo.everlastingskins.util.HttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * URL-allowlist regression (audit fix): the guard must reject a disallowed
 * domain BEFORE any HTTP request, so the fakes below fail the test if the
 * network layer is ever touched — deterministic, no live HTTP.
 */
class MineSkinApiAllowlistTest {

    private static final List<String> DOMAINS = Arrays.asList("imgur.com", "textures.minecraft.net");

    /** Fails the test if any HTTP request is attempted. */
    private static final class NeverHttpClient implements HttpClient {
        @Override
        public HttpResponse execute(URI uri, RequestBody requestBody, HttpType accepts,
                                    String userAgent, HttpMethod method,
                                    Map<String, String> headers, int timeout) {
            throw new AssertionError("HTTP must not be touched: " + uri);
        }
    }

    /** Terminal 400 response: proves the request reached the HTTP layer. */
    private static final class CannedHttpClient implements HttpClient {
        @Override
        public HttpResponse execute(URI uri, RequestBody requestBody, HttpType accepts,
                                    String userAgent, HttpMethod method,
                                    Map<String, String> headers, int timeout) {
            return new HttpResponse(400, "{\"errorCode\":\"bad_request\",\"error\":\"bad\"}",
                    Collections.<String, List<String>>emptyMap());
        }
    }

    @Test
    @DisplayName("disallowed domain + allowlist on -> rejected pre-network")
    void disallowedDomainRejectedWithoutNetwork() throws Exception {
        MineSkinApiHttpImpl api = new MineSkinApiHttpImpl(new NeverHttpClient(), "", true, DOMAINS);

        Optional<MineSkinResponse> result =
                api.genSkinInternal("https://evil.example.com/skin.png", SkinVariant.CLASSIC);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("subdomain spoof of an allowed eTLD+1 -> rejected")
    void disallowedSubdomainRejected() throws Exception {
        MineSkinApiHttpImpl api = new MineSkinApiHttpImpl(new NeverHttpClient(), "", true, DOMAINS);

        Optional<MineSkinResponse> result =
                api.genSkinInternal("https://imgur.com.evil.example/skin.png", SkinVariant.CLASSIC);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("allowed domain + allowlist on -> request reaches the HTTP layer")
    void allowedDomainPassesGuard() throws Exception {
        MineSkinApiHttpImpl api = new MineSkinApiHttpImpl(new CannedHttpClient(), "", true, DOMAINS);

        // A 400 is terminal (null): only possible if the guard let the URL through.
        Optional<MineSkinResponse> result =
                api.genSkinInternal("https://imgur.com/skin.png", SkinVariant.CLASSIC);

        assertNull(result);
    }
}
