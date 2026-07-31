package levosilimo.everlastingskins.util;

import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.skinchanger.responses.profile.DecodedTextureProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Texture round-trip: encode a Property, decode via PropertyUtils,
 * extract URL and variant, assert slim/classic detection.
 */
class PropertyUtilsTest {

    private static final String TEXTURE_URL = "http://textures.minecraft.net/texture/test123texture";
    private static final String TEXTURE_ID = "test123texture";

    @Nested
    @DisplayName("Classic skin (no metadata)")
    class ClassicSkin {

        private final CustomSkinProperty property = createProperty(null);

        @Test
        @DisplayName("getSkinTextureUrl returns full URL")
        void textureUrl() {
            String url = PropertyUtils.getSkinTextureUrl(property);
            assertEquals(TEXTURE_URL, url);
        }

        @Test
        @DisplayName("getTextureId returns texture ID")
        void strippedUrl() {
            String stripped = PropertyUtils.getTextureId(property);
            assertEquals(TEXTURE_ID, stripped);
        }

        @Test
        @DisplayName("getSkinVariant returns CLASSIC when no metadata")
        void variantClassic() {
            assertEquals(SkinVariant.CLASSIC, PropertyUtils.getSkinVariant(property));
        }

        @Test
        @DisplayName("getSkinProfileData returns decoded structure")
        void profileData() {
            DecodedTextureProperty data = PropertyUtils.getSkinProfileData(property);
            assertNotNull(data);
            assertNotNull(data.textures());
            assertNotNull(data.textures().SKIN());
            assertEquals(TEXTURE_URL, data.textures().SKIN().url());
            assertNull(data.textures().SKIN().metadata());
        }
    }

    @Nested
    @DisplayName("Slim skin (model=slim)")
    class SlimSkin {

        private final CustomSkinProperty property = createProperty("slim");

        @Test
        @DisplayName("getSkinVariant returns SLIM when model=slim")
        void variantSlim() {
            assertEquals(SkinVariant.SLIM, PropertyUtils.getSkinVariant(property));
        }

        @Test
        @DisplayName("getSkinProfileData includes metadata with model=slim")
        void profileDataWithMetadata() {
            DecodedTextureProperty data = PropertyUtils.getSkinProfileData(property);
            assertNotNull(data.textures().SKIN().metadata());
            assertEquals("slim", data.textures().SKIN().metadata().model());
        }
    }

    @Nested
    @DisplayName("Round-trip consistency")
    class RoundTrip {

        @Test
        @DisplayName("Encode and decode preserves URL and variant")
        void encodeDecodeRoundTrip() {
            CustomSkinProperty slimProp = createProperty("slim");
            CustomSkinProperty classicProp = createProperty(null);

            assertEquals(SkinVariant.SLIM, PropertyUtils.getSkinVariant(slimProp));
            assertEquals(SkinVariant.CLASSIC, PropertyUtils.getSkinVariant(classicProp));
            assertEquals(TEXTURE_URL, PropertyUtils.getSkinTextureUrl(slimProp));
            assertEquals(TEXTURE_URL, PropertyUtils.getSkinTextureUrl(classicProp));
        }
    }

    /* ================================================================== */
    /*  Helpers                                                            */
    /* ================================================================== */

    /**
     * Create a CustomSkinProperty whose value is a base64-encoded texture
     * JSON. The texture JSON includes an optional "model" metadata field.
     */
    private static CustomSkinProperty createProperty(String model) {
        String texturesJson = buildTexturesJson(model);
        String base64 = Base64.getEncoder().encodeToString(texturesJson.getBytes(StandardCharsets.UTF_8));
        return new CustomSkinProperty("textures", base64, "signature==", null);
    }

    private static String buildTexturesJson(String model) {
        String skinEntry;
        if (model != null) {
            skinEntry = """
                    "SKIN": {
                      "url": "%s",
                      "metadata": {
                        "model": "%s"
                      }
                    }
                    """.formatted(TEXTURE_URL, model);
        } else {
            skinEntry = """
                    "SKIN": {
                      "url": "%s"
                    }
                    """.formatted(TEXTURE_URL);
        }

        return """
                {
                  "timestamp": 1234567890,
                  "profileId": "f0000000000000000000000000000000",
                  "profileName": "TestPlayer",
                  "signatureRequired": false,
                  "textures": {
                    %s
                  }
                }
                """.formatted(skinEntry);
    }
}
