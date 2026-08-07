/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.UUIDUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-JUnit tests for the :common {@link SkinStorage} as consumed by the
 * 1.8.9 lane (memory #1115: deterministic fakes only — the SkinIO seam is
 * mocked, no live HTTP / Mojang / MineSkin calls anywhere).
 */
class SkinStorageCacheTest {

    private static final UUID PLAYER = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

    @TempDir
    Path tempDir;

    private SkinIO skinIO;
    private SkinStorage storage;

    @BeforeEach
    void setUp() {
        skinIO = mock(SkinIO.class);
        when(skinIO.saveSkinAsync(any(UUID.class), any(CustomSkinProperty.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        storage = new SkinStorage(skinIO);
    }

    @AfterEach
    void tearDown() {
        SkinStorage.resetForTest();
    }

    @Test
    void putGetByDashedUuidRoundTrips() {
        CustomSkinProperty skin = skin("value-1", "signature-1");
        storage.setSkin(PLAYER, skin);
        assertSame(skin, storage.getSkin(PLAYER));
    }

    @Test
    void dashlessKeyNormalizedViaConvertToDashed() {
        // getPersistentID()/GameProfile surfaces can yield 32-char no-dash
        // UUIDs; UUIDUtils.convertToDashed normalizes them for keying.
        String noDashes = UUIDUtils.convertToNoDashes(PLAYER);
        UUID normalized = SkinRestorer.normalizeUuid(noDashes);
        assertEquals(PLAYER, normalized);

        CustomSkinProperty skin = skin("value-2", "signature-2");
        storage.setSkin(normalized, skin);
        assertSame(skin, storage.getSkin(PLAYER));
    }

    @Test
    void normalizeUuidRejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> SkinRestorer.normalizeUuid("not-a-uuid"));
    }

    @Test
    void cacheMissOnUnknownUuid() {
        when(skinIO.loadSkin(any(UUID.class))).thenReturn(null);
        assertNull(storage.getSkin(UUID.randomUUID()));
        verify(skinIO).loadSkin(any(UUID.class));
    }

    @Test
    void coalescingAsyncWriteGoesThroughSkinIo() {
        CustomSkinProperty skin = skin("value-3", "signature-3");
        storage.saveSkinAsync(PLAYER, skin);
        verify(skinIO).saveSkinAsync(eq(PLAYER), eq(skin));

        storage.flushPending();
        verify(skinIO).flushPending();
    }

    private static CustomSkinProperty skin(String value, String signature) {
        return new CustomSkinProperty("textures", value, signature, null);
    }
}
