/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Texture-payload decoding coverage: the mod's skins arrive as base64
 * {@code value}/{@code signature} pairs (CustomSkinProperty) whose payload
 * is the Mojang textures JSON. Decoding is exercised against real fixture
 * payloads (memory #1115: no live services).
 */
class TextureDecoderTest {

    private static final Gson GSON = new Gson();

    private static JsonObject decodeValue(String base64Value) {
        byte[] raw = Base64.getDecoder().decode(base64Value);
        return GSON.fromJson(new String(raw, StandardCharsets.UTF_8), JsonObject.class);
    }

    @Test
    @DisplayName("A skin value decodes to a textures payload with a SKIN url")
    void decode_skinSignature_parsesValue() throws Exception {
        String fixture = new String(
                getClass().getResourceAsStream("/fixtures/mojang/profile-200-notch.json").readAllBytes(),
                StandardCharsets.UTF_8);
        JsonObject profile = GSON.fromJson(fixture, JsonObject.class);
        String value = profile.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
        String signature = profile.getAsJsonArray("properties").get(0).getAsJsonObject().get("signature").getAsString();

        JsonObject textures = decodeValue(value);
        String url = textures.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
        assertTrue(url.startsWith("http"));

        // The property round-trips through the :common carrier type.
        CustomSkinProperty property = new CustomSkinProperty("textures", value, signature, "MojangAPI");
        assertFalse(property.isEmpty());
        assertTrue(property.isValid());
        assertEquals("textures", property.getOriginalProperty().name());
    }

    @Test
    @DisplayName("Malformed base64 is rejected by the decoder")
    void decode_malformedBase64_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> Base64.getDecoder().decode("not-base64!!!"));
    }

    @Test
    @DisplayName("Slim-profile payloads carry model=slim metadata")
    void decode_slimProfile_flag() throws Exception {
        String fixture = new String(
                getClass().getResourceAsStream("/fixtures/mojang/profile-200-metadata-slim.json").readAllBytes(),
                StandardCharsets.UTF_8);
        JsonObject profile = GSON.fromJson(fixture, JsonObject.class);
        String value = profile.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();

        JsonObject textures = decodeValue(value);
        String model = textures.getAsJsonObject("textures").getAsJsonObject("SKIN")
                .getAsJsonObject("metadata").get("model").getAsString();
        assertEquals("slim", model);
    }
}
