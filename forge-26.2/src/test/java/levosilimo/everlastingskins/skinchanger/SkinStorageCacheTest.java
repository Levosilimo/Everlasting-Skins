/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UUID-keyed SkinStorage/cache coverage against the 26.2 binding.
 *
 * <p>Memory #1123 restated: SkinStorage is keyed by UUID ONLY — the storage
 * API surface has no player-object overload, and the forge-26.2 binding
 * extracts {@code player.getUUID()} at the lane boundary before any
 * :common call. These tests assert the UUID-keyed contract (distinct UUIDs
 * never alias; same UUID overwrites in place).
 *
 * <p>SkinIO is faked by subclass (no Mockito: Java 25 inline-mock
 * instrumentation is unsupported on Mockito 5.12).
 */
class SkinStorageCacheTest {

    /** In-memory SkinIO double: load/save/delete against a plain map. */
    private static final class FakeSkinIO extends SkinIO {
        private final Map<UUID, CustomSkinProperty> disk = new HashMap<>();

        FakeSkinIO() {
            super(Path.of("."));
        }

        @Override
        public CustomSkinProperty loadSkin(UUID uuid) {
            return disk.get(uuid);
        }

        @Override
        public void saveSkin(UUID uuid, CustomSkinProperty skin) {
            disk.put(uuid, skin);
        }

        @Override
        public CompletableFuture<Void> saveSkinAsync(UUID uuid, CustomSkinProperty skin) {
            disk.put(uuid, skin);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void deleteSkin(UUID uuid) {
            disk.remove(uuid);
        }

        @Override
        public void flushPending() {
        }

        @Override
        public boolean hasPendingWrites() {
            return false;
        }


    }

    private FakeSkinIO skinIO;
    private SkinStorage storage;

    private static final UUID UUID_A = UUID.randomUUID();
    private static final UUID UUID_B = UUID.randomUUID();

    private static CustomSkinProperty skin(String source) {
        return new CustomSkinProperty("textures", "value-" + source, "sig-" + source, source);
    }

    @BeforeEach
    void setUp() {
        skinIO = new FakeSkinIO();
        storage = new SkinStorage(skinIO);
        SkinStorage.resetForTest();
    }

    @Test
    @DisplayName("set/get round-trips by UUID without touching the disk")
    void cache_uuidKeyed_roundTrip() {
        CustomSkinProperty s = skin("mojang");
        storage.setSkin(UUID_A, s);
        assertSame(s, storage.getSkin(UUID_A));
        assertNull(skinIO.disk.get(UUID_A), "map hit must not touch the disk layer");
    }

    @Test
    @DisplayName("A cache miss loads from disk through SkinIO (UUID keyed)")
    void cache_miss_loadsFromDisk() {
        CustomSkinProperty stored = skin("mojang");
        skinIO.disk.put(UUID_A, stored);
        assertSame(stored, storage.getSkin(UUID_A));
        // Second read is a map hit — the disk layer is not consulted again.
        assertSame(stored, storage.getSkin(UUID_A));
    }

    @Test
    @DisplayName("Distinct UUIDs never alias; the same UUID overwrites in place")
    void cache_deduplicatesSameUuid() {
        storage.setSkin(UUID_A, skin("first"));
        storage.setSkin(UUID_A, skin("second"));
        storage.setSkin(UUID_B, skin("other"));
        assertEquals("second", storage.getSkin(UUID_A).getSource());
        assertEquals("other", storage.getSkin(UUID_B).getSource());
        assertNotSame(UUID_A, UUID_B);
    }

    @Test
    @DisplayName("saveSkin delegates to SkinIO only for the in-memory UUID")
    void cache_saveDelegatesByUuid() {
        CustomSkinProperty s = skin("mojang");
        storage.setSkin(UUID_A, s);
        storage.saveSkin(UUID_A);
        assertSame(s, skinIO.disk.get(UUID_A));
        assertNull(skinIO.disk.get(UUID_B));
    }

    @Test
    @DisplayName("removeSkin drops the map entry and the file together")
    void cache_removeClearsBothLayers() {
        storage.setSkin(UUID_A, skin("mojang"));
        storage.removeSkin(UUID_A);
        assertNull(storage.getSkin(UUID_A));
        assertNull(skinIO.disk.get(UUID_A));
    }
}
