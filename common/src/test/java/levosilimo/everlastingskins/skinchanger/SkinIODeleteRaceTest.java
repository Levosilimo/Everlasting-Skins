/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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

    /**
     * P3-7 rewrite of the old {@code @RepeatedTest(20) + rnd.nextInt(3) +
     * Thread.sleep} loop: a jqwik {@code @Property} over an
     * {@code Arbitrary<List<Op>>} (SAVE/DELETE/FLUSH). The Thread.sleep
     * (the flake source lib-21 flagged) is replaced by flushPending() as the
     * deterministic barrier. Same last-op contract:
     *   lastOp==DELETE -> file absent
     *   lastOp==SAVE   -> file present with the last payload
     *
     * <p>Per-try isolation uses newTempDir()+deleteRecursively()+resetForTest()+
     * METRICS_LOCK (the established SkinIOPropertyTest pattern) instead of the
     * shared {@code @TempDir} field, because jqwik + @TempDir injection is
     * unreliable and files would accumulate across tries.
     */
    @Property(tries = 100)
    @Label("randomized save/delete/flush sequences never resurrect a deleted file")
    void randomizedSequencesNeverResurrectDeletedFile(@ForAll @From("saveDeleteFlushSequences") List<Op> ops)
            throws IOException {
        synchronized (METRICS_LOCK) {
            SkinStorage.resetForTest();
            Path dir = newTempDir();
            try {
                SkinIO io = new SkinIO(dir);
                SkinStorage isolated = new SkinStorage(io);
                UUID u = UUID.randomUUID();
                Path target = dir.resolve(u + ".json");
                OpType lastOp = null;
                String lastPayload = null;
                for (Op op : ops) {
                    switch (op.type) {
                        case SAVE:
                            lastPayload = op.payload;
                            lastOp = OpType.SAVE;
                            CompletableFuture<Void> unused = isolated.saveSkinAsync(u, skin(op.payload));
                            break;
                        case DELETE:
                            isolated.removeSkin(u);
                            lastOp = OpType.DELETE;
                            break;
                        case FLUSH:
                            isolated.flushPending(); // barrier replaces the old Thread.sleep window
                            break;
                        default:
                            throw new IllegalStateException("unknown op " + op.type);
                    }
                }
                isolated.flushPending();

                if (lastOp == OpType.DELETE) {
                    assertFalse(Files.exists(target),
                            "stale write resurrected a deleted skin");
                } else if (lastOp == OpType.SAVE) {
                    // Compare the deserialized value, NOT raw file text: Gson
                    // escapes base64 padding chars on write ("YQ==" becomes
                    // "YQ\u003d\u003d"), so a text contains() match on the
                    // payload is wrong for base64 payloads.
                    CustomSkinProperty loaded = io.loadSkin(u);
                    assertNotNull(loaded, "last save was lost: " + target);
                    assertEquals(lastPayload, loaded.getValue(),
                            "stale payload won over the last save");
                }
            } finally {
                deleteRecursively(dir);
            }
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

    private enum OpType { SAVE, DELETE, FLUSH }

    private static final class Op {
        final OpType type;
        final String payload;

        Op(OpType type, String payload) {
            this.type = type;
            this.payload = payload;
        }

        @Override
        public String toString() {
            return type + (payload == null ? "" : "(" + payload + ")");
        }
    }

    @Provide
    Arbitrary<String> payloads() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(16)
                .map(s -> Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)));
    }

    @Provide
    Arbitrary<List<Op>> saveDeleteFlushSequences() {
        return Combinators.combine(
                Arbitraries.of(OpType.SAVE, OpType.DELETE, OpType.FLUSH),
                payloads())
                .as(Op::new)
                .list().ofMinSize(1).ofMaxSize(15);
    }

    private static final Object METRICS_LOCK = new Object();

    private static Path newTempDir() {
        try {
            return Files.createTempDirectory("skiniodelete-race-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort temp cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort temp cleanup
        }
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
