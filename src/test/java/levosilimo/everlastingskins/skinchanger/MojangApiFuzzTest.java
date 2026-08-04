/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.FakeHttpClient;
import levosilimo.everlastingskins.permission.TestConfigSupport;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fuzz corpus for the Mojang API JSON parsers (AI-generated code surface,
 * cf. Pearce et al., S&P 2022). Every property feeds malformed JSON byte
 * shapes from {@link MalformedJsonCorpus} to the UUID and profile parsers
 * and asserts the fail-closed contract: the result is {@link Optional#EMPTY}
 * ("no result") and no exception escapes the public API.
 * <p>
 * The malformed corpus never contains a valid 32-hex UUID or a complete
 * textures property (see the corpus contract), so {@code returnsEmpty} is a
 * real fail-closed assertion, not a base64 semantic check.
 * <p>
 * All HTTP responses are served by {@link FakeHttpClient}; no live endpoint
 * is ever contacted.
 */
class MojangApiFuzzTest {

    private static final String PLAYER_NAME = "FuzzPlayer";
    private static final UUID PLAYER_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final String NO_DASH_UUID = "12345678123412341234123456789abc";

    private static final MojangEndpoints TEST_ENDPOINTS = new MojangEndpoints(
            "http://test.local/uuid/mojang/%playerName%",
            "http://test.local/uuid/minetools/%playerName%",
            "http://test.local/session/profile/mojang/%uuid%",
            "http://test.local/profile/minetools/%uuid%"
    );

    private URI mojangUuidUri;
    private URI mineToolsUuidUri;
    private URI mojangProfileUri;
    private URI mineToolsProfileUri;

    private FakeHttpClient httpClient;
    private MojangApiHttpImpl api;

    @BeforeContainer
    static void loadConfig() {
        TestConfigSupport.loadDefaults();
    }

    @BeforeProperty
    void setUp() {
        mojangUuidUri = URI.create("http://test.local/uuid/mojang/" + PLAYER_NAME);
        mineToolsUuidUri = URI.create("http://test.local/uuid/minetools/" + PLAYER_NAME);
        mojangProfileUri = URI.create("http://test.local/session/profile/mojang/" + NO_DASH_UUID);
        mineToolsProfileUri = URI.create("http://test.local/profile/minetools/" + NO_DASH_UUID);

        httpClient = new FakeHttpClient();
        api = new MojangApiHttpImpl(TEST_ENDPOINTS, httpClient);
    }

    @Provide
    net.jqwik.api.Arbitrary<String> malformedJson() {
        return MalformedJsonCorpus.malformedJson();
    }

    @Provide
    net.jqwik.api.Arbitrary<String> truncatedEscapeJson() {
        // Truncated \\uXXXX escapes; the strict Gson fromJson raises a raw
        // NumberFormatException on every one of these shapes.
        return net.jqwik.api.Arbitraries.of(
                "{\"id\":\"\\u12\"}",
                "{\"id\":\"\\u1\"}",
                "{\"id\":\"\\u\"}",
                "{\"id\":\"\\u12\\u\"}",
                "{\"id\":\"\\uZZZZ\"}"
        );
    }

    /* ------------------------------------------------------------------ */
    /*  B1: UUID response parse                                            */
    /* ------------------------------------------------------------------ */

    /**
     * The UUID endpoint (Mojang {@code /uuid/...} and the MineTools fallback)
     * serves malformed bytes with HTTP 200. The parser must fail closed: the
     * result is empty and no exception escapes the lookup.
     */
    @Property(tries = 100)
    @Label("B1: Mojang UUID response parses malformed bytes to empty, never throws")
    void parseMojangUuidResponse_malformedBytes_returnsEmpty(
            @ForAll @From("malformedJson") String bytes) {
        httpClient.addResponse(mojangUuidUri, 200, bytes);
        httpClient.addResponse(mineToolsUuidUri, 200, bytes);

        Optional<UUID> result = assertDoesNotThrow(() -> api.getUUID(PLAYER_NAME));

        assertTrue(!result.isPresent(),
                () -> "malformed bytes produced a UUID: " + excerpt(bytes));
    }

    /* ------------------------------------------------------------------ */
    /*  B2: Mojang profile response parse                                  */
    /* ------------------------------------------------------------------ */

    /**
     * The profile endpoint (Mojang and MineTools fallback) serves malformed
     * bytes with HTTP 200. The texture-property extraction must fail closed.
     */
    @Property(tries = 100)
    @Label("B2: Mojang profile response parses malformed bytes to empty, never throws")
    void parseMojangProfileResponse_malformedBytes_returnsEmpty(
            @ForAll @From("malformedJson") String bytes) {
        httpClient.addResponse(mojangProfileUri, 200, bytes);
        httpClient.addResponse(mineToolsProfileUri, 200, bytes);

        Optional<CustomSkinProperty> result = assertDoesNotThrow(() ->
                api.getProfile(new ProfileLookup(PLAYER_NAME, PLAYER_UUID)));

        assertTrue(!result.isPresent(),
                () -> "malformed bytes produced a skin property: " + excerpt(bytes));
    }

    /* ------------------------------------------------------------------ */
    /*  B3: session profile parse (getSkin by UUID)                        */
    /* ------------------------------------------------------------------ */

    /**
     * The sessionserver-shaped profile response is parsed on the
     * {@code getSkin} UUID path, which skips the username/UUID resolution
     * and goes straight to the session profile lookup. Malformed bytes must
     * yield "no result" without an exception escape.
     */
    @Property(tries = 100)
    @Label("B3: session profile parse on the getSkin UUID path fails closed")
    void parseMojangSessionProfile_malformedBytes_returnsEmpty(
            @ForAll @From("malformedJson") String bytes) {
        httpClient.addResponse(mojangProfileUri, 200, bytes);

        Optional<MojangSkinDataResult> result = assertDoesNotThrow(() ->
                api.getSkin(PLAYER_UUID.toString()));

        assertTrue(!result.isPresent(),
                () -> "malformed bytes produced a skin result: " + excerpt(bytes));
    }

    /* ------------------------------------------------------------------ */
    /*  B4: no exception escapes across the whole parse surface            */
    /* ------------------------------------------------------------------ */

    /**
     * Every public parse entry point must swallow malformed bytes: no
     * exception (checked or unchecked) may escape, regardless of which
     * provider is serving the corrupted body.
     */
    @Property(tries = 60)
    @Label("B4: malformed bytes never escape any Mojang API parse entry point")
    void parseMojangApiResponse_malformedBytes_noExceptionEscapes(
            @ForAll @From("malformedJson") String bytes) {
        httpClient.addResponse(mojangUuidUri, 200, bytes);
        httpClient.addResponse(mineToolsUuidUri, 200, bytes);
        httpClient.addResponse(mojangProfileUri, 200, bytes);
        httpClient.addResponse(mineToolsProfileUri, 200, bytes);

        assertDoesNotThrow(() -> api.getUUID(PLAYER_NAME),
                () -> "getUUID escaped on: " + excerpt(bytes));
        assertDoesNotThrow(() -> api.getProfile(new ProfileLookup(PLAYER_NAME, PLAYER_UUID)),
                () -> "getProfile escaped on: " + excerpt(bytes));
        assertDoesNotThrow(() -> api.getSkin(PLAYER_UUID.toString()),
                () -> "getSkin escaped on: " + excerpt(bytes));
    }

    /* ------------------------------------------------------------------ */
    /*  B5: truncated \\uXXXX escapes (raw NumberFormatException)          */
    /* ------------------------------------------------------------------ */

    /**
     * Truncated {@code \\uXXXX} escapes (e.g. {@code {"id":"\\u12"}}) make
     * the strict Gson {@code fromJson} throw a raw NumberFormatException
     * instead of JsonSyntaxException, which escaped the parsers before the
     * round-9 production fix. The UUID lookup must fail closed on every
     * variant.
     */
    @Property(tries = 20)
    @Label("B5: truncated \\uXXXX escapes fail closed on the Mojang UUID lookup")
    void parseMojangUuidResponse_truncatedEscape_returnsEmpty(
            @ForAll @From("truncatedEscapeJson") String bytes) {
        httpClient.addResponse(mojangUuidUri, 200, bytes);
        httpClient.addResponse(mineToolsUuidUri, 200, bytes);

        Optional<UUID> result = assertDoesNotThrow(() -> api.getUUID(PLAYER_NAME));

        assertTrue(!result.isPresent(),
                () -> "truncated escape produced a UUID: " + excerpt(bytes));
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
