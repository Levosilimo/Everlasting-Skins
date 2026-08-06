/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
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

        CompletableFuture<Void> unused = blockingStorage.saveSkinAsync(u, skin("stale"));
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

    @RepeatedTest(20)
    @DisplayName("randomized save/delete/flush sequences never resurrect a deleted file")
    void randomizedSequencesNeverResurrectDeletedFile(RepetitionInfo repetition) throws Exception {
        UUID u = UUID.randomUUID();
        Path target = tempDir.resolve(u + ".json");
        Random rnd = new Random(0x5eedL + repetition.getCurrentRepetition() * 7919L);
        String lastPayload = null;
        Op lastOp = null;

        for (int i = 0; i < 15; i++) {
            switch (rnd.nextInt(3)) {
                case 0:
                    lastPayload = "payload-" + rnd.nextInt(100000);
                    CompletableFuture<Void> unused = storage.saveSkinAsync(u, skin(lastPayload));
                    lastOp = Op.SAVE;
                    break;
                case 1:
                    storage.removeSkin(u);
                    lastOp = Op.DELETE;
                    break;
                default:
                    storage.flushPending();
            }
            Thread.sleep(rnd.nextInt(3));
        }
        storage.flushPending();

        if (lastOp == Op.DELETE) {
            assertFalse(Files.exists(target),
                    "repetition " + repetition.getCurrentRepetition() + ": stale write resurrected a deleted skin");
        } else if (lastOp == Op.SAVE) {
            assertTrue(Files.exists(target),
                    "repetition " + repetition.getCurrentRepetition() + ": last save was lost");
            String content = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
            assertTrue(content.contains(lastPayload),
                    "repetition " + repetition.getCurrentRepetition() + ": stale payload won over the last save");
        }
    }

    @Test
    @DisplayName("delete then restart: fresh storage must not reload the cleared skin")
    void restartAfterDeleteKeepsFileAbsent() throws Exception {
        storage.setSkin(uuid, skin("persisted"));
        storage.saveSkin(uuid);
        Path target = tempDir.resolve(uuid + ".json");
        assertTrue(Files.exists(target));

        storage.removeSkin(uuid);
        SkinStorage.resetForTest();
        SkinStorage restarted = new SkinStorage(skinIO);

        assertNull(restarted.getSkin(uuid));
        assertFalse(Files.exists(target));
    }

    @Test
    @DisplayName("removeSkin deletes the file before dropping the map entry")
    void removeSkinFileThenMap() throws Exception {
        storage.setSkin(uuid, skin("persisted"));
        storage.saveSkin(uuid);
        assertNotNull(storage.getSkin(uuid));

        storage.removeSkin(uuid);

        assertNull(storage.getSkin(uuid));
        assertNull(storage.getSource(uuid));
        assertTrue(storage.hasDefaultSkin(uuid));
        assertFalse(Files.exists(tempDir.resolve(uuid + ".json")));
    }

    private static CustomSkinProperty skin(String value) {
        return new CustomSkinProperty(value, "sig", "src");
    }

    private enum Op {
        SAVE, DELETE
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
