/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.FakeHttpClient;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.skinchanger.FakeMojangAPI;
import levosilimo.everlastingskins.integration.TestProperties;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.EndpointsConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A5 stored-source skip: the stored skin carries a provider-class
 * discriminator (SOURCE_MOJANG / SOURCE_MINESKIN) plus the username it was
 * fetched for, so the skip fires only when BOTH match the incoming request.
 * The real provider implementations must store the production discriminator
 * values — if those literals drift, no stored skin ever matches and the skip
 * silently dies in production. A username-shaped stored source (the old
 * test-only contract) must NOT trigger the skip, neither must a stored
 * MineSkin-class source, an absent stored skin, or a Mojang-class skin that
 * was fetched for a different username (which falls through to a fresh
 * fetch).
 */
class SkinActionStoredSourceSkipTest {

    /** Production discriminator literal pinned by {@link #discriminatorValues_arePinned}. */
    private static final String PRODUCTION_MOJANG_SOURCE = "MojangAPI";
    private static final String PRODUCTION_MINESKIN_SOURCE = "MineSkin";

    /** Distinct texture payload so the fresh-fetch test can tell skins apart. */
    private static final String JEB_VALUE = Base64.getEncoder().encodeToString(
        ("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/jeb\"}}}")
            .getBytes(StandardCharsets.UTF_8));

    private static final UUID PLAYER_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final String NO_DASH_UUID = "12345678123412341234123456789abc";

    @TempDir
    Path tempDir;

    private TestServerContext ctx;
    private FakeMojangAPI fake;

    @BeforeEach
    void setUp() {
        ctx = new TestServerContext(tempDir);
        fake = new FakeMojangAPI();
        SkinCommandTestAccess.setMojangAPI(fake);
        SkinMetrics.INSTANCE.reset();
        SkinActionTestAccess.clearGuardState();
    }

    @AfterEach
    void tearDown() {
        SkinActionTestAccess.clearGuardState();
        SkinActionTestAccess.clearI18n();
        SkinCommandTestAccess.resetAPIs();
        ctx.close();
    }

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
        assertEquals("Notch", result.get().getUsername(),
            "the username used for the lookup must be persisted on the skin");
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
        assertNull(result.property().getUsername(),
            "MineSkin skins have no Mojang username to persist");
    }

    @Test
    @DisplayName("MineSkin-class stored source does not trigger the Mojang skip")
    void mineSkinClassSource_doesNotTriggerSkip() {
        CustomSkinProperty mineSkinShaped = new CustomSkinProperty("textures",
            TestProperties.NOTCH.getOriginalProperty().getValue(),
            TestProperties.NOTCH.getOriginalProperty().getSignature(), SkinAction.SOURCE_MINESKIN);
        fake.addSkin("Notch", mineSkinShaped);
        EntityPlayerMP alice = ctx.newPlayer("Alice");

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> SkinAction.SOURCE_MINESKIN.equals(sourceOf(alice))),
            "first dispatch must store the MineSkin-class skin");

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> fake.lookupCount("Notch") == 2),
            "MineSkin-class stored source must not skip a Mojang fetch");
    }

    @Test
    @DisplayName("username-shaped stored source does not trigger the skip (old test-only contract)")
    void usernameShapedSource_doesNotTriggerSkip() {
        CustomSkinProperty usernameShaped = new CustomSkinProperty("textures",
            TestProperties.NOTCH.getOriginalProperty().getValue(),
            TestProperties.NOTCH.getOriginalProperty().getSignature(), "Notch");
        fake.addSkin("Notch", usernameShaped);
        EntityPlayerMP alice = ctx.newPlayer("Alice");

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> "Notch".equals(sourceOf(alice))),
            "first dispatch must store the username-shaped skin");

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> fake.lookupCount("Notch") == 2),
            "a username stored as source must not skip the fetch");
    }

    @Test
    @DisplayName("Mojang-class skin stored for a different username does not skip — fresh fetch stores the new username")
    void differentUsernameMojangSkin_fetchesFresh() {
        CustomSkinProperty mojangShapedNotch = new CustomSkinProperty("textures",
            TestProperties.NOTCH.getOriginalProperty().getValue(),
            TestProperties.NOTCH.getOriginalProperty().getSignature(), SkinAction.SOURCE_MOJANG, "Notch");
        CustomSkinProperty jebSkin = new CustomSkinProperty("textures", JEB_VALUE,
            TestProperties.NOTCH.getOriginalProperty().getSignature(), SkinAction.SOURCE_MOJANG, "Jeb_");
        fake.addSkin("Notch", mojangShapedNotch).addSkin("Jeb_", jebSkin);
        EntityPlayerMP alice = ctx.newPlayer("Alice");

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> "Notch".equals(usernameOf(alice))),
            "first dispatch must store the skin with its username");
        assertEquals(1, fake.lookupCount("Notch"));

        ctx.commandManager.executeCommand(alice, "/skin set mojang Jeb_");
        assertTrue(AsyncSupport.await(5000, () -> fake.lookupCount("Jeb_") == 1),
            "different-username redispatch must consult the provider again");
        assertEquals(0, SkinMetrics.INSTANCE.snapshot().refreshesSkippedStored(),
            "different-username redispatch must not take the skip");
        assertTrue(AsyncSupport.await(5000, () -> "Jeb_".equals(usernameOf(alice))),
            "the fresh fetch must store the new username");
        assertTrue(AsyncSupport.await(5000,
                () -> SkinMetrics.INSTANCE.snapshot().refreshesCompleted() >= 2),
            "different-username redispatch must run the refresh pipeline");
    }

    @Test
    @DisplayName("no stored skin never triggers the skip")
    void noStoredSkin_doesNotTriggerSkip() {
        fake.addSkin("Notch", TestProperties.NOTCH);
        EntityPlayerMP alice = ctx.newPlayer("Alice");

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> fake.lookupCount("Notch") == 1),
            "an absent stored skin must not suppress the fetch");
        assertTrue(AsyncSupport.await(5000, () -> sourceOf(alice) != null),
            "dispatch must store the fetched skin");
    }

    private String sourceOf(EntityPlayerMP player) {
        CustomSkinProperty skin = ctx.storage.getSkin(player.getUniqueID());
        return skin != null ? skin.getSource() : null;
    }

    private String usernameOf(EntityPlayerMP player) {
        CustomSkinProperty skin = ctx.storage.getSkin(player.getUniqueID());
        return skin != null ? skin.getUsername() : null;
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
