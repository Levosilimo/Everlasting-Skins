/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.FakeHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CosmeticaApi}: GET /players/{id} parsing (player and
 * user account variants) and the fail-closed contract for every non-200
 * status, transport error and malformed JSON. All HTTP responses are served
 * by {@link FakeHttpClient}; no live endpoint is ever contacted.
 */
class CosmeticaApiTest {

    private static final String ENDPOINT_TEMPLATE = "http://test.local/players/%player%";
    private static final String CAPE_TEXTURE = "https://assets.namet.ag/test/cape.png";

    private FakeHttpClient httpClient;
    private CosmeticaApi api;

    @BeforeEach
    void setUp() {
        httpClient = new FakeHttpClient();
        api = new CosmeticaApi(httpClient, ENDPOINT_TEMPLATE);
    }

    private URI playerUri(String name) {
        return URI.create(ENDPOINT_TEMPLATE.replace("%player%", name));
    }

    @Nested
    @DisplayName("Player account variant (isUser=false)")
    class PlayerVariant {

        @Test
        @DisplayName("parses a player with an external cape")
        void parses_player_with_external_cape() {
            httpClient.addResponse(playerUri("Notch"), 200,
                    "{\"isUser\":false,\"player\":{\"uuid\":\"069a79f4-44e9-4726-a5be-fca90e38aaf5\","
                            + "\"username\":\"Notch\",\"type\":\"player\","
                            + "\"externalCape\":{\"id\":\"578631b3-4049-42bd-a205-3971f0965c5f\","
                            + "\"service\":\"optifine\",\"serviceName\":\"OptiFine\",\"texture\":\"" + CAPE_TEXTURE + "\","
                            + "\"hasElytra\":true,\"active\":true,\"frames\":1}}}");

            CosmeticaApi.CosmeticaPlayer player = api.getPlayer("Notch");

            assertNotNull(player);
            assertFalse(player.isUser());
            assertEquals("Notch", player.account().username());
            assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", player.account().uuid());
            assertTrue(player.hasCape());
            assertEquals(CAPE_TEXTURE, player.capeTextureUrl());
            assertNotNull(player.account().externalCape());
            assertEquals("578631b3-4049-42bd-a205-3971f0965c5f", player.account().externalCape().id());
            assertEquals("optifine", player.account().externalCape().service());
            assertTrue(player.account().externalCape().hasElytra());
        }

        @Test
        @DisplayName("parses a player without a cape")
        void parses_player_without_cape() {
            httpClient.addResponse(playerUri("Jeb_"), 200,
                    "{\"isUser\":false,\"player\":{\"uuid\":\"853c80ef-3c37-49fd-aa49-938b674adae6\","
                            + "\"username\":\"Jeb_\",\"type\":\"player\"}}");

            CosmeticaApi.CosmeticaPlayer player = api.getPlayer("Jeb_");

            assertNotNull(player);
            assertFalse(player.hasCape());
            assertNull(player.capeTextureUrl());
        }

        @Test
        @DisplayName("parses a player with an internal Cosmetica cape")
        void parses_player_with_internal_cape() {
            httpClient.addResponse(playerUri("InternalCapeLad"), 200,
                    "{\"isUser\":false,\"player\":{\"username\":\"InternalCapeLad\",\"type\":\"player\","
                            + "\"internalCape\":{\"id\":\"cap-1\",\"texture\":\"https://assets.namet.ag/internal.png\","
                            + "\"hasElytra\":false}}}");

            CosmeticaApi.CosmeticaPlayer player = api.getPlayer("InternalCapeLad");

            assertNotNull(player);
            assertTrue(player.hasCape());
            assertEquals("https://assets.namet.ag/internal.png", player.capeTextureUrl());
        }
    }

    @Nested
    @DisplayName("Cosmetica user account variant (isUser=true)")
    class UserVariant {

        @Test
        @DisplayName("parses a Cosmetica user with an external cape")
        void parses_user_with_external_cape() {
            httpClient.addResponse(playerUri("CapeLad"), 200,
                    "{\"isUser\":true,\"user\":{\"uuid\":\"12345678-1234-1234-1234-123456789abc\","
                            + "\"username\":\"CapeLad\","
                            + "\"externalCape\":{\"id\":\"usercape-1\",\"service\":\"official\","
                            + "\"texture\":\"" + CAPE_TEXTURE + "\",\"hasElytra\":false}}}");

            CosmeticaApi.CosmeticaPlayer player = api.getPlayer("CapeLad");

            assertNotNull(player);
            assertTrue(player.isUser());
            assertTrue(player.hasCape());
            assertEquals(CAPE_TEXTURE, player.capeTextureUrl());
            assertEquals("CapeLad", player.account().username());
        }
    }

    @Nested
    @DisplayName("Fail-closed contract")
    class FailClosed {

        @Test
        @DisplayName("returns null on 404")
        void returns_null_on_404() {
            httpClient.addResponse(playerUri("Ghost"), 404, "");

            assertNull(api.getPlayer("Ghost"));
        }

        @Test
        @DisplayName("returns null on 400 (unknown player)")
        void returns_null_on_400() {
            httpClient.addResponse(playerUri("Ghost"), 400, "{\"error\":\"No such player\"}");

            assertNull(api.getPlayer("Ghost"));
        }

        @Test
        @DisplayName("returns null on 5xx")
        void returns_null_on_5xx() {
            httpClient.addResponse(playerUri("Ghost"), 500, "internal error");

            assertNull(api.getPlayer("Ghost"));
        }

        @Test
        @DisplayName("returns null on malformed JSON")
        void returns_null_on_malformed_json() {
            httpClient.addResponse(playerUri("Ghost"), 200, "{not valid json");

            assertNull(api.getPlayer("Ghost"));
        }

        @Test
        @DisplayName("returns null on a JSON array root")
        void returns_null_on_array_root() {
            httpClient.addResponse(playerUri("Ghost"), 200, "[1,2,3]");

            assertNull(api.getPlayer("Ghost"));
        }

        @Test
        @DisplayName("returns null on transport error")
        void returns_null_on_transport_error() {
            httpClient.addTimeout(playerUri("Ghost"));

            assertNull(api.getPlayer("Ghost"));
        }
    }
}
