/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.FakeHttpClient;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.permission.TestConfigSupport;
import levosilimo.everlastingskins.skinchanger.command.SkinActionCommand;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.EndpointsConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A5 stored-source skip: the stored source is a provider-class discriminator
 * (SOURCE_MOJANG / SOURCE_MINESKIN), so the skip decision is class-to-class.
 * A username-shaped stored source (the old test-only contract) must NOT
 * trigger the skip, and the real provider implementations must store the
 * production discriminator values.
 */
class SkinActionStoredSourceSkipTest {

    /** Production discriminator literal pinned by {@link #discriminatorValues_arePinned}. */
    private static final String PRODUCTION_MOJANG_SOURCE = "MojangAPI";
    private static final String PRODUCTION_MINESKIN_SOURCE = "MineSkin";

    private static final UUID PLAYER_UUID = UUID.randomUUID();
    private static final String NO_DASH_UUID = "12345678123412341234123456789abc";

    @TempDir
    Path tempDir;

    private SkinStorage storage;

    @BeforeAll
    static void loadConfig() {
        TestConfigSupport.loadDefaults();
    }

    @BeforeEach
    void setUp() throws Exception {
        Path skinDir = tempDir.resolve("EverlastingSkins");
        Files.createDirectories(skinDir);
        storage = new SkinStorage(new SkinIO(skinDir));
        setStaticField(SkinRestorer.class, "skinStorage", storage);
        SkinRestorer.server = null;
    }

    @AfterEach
    void tearDown() throws Exception {
        setStaticField(SkinRestorer.class, "skinStorage", null);
        SkinRestorer.server = null;
    }

    private static void setStaticField(Class<?> clazz, String name, Object value) throws Exception {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static CustomSkinProperty skin(String source) {
        return new CustomSkinProperty("textures", "textureValue", "signature", source);
    }

    @Test
    @DisplayName("production Mojang discriminator triggers the stored-source skip")
    void mojangDiscriminator_triggersSkip() {
        storage.setSkin(PLAYER_UUID, skin(PRODUCTION_MOJANG_SOURCE));

        assertTrue(SkinActionCommand.storedSourceMatches(PLAYER_UUID),
                "a Mojang-class stored skin must skip the Mojang fetch");
    }

    @Test
    @DisplayName("MineSkin discriminator does not trigger the Mojang skip")
    void mineSkinDiscriminator_doesNotTriggerMojangSkip() {
        storage.setSkin(PLAYER_UUID, skin(PRODUCTION_MINESKIN_SOURCE));

        assertFalse(SkinActionCommand.storedSourceMatches(PLAYER_UUID),
                "a MineSkin-class stored skin must not skip a Mojang request");
    }

    @Test
    @DisplayName("username-shaped stored source does not trigger the skip (old test-only contract)")
    void usernameShapedSource_doesNotTriggerSkip() {
        storage.setSkin(PLAYER_UUID, skin("Notch"));

        assertFalse(SkinActionCommand.storedSourceMatches(PLAYER_UUID),
                "a stored source that is a username must not skip the fetch");
    }

    @Test
    @DisplayName("no stored skin never triggers the skip")
    void noStoredSkin_doesNotTriggerSkip() {
        assertFalse(SkinActionCommand.storedSourceMatches(PLAYER_UUID));
    }

    @Test
    @DisplayName("discriminator values are pinned to what the providers store")
    void discriminatorValues_arePinned() {
        assertEquals(PRODUCTION_MOJANG_SOURCE, SkinActionCommand.SOURCE_MOJANG);
        assertEquals(PRODUCTION_MINESKIN_SOURCE, SkinActionCommand.SOURCE_MINESKIN);
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
                .getProfile(new ProfileLookup("Notch", UUID.fromString("12345678-1234-1234-1234-123456789abc")));

        assertTrue(result.isPresent());
        assertEquals(SkinActionCommand.SOURCE_MOJANG, result.get().getSource());
    }

    @Test
    @DisplayName("MineSkinApiHttpImpl results carry the production MineSkin discriminator")
    void mineSkinApiResults_carryProductionDiscriminator() {
        URI mineskinUri = EndpointsConfig.getURI("endpoint.mineskin.generate");
        FakeHttpClient client = new FakeHttpClient();
        client.addResponse(mineskinUri, 200, validMineSkinJson());

        MineSkinResponse result = new MineSkinApiHttpImpl(client, "").genSkin("https://example.com/skin.png", SkinVariant.CLASSIC);

        assertNotNull(result);
        assertEquals(SkinActionCommand.SOURCE_MINESKIN, result.property().getSource());
    }

    private static String validMineSkinJson() {
        return """
                {
                  "id": 12345,
                  "idStr": "12345",
                  "uuid": "550e8400-e29b-41d4-a716-446655440000",
                  "name": "Test",
                  "variant": "classic",
                  "data": {
                    "uuid": "550e8400-e29b-41d4-a716-446655440000",
                    "texture": {
                      "value": "dGV4dHVyZXMgeyBTS0lOIHsgdXJsOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS90ZXN0IiB9IH0=",
                      "signature": "signature==",
                      "url": "https://example.com/skin"
                    }
                  },
                  "delay": 0,
                  "nextRequest": 0
                }
                """;
    }
}
