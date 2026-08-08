/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.HttpClient;
import levosilimo.everlastingskins.util.HttpClient.HttpMethod;
import levosilimo.everlastingskins.util.HttpClient.HttpType;
import levosilimo.everlastingskins.util.HttpClient.RequestBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Skin-resolution coverage with a deterministic fake HTTP client (memory
 * #1115: no live Mojang/MineSkin calls — all responses come from the
 * fixtures/ tree). Exercises the same :common providers the 26.2 binding
 * wires up in SkinCommand.
 */
class SkinResolutionProviderTest {

    /** Serves fixture files by path substring; 404 for anything else. */
    private static final class FixtureHttpClient implements HttpClient {
        private final Path fixtures;

        FixtureHttpClient(Path fixtures) {
            this.fixtures = fixtures;
        }

        @Override
        public HttpResponse execute(URI uri, RequestBody requestBody, HttpType accepts,
                                    String userAgent, HttpMethod method,
                                    Map<String, String> headers, int timeout) throws IOException {
            String path = uri.getPath();
            String body;
            int status;
            if (path.contains("/users/profiles/minecraft/Notch")) {
                body = "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}";
                status = 200;
            } else if (path.contains("/session/minecraft/profile/")) {
                body = fixture("mojang/profile-200-notch.json");
                status = 200;
            } else if (path.contains("/generate/url")) {
                body = fixture("mineskin/v2-200-valid.json");
                status = 200;
            } else {
                body = fixture("mojang/uuid-404-unknown-name.json");
                status = 404;
            }
            return new HttpResponse(status, body, Map.of());
        }

        private String fixture(String name) throws IOException {
            return Files.readString(fixtures.resolve(name), StandardCharsets.UTF_8);
        }
    }

    private MojangApiHttpImpl mojang;
    private MineSkinApiHttpImpl mineskin;

    @BeforeEach
    void setUp() throws Exception {
        Path fixtures = Path.of("src/test/resources/fixtures").toAbsolutePath();
        assertTrue(Files.isDirectory(fixtures), "fixtures dir must exist: " + fixtures);
        FixtureHttpClient http = new FixtureHttpClient(fixtures);
        mojang = new MojangApiHttpImpl(MojangEndpoints.DEFAULT, http);
        mineskin = new MineSkinApiHttpImpl(http, "", false, java.util.List.of());
    }

    @Test
    @DisplayName("Mojang resolution parses the mocked profile into a non-empty skin property")
    void resolution_mojang_mockedProfile() {
        Optional<MojangSkinDataResult> result = mojang.getSkin("Notch");
        assertTrue(result.isPresent(), "mocked Notch profile must resolve");
        assertFalse(result.get().skinProperty().isEmpty());
        assertEquals("MojangAPI", result.get().skinProperty().getSource());
    }

    @Test
    @DisplayName("Unknown names resolve to empty (provider chain 404s)")
    void resolution_fallbackPolicyOn404() {
        Optional<MojangSkinDataResult> result = mojang.getSkin("NobodyKnowsThisName");
        assertTrue(result.isEmpty(), "a 404 must not fabricate a skin");
    }

    @Test
    @DisplayName("MineSkin v2 generation parses the mocked response into a texture property")
    void resolution_mineskin_mockedV2() {
        var response = mineskin.genSkin("https://example.com/skin.png",
                levosilimo.everlastingskins.enums.SkinVariant.CLASSIC);
        assertNotNull(response);
        assertFalse(response.property().isEmpty());
        assertEquals("MineSkin", response.property().getSource());
    }
}
