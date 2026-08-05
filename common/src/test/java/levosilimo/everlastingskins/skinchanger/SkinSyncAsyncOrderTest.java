/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sync/async ordering regression tests: the drain-coalesce writer is
 * asynchronous, but a sync saveSkin(UUID) is a strict last-write-wins save
 * and must land on disk AFTER any async write for the same UUID — queued
 * (purged so the drain skips it) or in-flight (FIFO on the writer thread).
 */
class SkinSyncAsyncOrderTest {

    @TempDir
    Path tempDir;

    private SkinStorage storage;

    @BeforeEach
    void setUp() {
        storage = new SkinStorage(new SkinIO(tempDir));
    }

    @Test
    @DisplayName("sync save after a queued async save: latest value wins on disk")
    void syncSaveAfterQueuedAsyncSave_lastWriteWins() throws Exception {
        UUID u = UUID.randomUUID();
        storage.setSkin(u, skin("Yg==")); // v2 = latest, in the in-memory map
        storage.saveSkinAsync(u, skin("YQ==")); // v1 = stale payload, queued for the debounced drain
        storage.saveSkin(u); // sync: must purge the queued v1 and land v2
        storage.flushPending();

        String disk = new String(Files.readAllBytes(tempDir.resolve(u + ".json")), StandardCharsets.UTF_8);
        assertEquals("Yg==", valueOnDisk(disk),
                "disk holds YQ== (stale async payload) but model expects Yg== (latest sync save)");
    }

    @Test
    @DisplayName("sync save blocks until an in-flight drain write for the same UUID has landed")
    void syncSaveWaitsForInFlightDrainWrite() throws Exception {
        BlockingSkinIO blockingIO = new BlockingSkinIO(tempDir);
        SkinStorage blockingStorage = new SkinStorage(blockingIO);
        UUID u = UUID.randomUUID();
        Path target = tempDir.resolve(u + ".json");

        blockingStorage.setSkin(u, skin("Yg=="));
        blockingStorage.saveSkinAsync(u, skin("YQ=="));
        assertTrue(blockingIO.writeStarted.await(5, TimeUnit.SECONDS),
                "drain must reach the file write before the sync save");

        CountDownLatch syncDone = new CountDownLatch(1);
        ExecutorService saver = Executors.newSingleThreadExecutor();
        try {
            Future<?> sync = saver.submit(() -> {
                blockingStorage.saveSkin(u);
                syncDone.countDown();
            });
            // Unfixed code writes v2 directly on the caller thread while the
            // drain is blocked, so the sync save returns immediately and the
            // drain's stale v1 lands last. Fixed code submits through the
            // single writer thread (FIFO), so the sync save cannot complete
            // until the blocked drain write has landed.
            boolean completedWhileDrainBlocked = syncDone.await(2, TimeUnit.SECONDS);
            blockingIO.releaseWrite.countDown();
            sync.get(5, TimeUnit.SECONDS);
            blockingStorage.flushPending();

            String disk = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
            assertEquals("Yg==", valueOnDisk(disk),
                    "disk holds YQ== (stale async payload) but model expects Yg== (latest sync save)"
                            + " (completedWhileDrainBlocked=" + completedWhileDrainBlocked + ")");
        } finally {
            saver.shutdownNow();
        }
    }

    private static CustomSkinProperty skin(String value) {
        return new CustomSkinProperty(value, "sig", "src");
    }

    /** Parses the serialized value field off disk (Gson escapes '=' as \\u003d). */
    private static String valueOnDisk(String diskJson) {
        return JsonUtils.parseJson(diskJson)
                .get("originalProperty").getAsJsonObject()
                .get("value").getAsString();
    }

    /** SkinIO whose drain write blocks until the test releases it. */
    private static final class BlockingSkinIO extends SkinIO {

        final CountDownLatch writeStarted = new CountDownLatch(1);
        final CountDownLatch releaseWrite = new CountDownLatch(1);

        BlockingSkinIO(Path savePath) {
            super(savePath);
        }

        @Override
        void saveSkin(UUID uuid, byte[] payload) {
            writeStarted.countDown();
            try {
                if (!releaseWrite.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test latch not released in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for test latch", e);
            }
            super.saveSkin(uuid, payload);
        }
    }
}
