/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonObject;
import levosilimo.everlastingskins.FakeHttpClient;
import levosilimo.everlastingskins.permission.TestConfigSupport;
import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.skinchanger.responses.profile.PropertyResponse;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.HttpClient;
import levosilimo.everlastingskins.util.JsonUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Provider fallback order (Mojang → MineTools) and HTTP outcome handling for
 * every status code and transport failure. The Eclipse stub paths were
 * removed once their endpoints were aliased to Mojang, so no Eclipse request
 * may be issued.
 * <p>
 * All HTTP responses are served by {@link FakeHttpClient}. No live endpoint
 * is ever contacted.
 */
class MojangApiHttpImplTest {

    private static final String PLAYER_NAME = "TestPlayer";
    private static final UUID PLAYER_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final String NO_DASH_UUID = "12345678123412341234123456789abc";

    private static final MojangEndpoints TEST_ENDPOINTS = new MojangEndpoints(
            "http://test.local/uuid/mojang/%playerName%",
            "http://test.local/uuid/minetools/%playerName%",
            "http://test.local/profile/mojang/%uuid%",
            "http://test.local/profile/minetools/%uuid%"
    );

    private URI mojangUuidUri;
    private URI mineToolsUuidUri;
    private URI mojangProfileUri;
    private URI mineToolsProfileUri;

    private FakeHttpClient httpClient;
    private MojangApiHttpImpl api;

    @BeforeAll
    static void loadConfig() {
        TestConfigSupport.loadDefaults();
    }

    @BeforeEach
    void setUp() {
        mojangUuidUri = URI.create("http://test.local/uuid/mojang/" + PLAYER_NAME);
        mineToolsUuidUri = URI.create("http://test.local/uuid/minetools/" + PLAYER_NAME);
        mojangProfileUri = URI.create("http://test.local/profile/mojang/" + NO_DASH_UUID);
        mineToolsProfileUri = URI.create("http://test.local/profile/minetools/" + NO_DASH_UUID);

        httpClient = new FakeHttpClient();
        api = new MojangApiHttpImpl(TEST_ENDPOINTS, httpClient);
    }
    @Nested
    @DisplayName("UUID fallback order")
    class UuidFallback {

        @Test
        @DisplayName("Mojang 200 OK → returns UUID")
        void mojangFirst() {
            httpClient.addResponse(mojangUuidUri, 200, uuidMojangBody(PLAYER_NAME, PLAYER_UUID));

            Optional<UUID> result = api.getUUID(PLAYER_NAME);

            assertTrue(result.isPresent());
            assertEquals(PLAYER_UUID, result.get());
        }

        @Test
        @DisplayName("Mojang 404 → MineTools 200 OK → returns UUID")
        void mojangFailsMineToolsSucceeds() {
            httpClient.addResponse(mojangUuidUri, 404, "");
            httpClient.addResponse(mineToolsUuidUri, 200, uuidMineToolsBody(PLAYER_NAME, PLAYER_UUID));

            Optional<UUID> result = api.getUUID(PLAYER_NAME);

            assertTrue(result.isPresent());
            assertEquals(PLAYER_UUID, result.get());
        }

        @Test
        @DisplayName("All providers fail → empty")
        void allProvidersFail() {
            httpClient.addResponse(mojangUuidUri, 404, "");
            httpClient.addResponse(mineToolsUuidUri, 200, uuidMineToolsErrBody());

            Optional<UUID> result = api.getUUID(PLAYER_NAME);
            assertTrue(result.isEmpty());
        }
    }
    @Nested
    @DisplayName("Profile fallback order")
    class ProfileFallback {

        private ProfileLookup lookup;

        @BeforeEach
        void init() {
            lookup = new ProfileLookup(PLAYER_NAME, PLAYER_UUID);
        }

