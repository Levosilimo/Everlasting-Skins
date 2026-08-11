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

    /**
     * Await window for the cross-thread latches. The drain-coalesce writer is
     * a lazily-created static thread shared across all tests, so under CI load
     * the 50ms debounce + queue position can take far longer than a fixed 5s
     * window (the lib-58 flake). Once the awaited step fires the test is
     * deterministic, so a generous window costs nothing.
     */
    private static final long GENEROUS_AWAIT_SECONDS = 30;

    @TempDir
    Path tempDir;

    private SkinStorage storage;

    @BeforeEach
    void setUp() {
        SkinStorage.resetForTest();
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

        CustomSkinProperty stale = skin("YQ==");
        blockingStorage.setSkin(u, skin("Yg=="));
        blockingStorage.saveSkinAsync(u, stale);
        // Settle barrier (mirrors the #354 terminal-metric pattern): the drain
        // runs on the lazily-created static writer thread after the 50ms
        // debounce, so on a loaded CI box it can take far longer than 5s to
        // reach the write. Await generously — once writeStarted fires the
        // drain is deterministically blocked holding the single writer thread
        // and every subsequent step is race-free.
        //
        // The one-shot drainScheduled flag is suite-wide static while the
        // pending map is per-instance, so a suite-mate's in-flight drain can
        // consume the flag and strand this payload with no drain ever
        // scheduled — the debounced write never starts (the lib-58
        // lost-drain race, run 31508299301). Poll the real condition instead
        // of racing the debounce: re-arm the drain with an idempotent
        // saveSkinAsync (same payload — the merge is a no-op supersede; a
        // no-op while the original drain is merely delayed, since the flag is
        // still set) until writeStarted fires or the generous deadline passes.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(GENEROUS_AWAIT_SECONDS);
        while (!blockingIO.writeStarted.await(100, TimeUnit.MILLISECONDS)) {
            if (System.nanoTime() > deadline) {
                fail("drain must reach the file write before the sync save (writeStarted never fired within "
                        + GENEROUS_AWAIT_SECONDS + "s; stillQueued=" + blockingStorage.hasPendingWrites() + ")");
            }
            blockingStorage.saveSkinAsync(u, stale); // re-arm: re-schedules the drain
        }

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
            // Non-race assertion: the sync save is submitted to the same single
            // writer thread the drain is blocked on, so it cannot complete while
            // the drain holds the thread. The 2s probe is a deterministic
            // negative check, not a timing bet — a regression (direct write on
            // the caller thread) completes instantly and fails here.
            assertFalse(completedWhileDrainBlocked,
                    "sync save must be FIFO-ordered behind the in-flight drain write");
            blockingIO.releaseWrite.countDown();
            sync.get(GENEROUS_AWAIT_SECONDS, TimeUnit.SECONDS);
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
                if (!releaseWrite.await(GENEROUS_AWAIT_SECONDS, TimeUnit.SECONDS)) {
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
