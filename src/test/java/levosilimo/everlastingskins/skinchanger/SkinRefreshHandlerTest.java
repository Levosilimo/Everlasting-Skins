/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.permission.TestConfigSupport;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cascade atomicity invariant (R3): the disk flush must be enqueued BEFORE
 * the GameProfile is mutated, so a failed enqueue leaves the applied profile
 * untouched — applied and on-disk skin stay consistent and the change cannot
 * silently revert on the next server restart. The invariant lives in
 * {@link SkinRefreshHandler#applyAtomicPersistence} so it is unit-testable
 * without a ServerPlayer (1.21 entity classes need the FML runtime).
 */
class SkinRefreshHandlerTest {

    private static final UUID PLAYER_UUID = UUID.randomUUID();
    private static final Property OLD_PROPERTY = new Property("textures", "oldValue", "oldSignature");
    private static final CustomSkinProperty NEW_SKIN =
            new CustomSkinProperty("textures", "newValue", "newSignature", "MojangAPI");

    @TempDir
    Path tempDir;

    private SkinIO skinIO;
    private SkinStorage storage;

    @BeforeAll
    static void loadConfig() {
        TestConfigSupport.loadDefaults();
    }

    @BeforeEach
    void setUp() throws Exception {
        Path skinDir = tempDir.resolve("EverlastingSkins");
        Files.createDirectories(skinDir);
        skinIO = new SkinIO(skinDir);
        storage = new SkinStorage(skinIO);
        setStaticField(SkinRestorer.class, "skinStorage", storage);
        SkinRestorer.server = null;
        SkinMetrics.INSTANCE.reset();
    }

    @AfterEach
    void tearDown() throws Exception {
        skinIO.flushPending();
        setStaticField(SkinRestorer.class, "skinStorage", null);
        SkinRestorer.server = null;
    }

    private static void setStaticField(Class<?> clazz, String name, Object value) throws Exception {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static GameProfile profileWithTextures(Property textures) {
        GameProfile profile = new GameProfile(PLAYER_UUID, "Alice");
        profile.getProperties().put("textures", textures);
        return profile;
    }

    private static Collection<Property> texturesOf(GameProfile profile) {
        return profile.getProperties().get("textures");
    }

    @Test
    @DisplayName("saveSkinAsync failure leaves the applied GameProfile textures untouched")
    void saveSkinAsyncFailure_leavesProfileUntouched() throws Exception {
        GameProfile profile = profileWithTextures(OLD_PROPERTY);
        SkinStorage failing = mock(SkinStorage.class);
        when(failing.getSkin(any(UUID.class))).thenReturn(NEW_SKIN);
        doThrow(new RuntimeException("simulated disk enqueue failure"))
                .when(failing).saveSkinAsync(any(UUID.class));
        setStaticField(SkinRestorer.class, "skinStorage", failing);

        long failedBefore = SkinMetrics.INSTANCE.snapshot().refreshesFailed();
        long savesBefore = SkinMetrics.INSTANCE.snapshot().savesSubmitted();

        boolean persisted = SkinRefreshHandler.applyAtomicPersistence(PLAYER_UUID, profile, NEW_SKIN);

        assertFalse(persisted, "a failed enqueue must report failure");
        Collection<Property> textures = texturesOf(profile);
        assertEquals(1, textures.size(), "a failed save must not mutate the profile");
        assertEquals(OLD_PROPERTY, textures.iterator().next(),
                "the applied profile must keep the previous textures when persistence fails");
        assertEquals(failedBefore + 1, SkinMetrics.INSTANCE.snapshot().refreshesFailed(),
                "the partial cascade failure must be recorded");
        assertEquals(savesBefore, SkinMetrics.INSTANCE.snapshot().savesSubmitted(),
                "a failed enqueue must not count as a submitted save");
    }

    @Test
    @DisplayName("successful save applies the stored skin to the GameProfile")
    void saveSkinAsyncSuccess_appliesStoredSkin() throws Exception {
        GameProfile profile = profileWithTextures(OLD_PROPERTY);
        storage.setSkin(PLAYER_UUID, NEW_SKIN);

        long failedBefore = SkinMetrics.INSTANCE.snapshot().refreshesFailed();
        long savesBefore = SkinMetrics.INSTANCE.snapshot().savesSubmitted();

        boolean persisted = SkinRefreshHandler.applyAtomicPersistence(PLAYER_UUID, profile, NEW_SKIN);
        skinIO.flushPending();

        assertTrue(persisted, "a successful enqueue must report success");
        Collection<Property> textures = texturesOf(profile);
        assertEquals(1, textures.size());
        assertEquals(NEW_SKIN.getOriginalProperty(), textures.iterator().next(),
                "the applied profile must match the saved skin value");
        assertEquals(failedBefore, SkinMetrics.INSTANCE.snapshot().refreshesFailed(),
                "a successful refresh must not record a failure");
        assertEquals(savesBefore + 1, SkinMetrics.INSTANCE.snapshot().savesSubmitted(),
                "the cascade must enqueue exactly one save");
    }

    @Test
    @DisplayName("restart equivalence: after the cascade the on-disk skin equals the in-memory skin")
    void afterCascade_ondiskSkinEqualsInMemorySkin() throws Exception {
        GameProfile profile = new GameProfile(PLAYER_UUID, "Alice");
        storage.setSkin(PLAYER_UUID, NEW_SKIN);

        boolean persisted = SkinRefreshHandler.applyAtomicPersistence(PLAYER_UUID, profile, NEW_SKIN);
        skinIO.flushPending();

        assertTrue(persisted, "the cascade must report success");
        CustomSkinProperty fromDisk = skinIO.loadSkin(PLAYER_UUID);
        assertNotNull(fromDisk, "the cascade must have persisted the skin to disk");
        assertEquals(NEW_SKIN, fromDisk,
                "the on-disk skin after the cascade must equal the stored in-memory skin");
        assertEquals(storage.getSkin(PLAYER_UUID), fromDisk,
                "a restart reloading from disk must reproduce the in-memory skin");
    }
}
