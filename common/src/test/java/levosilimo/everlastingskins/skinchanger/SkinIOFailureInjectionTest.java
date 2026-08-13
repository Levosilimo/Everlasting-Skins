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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Failure-path regression tests for the SkinIO write pipeline:
 * <ul>
 *   <li>an interrupted delete propagates as {@link SkinStorage.DeleteFailedException}
 *       and the in-memory map entry is retained (no delete/resurrect desync);</li>
 *   <li>a failed async write is retried (bounded) instead of dropped, and the
 *       returned future completes only once the retried write lands;</li>
 *   <li>a sync save that exhausts the retry budget fails closed via
 *       {@link SkinStorage.SaveFailedException}.</li>
 * </ul>
 */
class SkinIOFailureInjectionTest {

    @TempDir
    Path tempDir;

    private SkinIO skinIO;

    @BeforeEach
    void setUp() {
        skinIO = new SkinIO(tempDir);
    }

    @Test
    @DisplayName("interrupt during delete: DeleteFailedException propagates and the map entry is retained")
    void interruptedDelete_failsClosedAndKeepsMapEntry() throws Exception {
        BlockingWriterSkinIO blockingIO = new BlockingWriterSkinIO(tempDir);
        SkinStorage storage = new SkinStorage(blockingIO);
        UUID u = UUID.randomUUID();
        storage.setSkin(u, skin("cGVyc2lzdGVk"));

        // Occupy the single writer thread with a blocked drain write so the
        // serialized delete task queues behind it and deleteSkin's wait blocks.
        CompletableFuture<Void> unused = storage.saveSkinAsync(u, skin("cXVldWVk"));
        assertTrue(blockingIO.writeStarted.await(5, TimeUnit.SECONDS),
                "drain must reach the file write before the delete is attempted");

        AtomicReference<Throwable> deleteFailure = new AtomicReference<>();
        Thread deleter = new Thread(() -> {
            try {
                storage.removeSkin(u);
            } catch (Throwable t) {
                deleteFailure.set(t);
            }
        }, "interrupted-delete-helper");
        deleter.start();
        try {
            Thread.sleep(300); // let the delete task queue behind the blocked drain
            deleter.interrupt();
            deleter.join(5000);
            assertFalse(deleter.isAlive(), "delete must return after the interrupt");
            assertTrue(deleter.isInterrupted(), "the interrupt flag must be re-set before propagating");

            Throwable failure = deleteFailure.get();
            assertNotNull(failure, "interrupted delete must not return normally");
            assertTrue(failure instanceof SkinStorage.DeleteFailedException,
                    "expected DeleteFailedException, got " + failure);
            assertNotNull(storage.getSkin(u),
                    "map entry must be retained when the delete cannot be confirmed");
        } finally {
            blockingIO.releaseWrite.countDown(); // never leave the shared writer blocked
        }
    }

    @Test
    @DisplayName("transient async write failure is retried: payload lands and the future completes")
    void transientAsyncFailure_isRetriedAndPersisted() throws Exception {
        FlakySkinIO flaky = new FlakySkinIO(tempDir, 1); // fail the first attempt, succeed after
        UUID u = UUID.randomUUID();
        Path target = tempDir.resolve(u + ".json");

        CompletableFuture<Void> stage = flaky.saveSkinAsync(u, skin("cmV0cnktbWU="));

        stage.get(5, TimeUnit.SECONDS); // completes only after the retried write lands
        assertTrue(Files.exists(target), "retried write must land on disk");
        CustomSkinProperty loaded = flaky.loadSkin(u);
        assertNotNull(loaded, "the retried payload must load back");
        assertEquals("cmV0cnktbWU=", loaded.getValue());
        assertTrue(flaky.attempts.get() >= 2, "the write must actually have been retried");
    }

    @Test
    @DisplayName("sync save fails closed when the write exhausts the retry budget")
    void syncSaveWithPermanentFailure_throwsSaveFailedException() {
        FlakySkinIO failing = new FlakySkinIO(tempDir, Integer.MAX_VALUE); // always fails
        SkinStorage storage = new SkinStorage(failing);
        UUID u = UUID.randomUUID();
        storage.setSkin(u, skin("ZG9vbWVk"));

        assertThrows(SkinStorage.SaveFailedException.class, () -> storage.saveSkin(u),
                "sync save must surface the persistence failure instead of swallowing it");
        assertTrue(failing.attempts.get() >= 3, "the payload must be retried up to the bounded budget");
    }

    @Test
    @DisplayName("failed async write exhausts the retry budget: future completes exceptionally")
    void asyncWritePermanentFailure_completesFutureExceptionally() throws Exception {
        FlakySkinIO failing = new FlakySkinIO(tempDir, Integer.MAX_VALUE);
        UUID u = UUID.randomUUID();

        CompletableFuture<Void> stage = failing.saveSkinAsync(u, skin("ZG9vbWVk"));

        try {
            stage.get(5, TimeUnit.SECONDS);
            fail("future must complete exceptionally when the payload is dropped");
        } catch (java.util.concurrent.ExecutionException e) {
            assertTrue(e.getCause() instanceof IOException,
                    "cause must be the write failure, got " + e.getCause());
        }
        assertFalse(Files.exists(tempDir.resolve(u + ".json")), "no payload may land on disk");
    }

    private static CustomSkinProperty skin(String value) {
        return new CustomSkinProperty(value, "sig", "src");
    }

    /**
     * SkinIO whose drain write blocks until the test releases it; used to hold
     * the shared writer thread so a delete's 5s wait is interruptible.
     */
    private static final class BlockingWriterSkinIO extends SkinIO {

        final CountDownLatch writeStarted = new CountDownLatch(1);
        final CountDownLatch releaseWrite = new CountDownLatch(1);

        BlockingWriterSkinIO(Path savePath) {
            super(savePath);
        }

        @Override
        void saveSkin(UUID uuid, byte[] payload) throws IOException {
            writeStarted.countDown();
            try {
                if (!releaseWrite.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test latch not released in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for test latch", e);
            }
            super.saveSkin(uuid, payload);
        }
    }

    /**
     * SkinIO whose write hook fails the first {@code failFirstAttempts} times
     * before delegating to the real write (fault injection for the retry path).
     */
    private static final class FlakySkinIO extends SkinIO {

        final AtomicInteger attempts = new AtomicInteger();
        private final int failFirstAttempts;

        FlakySkinIO(Path savePath, int failFirstAttempts) {
            super(savePath);
            this.failFirstAttempts = failFirstAttempts;
        }

        @Override
        void saveSkin(UUID uuid, byte[] payload) throws IOException {
            if (attempts.getAndIncrement() < failFirstAttempts) {
                throw new IOException("injected write failure for " + uuid);
            }
            super.saveSkin(uuid, payload);
        }
    }
}
