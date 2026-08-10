/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-JUnit tests for the 1.10.2 {@link SkinLoginHandler} login-apply /
 * logout-persist contract (memory #1115: deterministic fakes only — mocked
 * player + storage via the SkinRestorer test seam; no live server, no HTTP).
 */
class SkinLoginHandlerTest {

    private static final UUID PLAYER_UUID = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final CustomSkinProperty SKIN =
            new CustomSkinProperty("textures", "value-1", "signature-1", "MojangAPI");

    private SkinLoginHandler handler;
    private EntityPlayerMP player;
    private GameProfile profile;
    private SkinStorage storage;

    @BeforeEach
    void setUp() {
        SkinMetrics.INSTANCE.reset();
        handler = new SkinLoginHandler();
        profile = new GameProfile(PLAYER_UUID, "Steve");
        player = mock(EntityPlayerMP.class);
        when(player.getGameProfile()).thenReturn(profile);
        when(player.getUniqueID()).thenReturn(PLAYER_UUID);
        storage = mock(SkinStorage.class);
        SkinRestorer.setSkinStorageForTest(storage);
    }

    @AfterEach
    void tearDown() {
        SkinRestorer.setSkinStorageForTest(null);
    }

    private Collection<Property> appliedTextures() {
        return profile.getProperties().get("textures");
    }

    @Test
    void loginAppliesStoredSkinToProfile() {
        when(storage.getSkin(PLAYER_UUID)).thenReturn(SKIN);

        handler.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player));

        assertEquals(1, appliedTextures().size());
        assertEquals("value-1", appliedTextures().iterator().next().getValue());
    }

    @Test
    void loginWithoutStoredSkinLeavesProfileUntouched() {
        when(storage.getSkin(PLAYER_UUID)).thenReturn(null);

        handler.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player));

        assertTrue(appliedTextures().isEmpty());
    }

    @Test
    void loginWithNullStorageIsSafe() {
        SkinRestorer.setSkinStorageForTest(null);

        handler.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player));

        assertTrue(appliedTextures().isEmpty());
    }

    @Test
    void loginReplacesStaleAppliedTextures() {
        profile.getProperties().put("textures", new Property("textures", "stale", "sig"));
        when(storage.getSkin(PLAYER_UUID)).thenReturn(SKIN);

        handler.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player));

        assertEquals(1, appliedTextures().size());
        assertEquals("value-1", appliedTextures().iterator().next().getValue());
    }

    @Test
    void logoutPersistsStoredSkin() {
        when(storage.getSkin(PLAYER_UUID)).thenReturn(SKIN);

        handler.onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(player));

        verify(storage).saveSkin(PLAYER_UUID);
    }

    @Test
    void logoutWithoutStoredSkinSkipsSave() {
        when(storage.getSkin(PLAYER_UUID)).thenReturn(null);

        handler.onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(player));

        verify(storage, never()).saveSkin(any(UUID.class));
    }
}
