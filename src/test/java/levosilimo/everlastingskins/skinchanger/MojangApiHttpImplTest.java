package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.FakeHttpClient;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Provider fallback order (Eclipse → Mojang → MineTools) and HTTP outcome
 * handling for every status code and transport failure mentioned in the
 * Phase 1 plan.
 * <p>
 * All HTTP responses are served by {@link FakeHttpClient}. No live endpoint
 * is ever contacted.
 */
class MojangApiHttpImplTest {

    private static final String PLAYER_NAME = "TestPlayer";
    private static final UUID PLAYER_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final String NO_DASH_UUID = "12345678123412341234123456789abc";

    private static final MojangEndpoints TEST_ENDPOINTS = new MojangEndpoints(
            "http://test.local/uuid/eclipse/%playerName%",
            "http://test.local/uuid/mojang/%playerName%",
            "http://test.local/uuid/minetools/%playerName%",
            "http://test.local/profile/eclipse/%uuid%",
            "http://test.local/profile/mojang/%uuid%",
            "http://test.local/profile/minetools/%uuid%"
    );

    private URI eclipseUuidUri;
    private URI mojangUuidUri;
    private URI mineToolsUuidUri;
    private URI eclipseProfileUri;
    private URI mojangProfileUri;
    private URI mineToolsProfileUri;

    private FakeHttpClient httpClient;
    private MojangApiHttpImpl api;

    @BeforeEach
    void setUp() {
        eclipseUuidUri = URI.create("http://test.local/uuid/eclipse/" + PLAYER_NAME);
        mojangUuidUri = URI.create("http://test.local/uuid/mojang/" + PLAYER_NAME);
        mineToolsUuidUri = URI.create("http://test.local/uuid/minetools/" + PLAYER_NAME);
        eclipseProfileUri = URI.create("http://test.local/profile/eclipse/" + PLAYER_UUID);
        mojangProfileUri = URI.create("http://test.local/profile/mojang/" + NO_DASH_UUID);
        mineToolsProfileUri = URI.create("http://test.local/profile/minetools/" + NO_DASH_UUID);

        httpClient = new FakeHttpClient();
        api = new MojangApiHttpImpl(TEST_ENDPOINTS, httpClient);
    }
    @Nested
    @DisplayName("UUID fallback order")
    class UuidFallback {

        @Test
        @DisplayName("Eclipse 200 OK → returns UUID")
        void eclipseFirst() {
            httpClient.addResponse(eclipseUuidUri, 200, uuidEclipseBody(PLAYER_UUID));

            Optional<UUID> result = api.getUUID(PLAYER_NAME);

            assertTrue(result.isPresent());
            assertEquals(PLAYER_UUID, result.get());
        }

        @Test
        @DisplayName("Eclipse 404 → Mojang 200 OK → returns UUID")
        void eclipseFailsMojangSucceeds() {
            httpClient.addResponse(eclipseUuidUri, 404, "");
            httpClient.addResponse(mojangUuidUri, 200, uuidMojangBody(PLAYER_NAME, PLAYER_UUID));

            Optional<UUID> result = api.getUUID(PLAYER_NAME);

            assertTrue(result.isPresent());
            assertEquals(PLAYER_UUID, result.get());
        }

        @Test
        @DisplayName("Eclipse 404 → Mojang 404 → MineTools 200 OK → returns UUID")
        void eclipseAndMojangFailMineToolsSucceeds() {
            httpClient.addResponse(eclipseUuidUri, 404, "");
            httpClient.addResponse(mojangUuidUri, 404, "");
            httpClient.addResponse(mineToolsUuidUri, 200, uuidMineToolsBody(PLAYER_NAME, PLAYER_UUID));

            Optional<UUID> result = api.getUUID(PLAYER_NAME);

            assertTrue(result.isPresent());
            assertEquals(PLAYER_UUID, result.get());
        }

