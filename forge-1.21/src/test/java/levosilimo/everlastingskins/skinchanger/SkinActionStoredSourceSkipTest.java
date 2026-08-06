/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.forge21.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.permission.TestConfigSupport;
import levosilimo.everlastingskins.skinchanger.command.SkinActionCommand;
import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.EndpointsConfig;
import levosilimo.everlastingskins.util.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A5 stored-source skip: the stored skin carries a provider-class
 * discriminator (SOURCE_MOJANG / SOURCE_MINESKIN) plus the username it was
 * fetched for, so the skip fires only when BOTH match the incoming request.
 * A Mojang-class skin stored for a different username must NOT skip — the
 * fresh fetch is the production contract, and the gametest covers the
 * skippedStored/completed metrics deltas end-to-end.
 */
class SkinActionStoredSourceSkipTest {

    /** Production discriminator literal pinned by {@link #discriminatorValues_arePinned}. */
    private static final String PRODUCTION_MOJANG_SOURCE = "MojangAPI";
    private static final String PRODUCTION_MINESKIN_SOURCE = "MineSkin";

    private static final UUID PLAYER_UUID = UUID.randomUUID();
    private static final String NO_DASH_UUID = "12345678123412341234123456789abc";

    private static final MojangEndpoints TEST_ENDPOINTS = new MojangEndpoints(
            "http://test.local/uuid/mojang/%playerName%",
            "http://test.local/uuid/minetools/%playerName%",
            "http://test.local/profile/mojang/%uuid%",
            "http://test.local/profile/minetools/%uuid%"
    );

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
        return skin(source, null);
    }

    private static CustomSkinProperty skin(String source, String username) {
        return new CustomSkinProperty("textures", "textureValue", "signature", source, username);
    }

    @Test
    @DisplayName("Mojang-class skin stored for the same username triggers the skip")
    void sameUsernameMojangSkin_triggersSkip() {
        storage.setSkin(PLAYER_UUID, skin(PRODUCTION_MOJANG_SOURCE, "Notch"));

        assertTrue(SkinActionCommand.storedSourceMatches(PLAYER_UUID, "Notch"),
                "a Mojang-class skin stored for Notch must skip a Notch request");
    }

    @Test
    @DisplayName("Mojang-class skin stored for a different username does NOT trigger the skip")
    void differentUsernameMojangSkin_doesNotTriggerSkip() {
        storage.setSkin(PLAYER_UUID, skin(PRODUCTION_MOJANG_SOURCE, "Notch"));

        assertFalse(SkinActionCommand.storedSourceMatches(PLAYER_UUID, "Jeb_"),
                "a Mojang-class skin stored for Notch must not skip a Jeb_ request");
    }

    @Test
    @DisplayName("Mojang-class skin without a stored username never triggers the skip")
    void mojangSkinWithoutUsername_doesNotTriggerSkip() {
        storage.setSkin(PLAYER_UUID, skin(PRODUCTION_MOJANG_SOURCE));

        assertFalse(SkinActionCommand.storedSourceMatches(PLAYER_UUID, "Notch"),
                "a legacy Mojang-class skin with no username cannot prove it matches the request");
    }

    @Test
    @DisplayName("MineSkin discriminator does not trigger the Mojang skip")
    void mineSkinDiscriminator_doesNotTriggerMojangSkip() {
        storage.setSkin(PLAYER_UUID, skin(PRODUCTION_MINESKIN_SOURCE, "Notch"));

        assertFalse(SkinActionCommand.storedSourceMatches(PLAYER_UUID, "Notch"),
                "a MineSkin-class stored skin must not skip a Mojang request");
    }

    @Test
    @DisplayName("username-shaped stored source does not trigger the skip (old test-only contract)")
    void usernameShapedSource_doesNotTriggerSkip() {
        storage.setSkin(PLAYER_UUID, skin("Notch", "Notch"));

        assertFalse(SkinActionCommand.storedSourceMatches(PLAYER_UUID, "Notch"),
                "a stored source that is a username must not skip the fetch");
    }

    @Test
    @DisplayName("no stored skin never triggers the skip")
    void noStoredSkin_doesNotTriggerSkip() {
        // Fresh UUID: skinMap is static, so a shared constant could see a skin
        // stored by an earlier test in this class.
        assertFalse(SkinActionCommand.storedSourceMatches(UUID.randomUUID(), "Notch"));
    }

    @Test
    @DisplayName("discriminator values are pinned to what the providers store")
    void discriminatorValues_arePinned() {
        assertEquals(PRODUCTION_MOJANG_SOURCE, SkinActionCommand.SOURCE_MOJANG);
        assertEquals(PRODUCTION_MINESKIN_SOURCE, SkinActionCommand.SOURCE_MINESKIN);
    }

    @Test
    @DisplayName("MojangApiHttpImpl results carry the production discriminator and the requested username")
    void mojangApiResults_carryProductionDiscriminatorAndUsername() {
        URI mojangProfileUri = URI.create("http://test.local/profile/mojang/" + NO_DASH_UUID);
        CountingHttpClient client = new CountingHttpClient();
        client.register(mojangProfileUri, 200,
                "{\"id\":\"x\",\"name\":\"x\",\"properties\":[{\"name\":\"textures\",\"value\":\"val\",\"signature\":\"sig\"}]}");

        Optional<CustomSkinProperty> result = new MojangApiHttpImpl(TEST_ENDPOINTS, client)
                .getProfile(new ProfileLookup("Notch", UUID.fromString("12345678-1234-1234-1234-123456789abc")));

        assertTrue(result.isPresent());
        assertEquals(SkinActionCommand.SOURCE_MOJANG, result.get().getSource());
        assertEquals("Notch", result.get().getUsername(),
                "the username used for the lookup must be persisted on the skin");
    }

    @Test
    @DisplayName("identical-username redispatch on a production-shaped skin skips and issues no provider call")
    void identicalUsernameRedispatch_skipsWithoutProviderCall() {
        CountingHttpClient client = new CountingHttpClient();
        MojangApiHttpImpl api = new MojangApiHttpImpl(TEST_ENDPOINTS, client);
        registerMojangLookup(client, "Notch", NO_DASH_UUID, "val-notch");

        // First fetch through the REAL provider stores the production shape:
        // source=MojangAPI plus the username the provider was asked for.
        Optional<MojangSkinDataResult> fetched = api.getSkin("Notch");
        assertTrue(fetched.isPresent(), "provider must resolve Notch");
        storage.setSkin(PLAYER_UUID, fetched.get().skinProperty());
        long callsAfterFirstFetch = client.totalRequests();

        assertTrue(SkinActionCommand.storedSourceMatches(PLAYER_UUID, "Notch"),
                "the stored production-shaped skin must skip an identical redispatch");
        assertEquals(callsAfterFirstFetch, client.totalRequests(),
                "taking the skip decision must never consult the provider");
    }

    @Test
    @DisplayName("different-username redispatch on a production-shaped skin fetches fresh from the provider")
    void differentUsernameRedispatch_consultsProviderAndStoresNewUsername() {
        CountingHttpClient client = new CountingHttpClient();
        MojangApiHttpImpl api = new MojangApiHttpImpl(TEST_ENDPOINTS, client);
        registerMojangLookup(client, "Notch", NO_DASH_UUID, "val-notch");
        registerMojangLookup(client, "Jeb_", "22345678123412341234123456789abc", "val-jeb");

        Optional<MojangSkinDataResult> notch = api.getSkin("Notch");
        assertTrue(notch.isPresent(), "provider must resolve Notch");
        storage.setSkin(PLAYER_UUID, notch.get().skinProperty());
        long callsBeforeRedispatch = client.totalRequests();

        assertFalse(SkinActionCommand.storedSourceMatches(PLAYER_UUID, "Jeb_"),
                "a Mojang-class skin stored for Notch must not skip a Jeb_ request");

        Optional<MojangSkinDataResult> jeb = api.getSkin("Jeb_");
        assertTrue(jeb.isPresent(), "provider must resolve Jeb_");
        assertTrue(client.totalRequests() > callsBeforeRedispatch,
                "the no-skip path must perform a fresh provider fetch");
        assertEquals("Jeb_", jeb.get().skinProperty().getUsername(),
                "the fresh fetch must persist the newly requested username");
    }

    @Test
    @DisplayName("MineSkinApiHttpImpl results carry the production MineSkin discriminator")
    void mineSkinApiResults_carryProductionDiscriminator() {
        URI mineskinUri = EndpointsConfig.getURI("endpoint.mineskin.generate");
        CountingHttpClient client = new CountingHttpClient();
        client.register(mineskinUri, 200, validMineSkinJson());

        MineSkinResponse result = new MineSkinApiHttpImpl(client, "").genSkin("https://example.com/skin.png", SkinVariant.CLASSIC);

        assertNotNull(result);
        assertEquals(SkinActionCommand.SOURCE_MINESKIN, result.property().getSource());
        assertNull(result.property().getUsername(),
                "MineSkin skins have no Mojang username to persist");
    }

    /** Registers the Mojang uuid + profile endpoints for a username lookup. */
    private static void registerMojangLookup(CountingHttpClient client, String name, String noDashUuid, String textureValue) {
        client.register(URI.create("http://test.local/uuid/mojang/" + name), 200,
                "{\"name\":\"" + name + "\",\"id\":\"" + noDashUuid + "\"}");
        client.register(URI.create("http://test.local/profile/mojang/" + noDashUuid), 200,
                "{\"id\":\"x\",\"name\":\"x\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + textureValue + "\",\"signature\":\"sig\"}]}");
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

    /**
     * Counts every execute() per URI. Unregistered URIs answer 404 so the
     * fallback chain falls through instead of failing the lookup.
     */
    private static final class CountingHttpClient implements HttpClient {

        private final Map<URI, HttpResponse> responses = new HashMap<>();
        private final Map<URI, Long> counts = new ConcurrentHashMap<>();

        void register(URI uri, int statusCode, String body) {
            responses.put(uri, new HttpResponse(statusCode, body, Map.of()));
        }

        long totalRequests() {
            return counts.values().stream().mapToLong(Long::longValue).sum();
        }

        @Override
        public HttpResponse execute(URI uri, RequestBody requestBody, HttpType accepts,
                                    String userAgent, HttpMethod method,
                                    Map<String, String> headers, int timeout) throws IOException {
            counts.merge(uri, 1L, Long::sum);
            HttpResponse response = responses.get(uri);
            if (response == null) {
                return new HttpResponse(404, "", Map.of());
            }
            return response;
        }
    }
}