        @Test
        @DisplayName("Mojang 200 OK → returns property")
        void mojangFirst() {
            httpClient.addResponse(mojangProfileUri, 200, profileMojangBody("value1", "sig1"));

            Optional<CustomSkinProperty> result = api.getProfile(lookup);

            assertTrue(result.isPresent());
            assertEquals("value1", result.get().getOriginalProperty().value());
        }

        @Test
        @DisplayName("Mojang 404 → MineTools 200 OK → returns property")
        void mojangFailsMineToolsSucceeds() {
            httpClient.addResponse(mojangProfileUri, 404, "");
            httpClient.addResponse(mineToolsProfileUri, 200, profileMineToolsBody("value2", "sig2"));

            Optional<CustomSkinProperty> result = api.getProfile(lookup);

            assertTrue(result.isPresent());
            assertEquals("value2", result.get().getOriginalProperty().value());
        }

        @Test
        @DisplayName("All profile providers fail → empty")
        void allProvidersFail() {
            httpClient.addResponse(mojangProfileUri, 404, "");

            Optional<CustomSkinProperty> result = api.getProfile(lookup);
            assertTrue(result.isEmpty());
        }
    }
    @Nested
    @DisplayName("HTTP outcomes per UUID provider")
    class UuidHttpOutcomes {