        @Test
        @DisplayName("All providers fail → empty")
        void allProvidersFail() {
            httpClient.addResponse(eclipseUuidUri, 404, "");
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
        @DisplayName("Eclipse 200 OK → returns property")
        void eclipseFirst() {
            httpClient.addResponse(eclipseProfileUri, 200, profileEclipseBody("value1", "sig1"));

            Optional<CustomSkinProperty> result = api.getProfile(lookup);

            assertTrue(result.isPresent());
            assertEquals("value1", result.get().getOriginalProperty().value());
        }

        @Test
        @DisplayName("Eclipse 404 → Mojang 200 OK → returns property")
        void eclipseFailsMojangSucceeds() {
            httpClient.addResponse(eclipseProfileUri, 404, "");
            httpClient.addResponse(mojangProfileUri, 200, profileMojangBody("value2", "sig2"));

            Optional<CustomSkinProperty> result = api.getProfile(lookup);

            assertTrue(result.isPresent());
            assertEquals("value2", result.get().getOriginalProperty().value());
        }

        @Test
        @DisplayName("Eclipse 404 → Mojang 404 → MineTools 200 OK → returns property")
        void eclipseAndMojangFailMineToolsSucceeds() {
            httpClient.addResponse(eclipseProfileUri, 404, "");
            httpClient.addResponse(mojangProfileUri, 404, "");
            httpClient.addResponse(mineToolsProfileUri, 200, profileMineToolsBody("value3", "sig3"));

            Optional<CustomSkinProperty> result = api.getProfile(lookup);

            assertTrue(result.isPresent());
            assertEquals("value3", result.get().getOriginalProperty().value());
        }

        @Test
        @DisplayName("All profile providers fail → empty")
        void allProvidersFail() {
            httpClient.addResponse(eclipseProfileUri, 404, "");
            httpClient.addResponse(mojangProfileUri, 404, "");

            Optional<CustomSkinProperty> result = api.getProfile(lookup);
            assertTrue(result.isEmpty());
        }
    }
    @Nested
    @DisplayName("HTTP outcomes per UUID provider")
    class UuidHttpOutcomes {

