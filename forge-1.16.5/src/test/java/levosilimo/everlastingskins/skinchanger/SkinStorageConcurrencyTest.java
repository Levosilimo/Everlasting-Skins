/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Concurrent cache-access test for {@link SkinStorage}.
 *
 * <p>Hand-rolled JUnit port of the P3-7 jqwik property: jqwik needs Java 17,
 * this lane is Java 8, so the {@code @Property} becomes a
 * {@code @RepeatedTest} and the property body runs 10 times. {@code N}
 * worker threads hammer the (static) {@code skinMap} plus the shared
 * {@link SkinIO} coalescing writer concurrently. Each worker owns a disjoint
 * slice of the UUID pool, so the per-UUID "last write wins" model is
 * deterministic: after all workers join and {@code flushPending()} drains the
 * writer, {@code getSkin} must return exactly the last value that worker set
 * for that UUID (or null/absent if the worker's last op was a remove) — never
 * an older overwritten value, and never a value from another UUID.
 *
 * <p>Deadlock safety: every worker future is awaited with a timeout, so a real
 * deadlock in SkinStorage/SkinIO fails the test with TimeoutException instead
 * of hanging the suite. No-exception invariant: any worker-thrown Throwable is
 * collected and rethrown after join, and the future.get() propagates it.
 *
 * <p>Static state: {@code SkinStorage.resetForTest()} +
 * {@code SkinMetrics.INSTANCE.reset()} run at the start of every repetition
 * and the UUID pool is regenerated per repetition, so the static map never
 * bleeds across repetitions or test classes (repetitions of a
 * {@code @RepeatedTest} run sequentially on the same thread, so no extra
 * synchronization fence is needed — unlike jqwik's concurrent shrink runs).
 */
class SkinStorageConcurrencyTest {

    private static final int WORKERS = 8;
    private static final int OPS_PER_WORKER = 50;
    private static final int UUIDS_PER_WORKER = 3;
    private static final long AWAIT_SECONDS = 10L;

    @RepeatedTest(10)
    @DisplayName("concurrent set/get/remove/hasDefault/save/flush: no exceptions, no lost updates")
    void concurrentAccessNeverLosesLastWrite(RepetitionInfo info) throws Exception {
        SkinMetrics.INSTANCE.reset();
        SkinStorage.resetForTest();
        // Unique payload per repetition so disk reloads never alias across runs.
        String payload = Base64.getEncoder().encodeToString(
                ("p3-10-" + info.getCurrentRepetition()).getBytes(StandardCharsets.UTF_8));
        Path dir = newTempDir();
        try {
            SkinStorage storage = new SkinStorage(new SkinIO(dir));
            UUID[] uuids = new UUID[WORKERS * UUIDS_PER_WORKER];
            for (int i = 0; i < uuids.length; i++) {
                uuids[i] = UUID.randomUUID();
            }
            // Model: the last skin the owning worker set for each UUID.
            Map<UUID, CustomSkinProperty> expected = new ConcurrentHashMap<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
            CyclicBarrier start = new CyclicBarrier(WORKERS);
            try {
                Future<?>[] futures = new Future<?>[WORKERS];
                for (int w = 0; w < WORKERS; w++) {
                    final int workerId = w;
                    futures[w] = pool.submit(() ->
                            runWorker(workerId, uuids, payload, storage, expected, start, failure));
                }
                for (Future<?> f : futures) {
                    // Timeout => real deadlock in the store under test.
                    f.get(AWAIT_SECONDS, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }
            if (failure.get() != null) {
                throw new AssertionError("worker threw", failure.get());
            }
            // Deterministic barrier: drains every queued async write. Never Thread.sleep.
            storage.flushPending();
            assertModel(storage, uuids, expected);
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void runWorker(int workerId, UUID[] uuids, String payload,
                                  SkinStorage storage, Map<UUID, CustomSkinProperty> expected,
                                  CyclicBarrier start, AtomicReference<Throwable> failure) {
        try {
            start.await(); // release all workers at the same instant for genuine contention
            Random rnd = new Random(0xdeadbeefL + workerId * 104729L);
            for (int i = 0; i < OPS_PER_WORKER; i++) {
                UUID uuid = uuids[workerId * UUIDS_PER_WORKER + (i % UUIDS_PER_WORKER)];
                switch (rnd.nextInt(6)) {
                    case 0: // setSkin
                    case 1:
                        CustomSkinProperty skin = skin(payload, workerId, i);
                        storage.setSkin(uuid, skin);
                        expected.put(uuid, skin);
                        break;
                    case 2: // getSkin (harmless read, exercises map/disk read)
                        storage.getSkin(uuid);
                        break;
                    case 3: // removeSkin
                        storage.removeSkin(uuid);
                        expected.remove(uuid);
                        break;
                    case 4: // hasDefaultSkin
                        storage.hasDefaultSkin(uuid);
                        break;
                    case 5: // saveSkinAsync (coalescing async write of the current skin)
                        // Persist only live entries: after a remove the model has
                        // nothing to write, and a stray disk file would resurrect
                        // the uuid on the next getSkin map miss.
                        CustomSkinProperty current = expected.get(uuid);
                        if (current != null) {
                            storage.saveSkinAsync(uuid, current);
                        }
                        break;
                    default:
                        throw new IllegalStateException("unknown op");
                }
            }
        } catch (Throwable t) {
            failure.compareAndSet(null, t);
        }
    }

    /** For every pool UUID: live => getSkin equals the owning worker's last set; else absent. */
    private static void assertModel(SkinStorage storage, UUID[] uuids,
                                    Map<UUID, CustomSkinProperty> expected) {
        for (UUID uuid : uuids) {
            CustomSkinProperty want = expected.get(uuid);
            CustomSkinProperty got = storage.getSkin(uuid);
            if (want == null) {
                // Removed / never-set: getSkin must be absent (map miss + no disk file).
                assertNull(got, "deleted/never-set uuid resurrected: " + uuid);
            } else {
                assertNotNull(got, "no skin for live uuid " + uuid);
                // Compare the payload, NOT equals(): CustomSkinProperty.equals is source-only.
                assertEquals(want.getValue(), got.getValue(), "lost update for " + uuid);
            }
        }
    }

    private static CustomSkinProperty skin(String payload, int worker, int i) {
        String value = Base64.getEncoder().encodeToString(
                (payload + "-" + worker + "-" + i).getBytes(StandardCharsets.UTF_8));
        return new CustomSkinProperty(value, "sig", "concurrency-test");
    }

    private static Path newTempDir() {
        try {
            return Files.createTempDirectory("skinstorage-concurrency-");
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
}