        @Test
        @DisplayName("Mojang 204 → empty")
        void mojang204() {
            httpClient.addResponse(mojangUuidUri, 204, "");

            Optional<UUID> result = api.getUUID(PLAYER_NAME);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Mojang 429 → empty (falls through to MineTools)")
        void mojang429() {
            httpClient.addResponse(mojangUuidUri, 429, "");
            httpClient.addResponse(mineToolsUuidUri, 200, uuidMineToolsBody(PLAYER_NAME, PLAYER_UUID));

            Optional<UUID> result = api.getUUID(PLAYER_NAME);

            assertTrue(result.isPresent());
            assertEquals(PLAYER_UUID, result.get());
        }

        @Test
        @DisplayName("Mojang 500 → empty (falls through)")
        void mojang500() {
            httpClient.addResponse(mojangUuidUri, 500, "{}");
            httpClient.addResponse(mineToolsUuidUri, 200, uuidMineToolsBody(PLAYER_NAME, PLAYER_UUID));

            Optional<UUID> result = api.getUUID(PLAYER_NAME);
            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("Mojang malformed body → empty (falls through)")
        void mojangMalformedBody() {
            httpClient.addResponse(mojangUuidUri, 200, "not-json-at-all");
            httpClient.addResponse(mineToolsUuidUri, 200, uuidMineToolsBody(PLAYER_NAME, PLAYER_UUID));

            Optional<UUID> result = api.getUUID(PLAYER_NAME);
            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("Transport timeout → empty (falls through)")
        void transportTimeout() {
            httpClient.addTimeout(mojangUuidUri);
            httpClient.addResponse(mineToolsUuidUri, 200, uuidMineToolsBody(PLAYER_NAME, PLAYER_UUID));

            Optional<UUID> result = api.getUUID(PLAYER_NAME);

            assertTrue(result.isPresent());
            assertEquals(PLAYER_UUID, result.get());
        }

        @Test
        @DisplayName("All providers timeout → empty")
        void allTimeouts() {
            httpClient.addTimeout(mojangUuidUri);
            httpClient.addTimeout(mineToolsUuidUri);

            Optional<UUID> result = api.getUUID(PLAYER_NAME);
            assertTrue(result.isEmpty());
        }
    }
    @Nested
    @DisplayName("HTTP outcomes per profile provider")
    class ProfileHttpOutcomes {

        private ProfileLookup lookup;

        @BeforeEach
        void init() {
            lookup = new ProfileLookup(PLAYER_NAME, PLAYER_UUID);
        }

        @Test
        @DisplayName("Mojang 204 → empty (falls through)")
        void mojang204() {
            httpClient.addResponse(mojangProfileUri, 204, "");

            Optional<CustomSkinProperty> result = api.getProfile(lookup);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Mojang null properties → empty")
        void mojangNullProperties() {
            httpClient.addResponse(mojangProfileUri, 200, "{\"id\":\"abc\",\"name\":\"x\"}");

            Optional<CustomSkinProperty> result = api.getProfile(lookup);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Mojang empty value → empty")
        void mojangEmptyValue() {
            httpClient.addResponse(mojangProfileUri, 200,
                    "{\"id\":\"x\",\"name\":\"x\",\"properties\":[{\"name\":\"textures\",\"value\":\"\",\"signature\":\"\"}]}");

            Optional<CustomSkinProperty> result = api.getProfile(lookup);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("MineTools ERR status → empty")
        void mineToolsErrStatus() {
            httpClient.addResponse(mojangProfileUri, 404, "");
            httpClient.addResponse(mineToolsProfileUri, 200, "{\"raw\":{\"id\":\"x\",\"name\":\"x\",\"status\":\"ERR\",\"properties\":[]}}");

            Optional<CustomSkinProperty> result = api.getProfile(lookup);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Mojang 429 → empty (falls through)")
        void mojang429() {
            httpClient.addResponse(mojangProfileUri, 429, "");

            Optional<CustomSkinProperty> result = api.getProfile(lookup);
            // MojangProfileResponse parsing of empty body returns null properties → empty
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Profile transport timeout → empty (falls through)")
        void transportTimeout() {
            httpClient.addTimeout(mojangProfileUri);
            httpClient.addResponse(mineToolsProfileUri, 200, profileMineToolsBody("v", "sig"));

            Optional<CustomSkinProperty> result = api.getProfile(lookup);
            assertTrue(result.isPresent());
        }
    }
    @Nested
    @DisplayName("getSkin integration")
    class GetSkin {

        @Test
        @DisplayName("Valid username and profile → returns MojangSkinDataResult")
        void fullSuccess() {
            httpClient.addResponse(mojangUuidUri, 200, uuidMojangBody(PLAYER_NAME, PLAYER_UUID));
            httpClient.addResponse(mojangProfileUri, 200, profileMojangBody("val", "sig"));

            Optional<MojangSkinDataResult> result = api.getSkin(PLAYER_NAME);

            assertTrue(result.isPresent());
            assertEquals(PLAYER_UUID, result.get().uniqueId());
        }

        @Test
        @DisplayName("Invalid username → empty")
        void invalidUsername() {
            Optional<MojangSkinDataResult> result = api.getSkin("$$$invalid");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("UUID input (dashed) bypasses name lookup → looks up profile directly")
        void uuidInput() {
            httpClient.addResponse(mojangProfileUri, 200, profileMojangBody("v", "s"));

            Optional<MojangSkinDataResult> result = api.getSkin(PLAYER_UUID.toString());

            assertTrue(result.isPresent());
        }
    }

    @Nested
    @DisplayName("Mojang schema fixtures")
    class MojangSchemaFixtures {

        private ProfileLookup lookup;

        @BeforeEach
        void init() {
            lookup = new ProfileLookup(PLAYER_NAME, PLAYER_UUID);
        }

        @Test
        @DisplayName("404 unknown name → empty (no exception)")
        void uuid404UnknownName() {
            httpClient.addResponse(mojangUuidUri, 404, fixture("uuid-404-unknown-name.json"));

            Optional<UUID> result = assertDoesNotThrow(() -> api.getUUID(PLAYER_NAME));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("204 empty profile → empty (no exception)")
        void profile204Empty() {
            httpClient.addResponse(mojangProfileUri, 204, fixture("profile-204-empty.json"));

            Optional<CustomSkinProperty> result = assertDoesNotThrow(() -> api.getProfile(lookup));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("400 invalid UUID → empty (no exception)")
        void profile400InvalidUuid() {
            httpClient.addResponse(mojangProfileUri, 400, fixture("profile-400-invalid-uuid.json"));

            Optional<CustomSkinProperty> result = assertDoesNotThrow(() -> api.getProfile(lookup));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("200 textures only → value and signature preserved (unsigned=false)")
        void profile200TexturesOnly() {
            httpClient.addResponse(mojangProfileUri, 200, fixture("profile-200-textures-only.json"));

            Optional<CustomSkinProperty> result = api.getProfile(lookup);

            assertTrue(result.isPresent());
            PropertyResponse property = propertyOf(result.get());
            assertEquals("eyJ0aW1lc3RhbXAiOjE3MTIzNDU2Nzg5MDEsInByb2ZpbGVJZCI6IjEyMzQ1Njc4MTIzNDEyMzQxMjM0MTIzNDU2Nzg5YWJjIiwicHJvZmlsZU5hbWUiOiJUZXN0UGxheWVyIiwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2FiYzEyM2RlZjQ1NiJ9fX0=", property.value());
            assertFalse(property.signature().isEmpty(), "signature must be present when unsigned=false");
        }

        @Test
        @DisplayName("200 skin and cape → value decodes with CAPE present")
        void profile200SkinAndCape() {
            httpClient.addResponse(mojangProfileUri, 200, fixture("profile-200-skin-and-cape.json"));

            Optional<CustomSkinProperty> result = api.getProfile(lookup);

            assertTrue(result.isPresent());
            JsonObject decoded = decodeTextures(propertyOf(result.get()).value());
            assertTrue(decoded.getAsJsonObject("textures").has("CAPE"));
        }

        @Test
        @DisplayName("200 metadata slim → value decodes with model slim")
        void profile200MetadataSlim() {
            httpClient.addResponse(mojangProfileUri, 200, fixture("profile-200-metadata-slim.json"));

            Optional<CustomSkinProperty> result = api.getProfile(lookup);

            assertTrue(result.isPresent());
            JsonObject decoded = decodeTextures(propertyOf(result.get()).value());
            assertEquals("slim", decoded.getAsJsonObject("textures")
                    .getAsJsonObject("SKIN").getAsJsonObject("metadata").get("model").getAsString());
        }

        @Test
        @DisplayName("200 missing signature → value kept, signature empty")
        void profile200MissingSignature() {
            httpClient.addResponse(mojangProfileUri, 200, fixture("profile-200-missing-signature.json"));

            Optional<CustomSkinProperty> result = api.getProfile(lookup);

            assertTrue(result.isPresent());
            PropertyResponse property = propertyOf(result.get());
            assertFalse(property.value().isEmpty());
            assertTrue(property.signature().isEmpty());
        }

        @Test
        @DisplayName("200 legacy/demo flags → parsed, value preserved")
        void profile200LegacyDemo() {
            httpClient.addResponse(mojangProfileUri, 200, fixture("profile-200-legacy-demo.json"));

            Optional<CustomSkinProperty> result = api.getProfile(lookup);

            assertTrue(result.isPresent());
            assertEquals("eyJ0aW1lc3RhbXAiOjE3MTIzNDU2Nzg5MDEsInByb2ZpbGVJZCI6IjEyMzQ1Njc4MTIzNDEyMzQxMjM0MTIzNDU2Nzg5YWJjIiwicHJvZmlsZU5hbWUiOiJUZXN0UGxheWVyIiwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2FiYzEyM2RlZjQ1NiJ9fX0=", propertyOf(result.get()).value());
        }

        @Test
        @DisplayName("real Notch profile → value and signature populated")
        void profile200Notch() {
            httpClient.addResponse(mojangProfileUri, 200, fixture("profile-200-notch.json"));

            Optional<CustomSkinProperty> result = api.getProfile(lookup);

            assertTrue(result.isPresent());
            PropertyResponse property = propertyOf(result.get());
            assertEquals("eyJ0aW1lc3RhbXAiOjE3MTIzNDU2Nzg5MDEsInByb2ZpbGVJZCI6IjA2OWE3OWY0NDRlOTQ3MjZhNWJlZmNhOTBlMzhhYWY1IiwicHJvZmlsZU5hbWUiOiJOb3RjaCIsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8xYTJiM2M0ZDVlNmY3YThiIn19fQ==", property.value());
            assertFalse(property.signature().isEmpty());
        }
    }

    /* ================================================================== */
    /*  JSON body helpers                                                  */
    /* ================================================================== */

    private static String fixture(String name) {
        try (java.io.InputStream in = MojangApiHttpImplTest.class.getResourceAsStream("/fixtures/mojang/" + name)) {
            if (in == null) {
                throw new AssertionError("Missing fixture: " + name);
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError("Failed to read fixture: " + name, e);
        }
    }

    private static PropertyResponse propertyOf(CustomSkinProperty skin) {
        return new PropertyResponse("textures",
                skin.getOriginalProperty().value(),
                skin.getOriginalProperty().signature());
    }

    private static JsonObject decodeTextures(String base64Value) {
        byte[] decoded = java.util.Base64.getDecoder().decode(base64Value);
        return JsonUtils.parseJson(new String(decoded, java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String uuidMojangBody(String name, UUID uuid) {
        return "{\"name\":\"" + name + "\",\"id\":\"" + uuid.toString().replace("-", "") + "\"}";
    }

    private static String uuidMineToolsBody(String name, UUID uuid) {
        return "{\"id\":\"" + uuid.toString().replace("-", "") + "\",\"name\":\"" + name + "\",\"status\":\"OK\"}";
    }

    private static String uuidMineToolsErrBody() {
        return "{\"id\":null,\"name\":null,\"status\":\"ERR\"}";
    }

    private static String profileMojangBody(String value, String signature) {
        return "{\"id\":\"x\",\"name\":\"x\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + value + "\",\"signature\":\"" + signature + "\"}]}";
    }

    private static String profileMineToolsBody(String value, String signature) {
        return "{\"raw\":{\"id\":\"x\",\"name\":\"x\",\"status\":\"OK\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + value + "\",\"signature\":\"" + signature + "\"}]}}";
    }

    /**
     * M2 step 5: the Config-gated cache disable moved out of MojangProfileCache
     * (decoupled in /common) into the impl's {@code profileCacheEnabled} ctor
     * flag — the cache is never consulted or populated when it is false.
     */
    @Test
    @DisplayName("profileCacheEnabled=false re-runs the full lookup chain on every call")
    void disabledCache_fullLookupOnEveryCall() throws Exception {
        CountingHttpClient counting = new CountingHttpClient();
        counting.delegate.addResponse(mojangUuidUri, 200,
                "{\"name\":\"" + PLAYER_NAME + "\",\"id\":\"" + NO_DASH_UUID + "\"}");
        counting.delegate.addResponse(mojangProfileUri, 200, profileMojangBody("val", "sig"));

        MojangApiHttpImpl disabledApi = new MojangApiHttpImpl(TEST_ENDPOINTS, counting, false);
        assertTrue(disabledApi.getSkin(PLAYER_NAME).isPresent(), "first lookup must succeed");
        int afterFirst = counting.total;
        assertTrue(disabledApi.getSkin(PLAYER_NAME).isPresent(), "repeat lookup must succeed");
        assertEquals(afterFirst, counting.total - afterFirst,
                "a disabled cache must not skip the profile chain on repeat lookups");
    }

    /** Delegating client that counts every execute() call. */
    private static final class CountingHttpClient implements HttpClient {
        private final FakeHttpClient delegate = new FakeHttpClient();
        private int total = 0;

        @Override
        public HttpResponse execute(URI uri, RequestBody requestBody, HttpType accepts,
                                    String userAgent, HttpMethod method,
                                    Map<String, String> headers, int timeout) throws IOException {
            total++;
            return delegate.execute(uri, requestBody, accepts, userAgent, method, headers, timeout);
        }
    }
}
