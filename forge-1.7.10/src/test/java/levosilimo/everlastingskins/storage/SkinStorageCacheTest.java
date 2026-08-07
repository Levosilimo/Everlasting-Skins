/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.storage;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.skinchanger.MojangProfileCache;
import levosilimo.everlastingskins.skinchanger.SkinIO;
import levosilimo.everlastingskins.skinchanger.SkinStorage;
import levosilimo.everlastingskins.skinchanger.SkinStorageProvider;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.PropertyUtils;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Skin cache + GameProfile decode against the 1.7.10 surface.
 *
 * <p>Deterministic fakes only (memory #1115): all payloads are locally
 * constructed base64 fixtures — no live HTTP, no network. The 1.7.10
 * GameProfile ({@code com.mojang.authlib.GameProfile}) carries its
 * {@code textures} property exactly like 1.8+, so the shared
 * {@link PropertyUtils} decode path is exercised unchanged.
 */
public class SkinStorageCacheTest {

    private static final UUID TEST_UUID = UUID.randomUUID();

    private SkinStorageProvider provider;

    @Before
    public void setUp() throws Exception {
        Path dir = Files.createTempDirectory("es-skin-storage-test");
        provider = new SkinStorageProvider(new SkinStorage(new SkinIO(dir)));
        SkinStorage.resetForTest();
    }

    @Test
    public void cacheHitAfterSet() {
        CustomSkinProperty skin = fixture("aGVsbG8=", "slim");
        assertNull(provider.getSkin(TEST_UUID));
        provider.applySkin(new GameProfile(TEST_UUID, "Notch"), TEST_UUID, skin);
        assertNotNull(provider.getSkin(TEST_UUID));
    }

    @Test
    public void cacheMissAfterClear() {
        CustomSkinProperty skin = fixture("aGVsbG8=", "classic");
        provider.applySkin(new GameProfile(TEST_UUID, "Notch"), TEST_UUID, skin);
        provider.clearSkin(new GameProfile(TEST_UUID, "Notch"), TEST_UUID);
        assertNull(provider.getSkin(TEST_UUID));
    }

    @Test
    public void emptySkinNotStored() {
        CustomSkinProperty empty = new CustomSkinProperty("textures", "", null, "test");
        provider.applySkin(new GameProfile(TEST_UUID, "Notch"), TEST_UUID, empty);
        assertNull(provider.getSkin(TEST_UUID));
    }

    @Test
    public void profileTexturesPropertyDecodes() {
        CustomSkinProperty skin = fixture("c2tpbi11cmw=", "slim");
        GameProfile profile = new GameProfile(TEST_UUID, "Notch");
        provider.applySkin(profile, TEST_UUID, skin);

        // The 1.7.10 GameProfile surface: textures property readable via
        // the authlib PropertyMap, decodable by the shared PropertyUtils.
        Property property = profile.getProperties().get("textures").iterator().next();
        assertEquals("textures", property.getName());
        CustomSkinProperty roundTrip = new CustomSkinProperty(
            property.getName(), property.getValue(), property.getSignature(), "MojangAPI");
        assertNotNull(PropertyUtils.getSkinProfileData(roundTrip));
        assertEquals(
            levosilimo.everlastingskins.enums.SkinVariant.SLIM,
            PropertyUtils.getSkinVariant(roundTrip));
    }

    @Test
    public void mojangProfileCacheEvictsOverCapacity() {
        MojangProfileCache cache = new MojangProfileCache(60_000, 2);
        cache.put("a", fixture("YQ==", "classic"));
        cache.put("b", fixture("Yg==", "classic"));
        cache.put("c", fixture("Yw==", "classic"));
        assertTrue("cache must stay at capacity", cache.size() <= 2);
    }

    @Test
    public void mojangProfileCacheHitAndMiss() {
        MojangProfileCache cache = new MojangProfileCache(60_000, 10);
        assertNull(cache.get("Notch"));
        cache.put("Notch", fixture("bm90Y2g=", "slim"));
        assertNotNull(cache.get("Notch"));
        assertEquals(1, cache.size());
    }

    @Test
    public void sourcePersistedThroughProvider() {
        CustomSkinProperty skin = fixture("c291cmNl", "classic");
        provider.applySkin(new GameProfile(TEST_UUID, "Notch"), TEST_UUID, skin);
        assertEquals("MojangAPI", provider.getSource(TEST_UUID));
    }

    /** Locally constructed base64 textures payload (memory #1115 — no live HTTP). */
    private static CustomSkinProperty fixture(String urlFragment, String model) {
        String json = "{\"timestamp\":1,\"profileId\":\"" + TEST_UUID + "\","
            + "\"profileName\":\"Notch\",\"textures\":{\"SKIN\":{\"url\":"
            + "\"https://textures.minecraft.net/texture/" + urlFragment + "\""
            + (model.equals("slim") ? ",\"metadata\":{\"model\":\"slim\"}" : "")
            + "}}}";
        return new CustomSkinProperty(
            "textures",
            Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            null,
            "MojangAPI");
    }
}
