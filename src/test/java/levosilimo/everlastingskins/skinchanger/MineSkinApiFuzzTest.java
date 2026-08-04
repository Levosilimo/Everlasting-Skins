/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.FakeHttpClient;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse;
import levosilimo.everlastingskins.util.EndpointsConfig;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fuzz corpus for the MineSkin API response parsers (V1 success shape,
 * V2 success shape, rate-limit shape). Every property feeds malformed JSON
 * byte shapes from {@link MalformedJsonCorpus} and asserts the fail-closed
 * contract: "no result" (empty or terminal null) and no exception escapes.
 * <p>
 * Properties drive {@code genSkinInternal} directly (one HTTP round trip,
 * no retry loop), so the retry sleeps in {@code genSkin} never run and a
 * malformed 429 body resolving to a non-zero wait would be caught as a
 * delay metric instead of stalling the suite.
 * <p>
 * All HTTP responses are served by {@link FakeHttpClient}; no live endpoint
 * is ever contacted.
 */
class MineSkinApiFuzzTest {

    private static final URI MINESKIN_URI = EndpointsConfig.getURI("endpoint.mineskin.generate");
    private static final String IMAGE_URL = "https://example.com/skin.png";

    /**
     * Serializes the delay-metric properties: jqwik may shrink a failing
     * property on a background thread while the next property's tries run,
     * so the shared SkinMetrics counters need a mutual-exclusion fence.
     */
    private static final Object METRICS_LOCK = new Object();

    private FakeHttpClient httpClient;
    private MineSkinApiHttpImpl api;

    @BeforeProperty
    void setUp() {
        httpClient = new FakeHttpClient();
        // A configured key keeps the properties off the empty-key config-warning path.
        api = new MineSkinApiHttpImpl(httpClient, "test-api-key");
    }

    @Provide
    net.jqwik.api.Arbitrary<String> malformedJson() {
        return MalformedJsonCorpus.malformedJson();
    }

    @Provide
    net.jqwik.api.Arbitrary<String> v2Malformed() {
        return MalformedJsonCorpus.v2Malformed();
    }

    @Provide
    net.jqwik.api.Arbitrary<String> rateLimitMalformed() {
        return MalformedJsonCorpus.rateLimitMalformed();
    }

    /* ------------------------------------------------------------------ */
    /*  C1: V1 success-shape parse                                         */
    /* ------------------------------------------------------------------ */

    /**
     * HTTP 200 with malformed bytes: the V1 deserialization
     * ({@code MineSkinUrlResponse}) must fail closed to "no result" and
     * never let an exception escape.
     */
    @Property(tries = 100)
    @Label("C1: MineSkin V1 response parses malformed bytes to empty, never throws")
    void parseMineSkinResponse_v1_malformedBytes_returnsEmpty(
            @ForAll @From("malformedJson") String bytes) {
        httpClient.addResponse(MINESKIN_URI, 200, bytes);

        Optional<MineSkinResponse> result = assertDoesNotThrow(() ->
                api.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC));

        assertTrue(!result.isPresent(),
                () -> "malformed bytes produced a V1 skin: " + excerpt(bytes));
    }

    /* ------------------------------------------------------------------ */
    /*  C2: V2 success-shape parse                                         */
    /* ------------------------------------------------------------------ */

    /**
     * HTTP 200 with malformed V2-shaped bodies (truncated v2 documents and
     * corrupted v2 shapes whose value slot is empty, absent or null): the
     * {@code JsonUtils.parseJson}-based V2 path must fail closed to "no
     * result" and never let an exception escape.
     */
    @Property(tries = 100)
    @Label("C2: MineSkin V2 response parses malformed bytes to empty, never throws")
    void parseMineSkinResponse_v2_malformedBytes_returnsEmpty(
            @ForAll @From("v2Malformed") String bytes) {
        httpClient.addResponse(MINESKIN_URI, 200, bytes);

        Optional<MineSkinResponse> result = assertDoesNotThrow(() ->
                api.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC));

        assertTrue(!result.isPresent(),
                () -> "malformed bytes produced a V2 skin: " + excerpt(bytes));
    }

    /* ------------------------------------------------------------------ */
    /*  C3: rate-limit shape parse                                         */
    /* ------------------------------------------------------------------ */

    /**
     * HTTP 429 with malformed bytes: the rate-limit parsers (V1
     * {@code MineSkinErrorDelayResponse} and the V2 {@code rateLimit.next}
     * shapes) must fail closed to a zero wait — no exception escapes, the
     * result is empty, and no delay is recorded on {@link SkinMetrics}
     * (observable proof that no provider-controlled sleep was scheduled).
     */
    @Property(tries = 100)
    @Label("C3: MineSkin rate-limit parses malformed bytes to zero wait, never throws")
    void parseMineSkinRateLimit_malformedBytes_returnsZeroOrDefault(
            @ForAll @From("rateLimitMalformed") String bytes) {
        synchronized (METRICS_LOCK) {
            SkinMetrics.INSTANCE.reset();
            httpClient.addResponse(MINESKIN_URI, 429, bytes);

            Optional<MineSkinResponse> result = assertDoesNotThrow(() ->
                    api.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC));

            assertTrue(!result.isPresent(),
                    () -> "malformed rate-limit bytes produced a result: " + excerpt(bytes));
            assertEquals(0, SkinMetrics.INSTANCE.snapshot().mineSkinDelayTotalMs(),
                    () -> "malformed rate-limit bytes scheduled a wait: " + excerpt(bytes));
        }
    }

    /* ------------------------------------------------------------------ */
    /*  C4: no exception escapes across the whole parse surface            */
    /* ------------------------------------------------------------------ */

    /**
     * Malformed bytes served under every status code the MineSkin API
     * classifies (200, 400, 401, 403, 404, 429, 500) must never escape any
     * parse path as an exception, and a malformed 429 body must never
     * schedule a provider-controlled sleep.
     */
    @Property(tries = 60)
    @Label("C4: malformed bytes never escape any MineSkin API parse entry point")
    void parseMineSkinApiResponse_malformedBytes_noExceptionEscapes(
            @ForAll @From("malformedJson") String bytes) {
        synchronized (METRICS_LOCK) {
            SkinMetrics.INSTANCE.reset();
            int[] statuses = {200, 400, 401, 403, 404, 429, 500};
            for (int status : statuses) {
                httpClient.addResponse(MINESKIN_URI, status, bytes);
                assertDoesNotThrow(() -> api.genSkinInternal(IMAGE_URL, SkinVariant.CLASSIC),
                        () -> "status " + status + " escaped on: " + excerpt(bytes));
            }
            assertEquals(0, SkinMetrics.INSTANCE.snapshot().mineSkinDelayTotalMs(),
                    () -> "malformed 429 bytes scheduled a wait: " + excerpt(bytes));
        }
    }

    /* ================================================================== */
    /*  Helpers                                                           */
    /* ================================================================== */

    private static String excerpt(String bytes) {
        if (bytes.length() <= 96) {
            return bytes;
        }
        return bytes.substring(0, 96) + "…(" + bytes.length() + " bytes)";
    }
}
