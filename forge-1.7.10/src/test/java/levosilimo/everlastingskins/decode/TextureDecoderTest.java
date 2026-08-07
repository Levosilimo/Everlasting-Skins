/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.decode;

import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.skinchanger.responses.profile.DecodedTextureProperty;
import levosilimo.everlastingskins.skinchanger.responses.profile.MojangProfileTexture;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.PropertyUtils;
import org.junit.Test;

import java.util.Base64;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Base64 texture-payload decode (skin/cape + slim/classic variant).
 *
 * <p>Deterministic fakes only (memory #1115): all payloads are locally
 * constructed fixtures — no live HTTP. Exercises the shared
 * {@link PropertyUtils} decode path that the 1.7.10 GameProfile textures
 * property feeds.
 */
public class TextureDecoderTest {

    private static final UUID TEST_UUID = UUID.randomUUID();

    @Test
    public void decodesSkinUrl() {
        String json = "{\"timestamp\":1,\"profileId\":\"" + TEST_UUID + "\","
            + "\"profileName\":\"Notch\","
            + "\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/skinId\"}}}";
        CustomSkinProperty property = property(json);

        DecodedTextureProperty decoded = PropertyUtils.getSkinProfileData(property);
        assertNotNull(decoded.textures());
        MojangProfileTexture skin = decoded.textures().SKIN();
        // CAPE() is a DecodedTextureProperty on this shared response shape
        // (unset when the payload has no cape) — assert the skin URL only.
        assertEquals("https://textures.minecraft.net/texture/skinId", skin.url());
    }

    @Test
    public void decodesSlimVariant() {
        String json = "{\"timestamp\":1,\"profileId\":\"" + TEST_UUID + "\","
            + "\"profileName\":\"Notch\","
            + "\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/s\","
            + "\"metadata\":{\"model\":\"slim\"}}}}";
        assertEquals(SkinVariant.SLIM, PropertyUtils.getSkinVariant(property(json)));
    }

    @Test
    public void decodesClassicVariantByDefault() {
        String json = "{\"timestamp\":1,\"profileId\":\"" + TEST_UUID + "\","
            + "\"profileName\":\"Notch\","
            + "\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/s\"}}}";
        assertEquals(SkinVariant.CLASSIC, PropertyUtils.getSkinVariant(property(json)));
    }

    @Test
    public void classicModelIsClassic() {
        String json = "{\"timestamp\":1,\"profileId\":\"" + TEST_UUID + "\","
            + "\"profileName\":\"Notch\","
            + "\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/s\","
            + "\"metadata\":{\"model\":\"classic\"}}}}";
        assertEquals(SkinVariant.CLASSIC, PropertyUtils.getSkinVariant(property(json)));
    }

    @Test
    public void corruptBase64IsInvalid() {
        CustomSkinProperty corrupt =
            new CustomSkinProperty("textures", "not-base64!!!", null, "test");
        assertFalse(corrupt.isValid());
    }

    @Test
    public void validBase64IsValid() {
        assertTrue(property("{\"textures\":{}}").isValid());
    }

    private static CustomSkinProperty property(String json) {
        return new CustomSkinProperty(
            "textures",
            Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            null,
            "MojangAPI");
    }
}