        @Test
        @DisplayName("Eclipse 204 → empty (falls through)")
        void eclipse204() {
            httpClient.addResponse(eclipseUuidUri, 204, "");
            httpClient.addResponse(mojangUuidUri, 404, "");

            Optional<UUID> result = api.getUUID(PLAYER_NAME);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Mojang 204 → empty")
        void mojang204() {
            httpClient.addResponse(eclipseUuidUri, 404, "");
            httpClient.addResponse(mojangUuidUri, 204, "");

            Optional<UUID> result = api.getUUID(PLAYER_NAME);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Mojang 429 → empty (falls through to MineTools)")
        void mojang429() {
            httpClient.addResponse(eclipseUuidUri, 404, "");
            httpClient.addResponse(mojangUuidUri, 429, "");
            httpClient.addResponse(mineToolsUuidUri, 200, uuidMineToolsBody(PLAYER_NAME, PLAYER_UUID));

            Optional<UUID> result = api.getUUID(PLAYER_NAME);

            assertTrue(result.isPresent());
            assertEquals(PLAYER_UUID, result.get());
        }

        @Test
        @DisplayName("Mojang 500 → empty (falls through)")
        void mojang500() {
            httpClient.addResponse(eclipseUuidUri, 404, "");
            httpClient.addResponse(mojangUuidUri, 500, "{}");
            httpClient.addResponse(mineToolsUuidUri, 200, uuidMineToolsBody(PLAYER_NAME, PLAYER_UUID));

            Optional<UUID> result = api.getUUID(PLAYER_NAME);
            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("Mojang malformed body → empty (falls through)")
        void mojangMalformedBody() {
            httpClient.addResponse(eclipseUuidUri, 404, "");
            httpClient.addResponse(mojangUuidUri, 200, "not-json-at-all");
            httpClient.addResponse(mineToolsUuidUri, 200, uuidMineToolsBody(PLAYER_NAME, PLAYER_UUID));

            Optional<UUID> result = api.getUUID(PLAYER_NAME);
            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("Transport timeout → empty (falls through)")
        void transportTimeout() {
            httpClient.addTimeout(eclipseUuidUri);
            httpClient.addResponse(mojangUuidUri, 200, uuidMojangBody(PLAYER_NAME, PLAYER_UUID));

            Optional<UUID> result = api.getUUID(PLAYER_NAME);

            assertTrue(result.isPresent());
            assertEquals(PLAYER_UUID, result.get());
        }

        @Test
        @DisplayName("All providers timeout → empty")
        void allTimeouts() {
            httpClient.addTimeout(eclipseUuidUri);
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
        @DisplayName("Eclipse 204 → empty (falls through)")
        void eclipse204() {
            httpClient.addResponse(eclipseProfileUri, 204, "");
            httpClient.addResponse(mojangProfileUri, 200, profileMojangBody("v", "s"));

            Optional<CustomSkinProperty> result = api.getProfile(lookup);
            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("Mojang 204 → empty (falls through)")
        void mojang204() {
            httpClient.addResponse(eclipseProfileUri, 404, "");
            httpClient.addResponse(mojangProfileUri, 204, "");

            Optional<CustomSkinProperty> result = api.getProfile(lookup);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Mojang null properties → empty")
        void mojangNullProperties() {
            httpClient.addResponse(eclipseProfileUri, 404, "");
            httpClient.addResponse(mojangProfileUri, 200, "{\"id\":\"abc\",\"name\":\"x\"}");

            Optional<CustomSkinProperty> result = api.getProfile(lookup);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Mojang empty value → empty")
        void mojangEmptyValue() {
            httpClient.addResponse(eclipseProfileUri, 404, "");
            httpClient.addResponse(mojangProfileUri, 200,
                    "{\"id\":\"x\",\"name\":\"x\",\"properties\":[{\"name\":\"textures\",\"value\":\"\",\"signature\":\"\"}]}");

            Optional<CustomSkinProperty> result = api.getProfile(lookup);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("MineTools ERR status → empty")
        void mineToolsErrStatus() {
            httpClient.addResponse(eclipseProfileUri, 404, "");
            httpClient.addResponse(mojangProfileUri, 404, "");
            httpClient.addResponse(mineToolsProfileUri, 200, "{\"raw\":{\"id\":\"x\",\"name\":\"x\",\"status\":\"ERR\",\"properties\":[]}}");

            Optional<CustomSkinProperty> result = api.getProfile(lookup);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Mojang 429 → empty (falls through)")
        void mojang429() {
            httpClient.addResponse(eclipseProfileUri, 404, "");
            httpClient.addResponse(mojangProfileUri, 429, "");

            Optional<CustomSkinProperty> result = api.getProfile(lookup);
            // MojangProfileResponse parsing of empty body returns null properties → empty
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Profile transport timeout → empty (falls through)")
        void transportTimeout() {
            httpClient.addTimeout(eclipseProfileUri);
            httpClient.addResponse(mojangProfileUri, 200, profileMojangBody("v", "sig"));

            Optional<CustomSkinProperty> result = api.getProfile(lookup);
            assertTrue(result.isPresent());
        }
    }
    @Nested
    @DisplayName("getSkin integration")
    class GetSkin {

        @Test
        @DisplayName("Valid UUID and profile → returns MojangSkinDataResult")
        void fullSuccess() {
            httpClient.addResponse(eclipseUuidUri, 200, uuidEclipseBody(PLAYER_UUID));
            httpClient.addResponse(eclipseProfileUri, 200, profileEclipseBody("val", "sig"));

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
            String uuidStr = PLAYER_UUID.toString();
            httpClient.addResponse(URI.create("http://test.local/profile/eclipse/" + PLAYER_UUID), 200, profileEclipseBody("v", "s"));

            Optional<MojangSkinDataResult> result = api.getSkin(uuidStr);

            assertTrue(result.isPresent());
        }
    }

    /* ================================================================== */
    /*  JSON body helpers                                                  */
    /* ================================================================== */

    private static String uuidEclipseBody(UUID uuid) {
        return "{\"cacheData\":{\"state\":\"MISS\",\"createdAt\":0},\"exists\":true,\"uuid\":\"" + uuid + "\"}";
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

    private static String profileEclipseBody(String value, String signature) {
        return "{\"cacheData\":{\"state\":\"MISS\",\"createdAt\":0},\"exists\":true,\"skinProperty\":{\"value\":\"" + value + "\",\"signature\":\"" + signature + "\"}}";
    }

    private static String profileMojangBody(String value, String signature) {
        return "{\"id\":\"x\",\"name\":\"x\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + value + "\",\"signature\":\"" + signature + "\"}]}";
    }

    private static String profileMineToolsBody(String value, String signature) {
        return "{\"raw\":{\"id\":\"x\",\"name\":\"x\",\"status\":\"OK\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + value + "\",\"signature\":\"" + signature + "\"}]}}";
    }
}
