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
import org.junit.jupiter.api.io.TempDir;

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
 * Write-after-delete regression tests for the coalescing async writer:
 * a drain that already pulled a payload must land its write BEFORE a
 * delete of the same skin, never after it (resurrecting the file).
 */
class SkinIODeleteRaceTest {

    @TempDir
    Path tempDir;

    private SkinIO skinIO;
    private SkinStorage storage;
    private UUID uuid;

    @BeforeEach
    void setUp() {
        skinIO = new SkinIO(tempDir);
        storage = new SkinStorage(skinIO);
        uuid = UUID.randomUUID();
    }

    @Test
    @DisplayName("delete serialized after an in-flight drain write: no write-after-delete resurrection")
    void deleteSerializedAgainstInFlightDrain() throws Exception {
        BlockingSkinIO blockingIO = new BlockingSkinIO(tempDir);
        SkinStorage blockingStorage = new SkinStorage(blockingIO);
        UUID u = UUID.randomUUID();
        Path target = tempDir.resolve(u + ".json");

        blockingStorage.saveSkinAsync(u, skin("stale"));
        assertTrue(blockingIO.writeStarted.await(5, TimeUnit.SECONDS),
                "drain must reach the file write before the delete");

        CountDownLatch removeDone = new CountDownLatch(1);
        ExecutorService remover = Executors.newSingleThreadExecutor();
        try {
            Future<?> delete = remover.submit(() -> {
                blockingStorage.removeSkin(u);
                removeDone.countDown();
            });
            // Unfixed code returns here immediately: the delete runs on the
            // remover thread while the drain is still blocked, so the drain's
            // write lands after the delete and resurrects the file. Fixed code
            // blocks until the serialized delete has run on the writer thread,
            // which can only happen after the blocked drain write completes.
            boolean removedWhileDrainBlocked = removeDone.await(2, TimeUnit.SECONDS);
            blockingIO.releaseWrite.countDown();
            delete.get(5, TimeUnit.SECONDS);
            // Barrier on the writer thread: the drain write is async, so wait
            // for it to land before asserting on the file state.
            blockingStorage.flushPending();

            assertFalse(Files.exists(target),
                    "stale drain write resurrected the deleted file (removedWhileDrainBlocked="
                            + removedWhileDrainBlocked + ")");
        } finally {
            remover.shutdownNow();
        }
    }

    private static CustomSkinProperty skin(String value) {
        return new CustomSkinProperty(value, "sig", "src");
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
