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
import org.junit.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * URL-allowlist regression (audit fix): the guard must reject a disallowed
 * domain BEFORE any HTTP request, so the fakes below fail the test if the
 * network layer is ever touched — deterministic, no live HTTP.
 */
public class MineSkinApiAllowlistTest {

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
    public void disallowedDomainRejectedWithoutNetwork() throws Exception {
        MineSkinApiHttpImpl api = new MineSkinApiHttpImpl(new NeverHttpClient(), "", true, DOMAINS);

        Optional<MineSkinResponse> result =
                api.genSkinInternal("https://evil.example.com/skin.png", SkinVariant.CLASSIC);

        assertFalse(result.isPresent());
    }

    @Test
    public void disallowedSubdomainRejected() throws Exception {
        MineSkinApiHttpImpl api = new MineSkinApiHttpImpl(new NeverHttpClient(), "", true, DOMAINS);

        Optional<MineSkinResponse> result =
                api.genSkinInternal("https://imgur.com.evil.example/skin.png", SkinVariant.CLASSIC);

        assertFalse(result.isPresent());
    }

    @Test
    public void allowedDomainPassesGuard() throws Exception {
        MineSkinApiHttpImpl api = new MineSkinApiHttpImpl(new CannedHttpClient(), "", true, DOMAINS);

        // A 400 is terminal (null): only possible if the guard let the URL through.
        Optional<MineSkinResponse> result =
                api.genSkinInternal("https://imgur.com/skin.png", SkinVariant.CLASSIC);

        assertNull(result);
    }

    @Test
    public void skinCommandWiresAllowlistOn() throws Exception {
        // SkinRestorerCommandTest tears the field down to null / injects
        // fakes; skip unless the real field initializer product is present.
        Field field = SkinCommand.class.getDeclaredField("mineSkinApi");
        field.setAccessible(true);
        Object api = field.get(null);
        assumeTrue("another test injected a fake", api instanceof MineSkinApiHttpImpl);

        Field enabled = MineSkinApiHttpImpl.class.getDeclaredField("allowlistEnabled");
        enabled.setAccessible(true);
        assertTrue((Boolean) enabled.get(api));

        Field domains = MineSkinApiHttpImpl.class.getDeclaredField("allowlistDomains");
        domains.setAccessible(true);
        assertTrue(((List<?>) domains.get(api)).contains("imgur.com"));
    }
}
