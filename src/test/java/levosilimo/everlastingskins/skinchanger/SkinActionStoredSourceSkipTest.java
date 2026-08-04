/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.FakeHttpClient;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.EndpointsConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A5 stored-source skip: the stored source is a provider-class discriminator
 * (SOURCE_MOJANG / SOURCE_MINESKIN), so the skip decision is class-to-class.
 * The real provider implementations must store the production discriminator
 * values — if those literals drift, no stored skin ever matches and the skip
 * silently dies in production.
 */
class SkinActionStoredSourceSkipTest {

    /** Production discriminator literal pinned by {@link #discriminatorValues_arePinned}. */
    private static final String PRODUCTION_MOJANG_SOURCE = "MojangAPI";
    private static final String PRODUCTION_MINESKIN_SOURCE = "MineSkin";

    private static final UUID PLAYER_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final String NO_DASH_UUID = "12345678123412341234123456789abc";

    @Test
    @DisplayName("discriminator values are pinned to what the providers store")
    void discriminatorValues_arePinned() {
        assertEquals(PRODUCTION_MOJANG_SOURCE, SkinAction.SOURCE_MOJANG);
        assertEquals(PRODUCTION_MINESKIN_SOURCE, SkinAction.SOURCE_MINESKIN);
    }

    @Test
    @DisplayName("MojangApiHttpImpl results carry the production Mojang discriminator")
    void mojangApiResults_carryProductionDiscriminator() {
        MojangEndpoints endpoints = new MojangEndpoints(
                "http://test.local/uuid/mojang/%playerName%",
                "http://test.local/uuid/minetools/%playerName%",
                "http://test.local/profile/mojang/%uuid%",
                "http://test.local/profile/minetools/%uuid%"
        );
        URI mojangProfileUri = URI.create("http://test.local/profile/mojang/" + NO_DASH_UUID);
        FakeHttpClient client = new FakeHttpClient();
        client.addResponse(mojangProfileUri, 200,
                "{\"id\":\"x\",\"name\":\"x\",\"properties\":[{\"name\":\"textures\",\"value\":\"val\",\"signature\":\"sig\"}]}");

        Optional<CustomSkinProperty> result = new MojangApiHttpImpl(endpoints, client)
                .getProfile(new ProfileLookup("Notch", PLAYER_UUID));

        assertTrue(result.isPresent());
        assertEquals(PRODUCTION_MOJANG_SOURCE, result.get().getSource());
    }

    @Test
    @DisplayName("MineSkinApiHttpImpl results carry the production MineSkin discriminator")
    void mineSkinApiResults_carryProductionDiscriminator() {
        URI mineskinUri = EndpointsConfig.getURI("endpoint.mineskin.generate");
        FakeHttpClient client = new FakeHttpClient();
        client.addResponse(mineskinUri, 200, validMineSkinJson());

        MineSkinResponse result = new MineSkinApiHttpImpl(client, "")
                .genSkin("https://example.com/skin.png", SkinVariant.CLASSIC);

        assertNotNull(result);
        assertEquals(PRODUCTION_MINESKIN_SOURCE, result.property().getSource());
    }

    private static String validMineSkinJson() {
        return "{\n" +
            "  \"id\": 12345,\n" +
            "  \"idStr\": \"12345\",\n" +
            "  \"uuid\": \"550e8400-e29b-41d4-a716-446655440000\",\n" +
            "  \"name\": \"Test\",\n" +
            "  \"variant\": \"classic\",\n" +
            "  \"data\": {\n" +
            "    \"uuid\": \"550e8400-e29b-41d4-a716-446655440000\",\n" +
            "    \"texture\": {\n" +
            "      \"value\": \"dGV4dHVyZXMgeyBTS0lOIHsgdXJsOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS90ZXN0IiB9IH0=\",\n" +
            "      \"signature\": \"signature==\",\n" +
            "      \"url\": \"https://example.com/skin\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"timestamp\": 1234567890,\n" +
            "  \"duration\": 100,\n" +
            "  \"account\": 1,\n" +
            "  \"server\": \"server1\",\n" +
            "  \"private\": false,\n" +
            "  \"views\": 0,\n" +
            "  \"nextRequest\": 0,\n" +
            "  \"duplicate\": false\n" +
            "}";
    }
}
