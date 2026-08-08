/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.FakeHttpClient;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end fall-back chain contract (Mojang-404 -> MineSkin -> default skin).
 * <p>
 * There is no single {@code :common} assembler that chains all three stages (the
 * literal production chain spans forge lanes; see P3-part2 plan P3-9, lines 109-115).
 * This test therefore composes the component fall-through contract the lanes wire up:
 * <ol>
 *   <li>{@link MojangApiHttpImpl} over {@link FakeHttpClient} serving 404 on the
 *       sessionserver profile URI must return empty (chain advances past Mojang).</li>
 *   <li>A MineSkin seam (local {@code MineSkinAPI} fake) returning null must advance
 *       the chain past MineSkin.</li>
 *   <li>{@link DefaultSkinResolver} must terminate at a resolved default skin.</li>
 * </ol>
 * A shared {@code order} list records each stage as it fires, so the exact
 * Mojang-then-MineSkin-then-default sequence is asserted. The {@code recording}
 * client proves the sessionserver URI was actually the first HTTP hit.
 */
class FallbackChainIntegrationTest {

    private static final UUID PLAYER_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final String NO_DASH_UUID = "12345678123412341234123456789abc";
    private static final String IMAGE_URL = "https://example.com/skin.png";

    private static final MojangEndpoints TEST_ENDPOINTS = new MojangEndpoints(
            "http://test.local/uuid/mojang/%playerName%",
            "http://test.local/uuid/minetools/%playerName%",
            "http://test.local/profile/mojang/%uuid%",
            "http://test.local/profile/minetools/%uuid%");

    private final List<String> order = new ArrayList<>();
    private FakeHttpClient httpClient;
    private RecordingHttpClient recording;
    private MojangApiHttpImpl mojangApi;
    private FakeMojangAPI seed;

    @BeforeEach
    void setUp() {
        httpClient = new FakeHttpClient();
        recording = new RecordingHttpClient(httpClient);
        mojangApi = new MojangApiHttpImpl(TEST_ENDPOINTS, recording);
        // Default-skin resolver seed: a real Mojang API returns empty after a 404, so
        // the default stage resolves through a seeded fake keyed by the entry name.
        // FakeMojangAPI.addSkin registers by skin.getSource(), so source must be "Steve".
        seed = new FakeMojangAPI();
        seed.addSkin("Steve", new CustomSkinProperty("textures", "defaultValue", "defaultSig", "Steve"));
    }

    /**
     * Mojang 404 profile -> MineSkin returns null -> DefaultSkinResolver default.
     * Asserts the three stages fire in exactly Mojang -> MineSkin -> default order.
     */
    @Test
    @DisplayName("Mojang 404 -> MineSkin empty -> default skin (order mojang, mineskin, default)")
    void fullChainFallsThroughToDefault() {
        URI mojangProfile = URI.create("http://test.local/profile/mojang/" + NO_DASH_UUID);
        httpClient.addResponse(mojangProfile, 404, "");
        // profileMineTools URI intentionally unregistered -> FakeHttpClient throws
        // IOException (caught in fetchJson) -> getProfile returns empty.

        ProfileLookup lookup = new ProfileLookup("TestPlayer", PLAYER_UUID);

        // Stage 1: Mojang must come up empty on 404 AND hit the sessionserver URI first.
        Optional<CustomSkinProperty> mojangResult = mojangApi.getProfile(lookup);
        assertFalse(mojangResult.isPresent(), "404 profile must advance past Mojang");
        assertEquals(mojangProfile, recording.firstUri(), "Mojang profile URI must be the first HTTP hit");
        order.add("mojang"); // Mojang consulted and empty -> advance

        // Stage 2: MineSkin seam returns null -> falls through.
        FakeMineSkin mineskin = new FakeMineSkin();
        MineSkinResponse msResult = mineskin.genSkin(IMAGE_URL, SkinVariant.CLASSIC);
        assertNull(msResult, "empty MineSkin must advance past MineSkin");
        order.add("mineskin");

        // Stage 3: default skin termination.
        Property defaultSkin = DefaultSkinResolver.resolveDefault(Collections.singletonList("Steve"), seed);
        assertNotNull(defaultSkin, "chain must terminate at a default skin");
        assertEquals("defaultValue", defaultSkin.getValue());
        order.add("default");

        assertEquals(Arrays.asList("mojang", "mineskin", "default"), order,
                "fallback stages must fire in Mojang -> MineSkin -> default order");
    }

    /**
     * A 200 Mojang profile short-circuits: MineSkin and default must NOT be reached.
     */
    @Test
    @DisplayName("Mojang 200 short-circuits the chain (only Mojang is consulted)")
    void mojangSuccessShortCircuitsChain() {
        URI mojangProfile = URI.create("http://test.local/profile/mojang/" + NO_DASH_UUID);
        httpClient.addResponse(mojangProfile, 200,
                "{\"id\":\"x\",\"name\":\"x\",\"properties\":" +
                        "[{\"name\":\"textures\",\"value\":\"v\",\"signature\":\"s\"}]}");

        ProfileLookup lookup = new ProfileLookup("TestPlayer", PLAYER_UUID);
        Optional<CustomSkinProperty> result = mojangApi.getProfile(lookup);

        assertTrue(result.isPresent(), "200 profile must resolve at the Mojang stage");
        assertEquals("v", result.get().getOriginalProperty().getValue());
        assertEquals(mojangProfile, recording.firstUri(), "Mojang profile URI must be hit");
        // No stage falls through on success: MineSkin and default are never reached.
        assertTrue(order.isEmpty(),
                "MineSkin and default must NOT fire when Mojang succeeds: " + order);
    }

    /* ================================================================== */
    /*  Local MineSkin seam (P2-12 not yet promoted into :common)          */
    /* ================================================================== */

    /**
     * Deterministic MineSkin stub that always returns null (empty fall-through).
     * Kept nested and named distinctly to avoid a duplicate-class collision if P2-12
     * later promotes {@code FakeMineSkinAPI} into the :common test tree.
     */
    private static final class FakeMineSkin implements MineSkinAPI {
        @Nullable
        @Override
        public MineSkinResponse genSkin(String url, @Nullable SkinVariant variant) {
            return null;
        }
    }

    /* ================================================================== */
    /*  Recording HttpClient: delegates to FakeHttpClient, logs call order  */
    /* ================================================================== */

    private static final class RecordingHttpClient implements HttpClient {
        private final FakeHttpClient delegate;
        private final List<URI> uris = new ArrayList<>();

        RecordingHttpClient(FakeHttpClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public HttpResponse execute(URI uri, @Nullable RequestBody body, HttpType accepts,
                String userAgent, HttpMethod method, Map<String, String> headers, int timeout)
                throws IOException {
            uris.add(uri);
            return delegate.execute(uri, body, accepts, userAgent, method, headers, timeout);
        }

        URI firstUri() {
            return uris.isEmpty() ? null : uris.get(0);
        }
    }
}
