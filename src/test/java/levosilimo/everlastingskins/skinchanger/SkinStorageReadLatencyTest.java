/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.metrics.PlayerSnapshot;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.metrics.Snapshot;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Read-path observability: a map-miss getSkin aggregates a load-level read
 * (SkinStorage.loadSkin) plus a disk-level read (SkinIO.readSkinFile), both
 * recorded into the shared read counters/histogram; a map hit records nothing.
 * Uses {@link SkinMetrics#INSTANCE} (the production instance the code paths
 * record into) and resets it per test for isolation.
 */
class SkinStorageReadLatencyTest {

    @TempDir
    Path tempDir;

    private SkinIO skinIO;
    private SkinStorage storage;
    private UUID uuid;

    @BeforeEach
    void setUp() {
        SkinMetrics.INSTANCE.reset();
        skinIO = new SkinIO(tempDir);
        storage = new SkinStorage(skinIO);
        uuid = UUID.randomUUID();
    }

    @Test
    @DisplayName("getSkin on map miss records load and disk read latency")
    void getSkin_onMapMiss_recordsReadLatency() {
        CustomSkinProperty persisted = new CustomSkinProperty("cGVyc2lzdGVk", "sig", "disk");
        skinIO.saveSkin(uuid, persisted);

        CustomSkinProperty result = storage.getSkin(uuid);
        assertNotNull(result);

        Snapshot s = SkinMetrics.INSTANCE.snapshot();
        // One load-level read (SkinStorage.loadSkin) + one disk-level read (readSkinFile).
        assertEquals(2, s.readsSubmitted());
        assertEquals(2, s.readsCompleted());
        assertEquals(0, s.readFailures());
        assertTrue(s.readDiskPercentiles().get("max") > 0, "readDiskLatency must record a positive sample");
        assertTrue(s.readDiskPercentiles().get("p50") > 0, "readDiskLatency p50 must land in a positive bucket");
    }

    @Test
    @DisplayName("getSkin on map hit records no read latency")
    void getSkin_onMapHit_recordsNothing() {
        CustomSkinProperty cached = new CustomSkinProperty("Y2FjaGVk", "sig", "cache");
        storage.setSkin(uuid, cached);

        storage.getSkin(uuid);
        storage.getSkin(uuid);

        Snapshot s = SkinMetrics.INSTANCE.snapshot();
        assertEquals(0, s.readsSubmitted());
        assertEquals(0, s.readsCompleted());
        assertEquals(0, s.readFailures());
        assertEquals(0, s.readDiskPercentiles().get("max"));
    }
}
