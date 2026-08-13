/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

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
 * Concurrent cache-access property test for {@link SkinStorage}.
 *
 * <p>N worker threads hammer the (static) {@code skinMap} plus the shared
 * {@link SkinIO} coalescing writer concurrently. Each worker owns a disjoint
 * slice of the UUID pool, so the per-UUID "last write wins" model is
 * deterministic: after all workers join and {@code flushPending()} drains the
 * writer, {@code getSkin} must return exactly the last value that worker set
 * for that UUID (or null/absent if the worker's last op was a remove) — never
 * an older overwritten value, and never a value from another UUID.
 *
 * <p>Deadlock safety: every worker future is awaited with a timeout, so a real
 * deadlock in SkinStorage/SkinIO fails the try with TimeoutException instead
 * of hanging the suite. No-exception invariant: any worker-thrown Throwable is
 * collected and rethrown after join, and the future.get() propagates it.
 *
 * <p>The whole body is synchronized on METRICS_LOCK (jqwik may re-run/shrink a
 * failing property on a background thread concurrently with other tries) and
 * calls SkinStorage.resetForTest() + SkinMetrics.INSTANCE.reset() per try so
 * the static map never bleeds across tries or test classes.
 */
class SkinStorageConcurrencyTest {

    /** Fences property bodies against jqwik's concurrent shrink re-runs. */
    private static final Object METRICS_LOCK = new Object();

    private static final int WORKERS = 8;
    private static final int OPS_PER_WORKER = 50;
    private static final long AWAIT_SECONDS = 10L;

    /** Base64 payloads so any disk reload path (getSkin on a map miss) stays loadable. */
    @Provide
    Arbitrary<String> payloads() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(16)
                .map(s -> Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)));
    }

    /** How many UUIDs each worker owns; a worker's slice is workerId * slice .. +slice. */
    @Provide
    Arbitrary<Integer> uuidsPerWorker() {
        return Arbitraries.integers().between(1, 4);
    }

    @Property(tries = 25)
    @Label("concurrent set/get/remove/hasDefault/save/flush: no exceptions, no lost updates")
    void concurrentAccessNeverLosesLastWrite(@ForAll @From("payloads") String payload,
                                             @ForAll @From("uuidsPerWorker") int slice)
            throws Exception {
        synchronized (METRICS_LOCK) {
            SkinMetrics.INSTANCE.reset();
            SkinStorage.resetForTest();
            Path dir = newTempDir();
            try {
                SkinStorage storage = new SkinStorage(new SkinIO(dir));
                UUID[] uuids = new UUID[WORKERS * slice];
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
                                runWorker(workerId, slice, uuids, payload, storage, expected, start, failure));
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
                storage.flushPending();
                assertModel(storage, uuids, expected);
            } finally {
                deleteRecursively(dir);
            }
        }
    }

    private static void runWorker(int workerId, int slice, UUID[] uuids, String payload,
                                  SkinStorage storage, Map<UUID, CustomSkinProperty> expected,
                                  CyclicBarrier start, AtomicReference<Throwable> failure) {
        try {
            start.await(); // release all workers at the same instant for genuine contention
            Random rnd = new Random(0xdeadbeefL + workerId * 104729L);
            for (int i = 0; i < OPS_PER_WORKER; i++) {
                UUID uuid = uuids[workerId * slice + (i % slice)];
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
                    case 5: // saveSkinAsync (coalescing async write)
                        // setSkin first keeps the map and the model warm: a bare
                        // async save would leave a map miss + on-disk hit, which
                        // getSkin() would reload and assertModel would misread as
                        // a "resurrected" uuid. The async write itself still lands
                        // on the shared writer thread after flushPending().
                        CustomSkinProperty asyncSkin = skin(payload, workerId, i);
                        storage.setSkin(uuid, asyncSkin);
                        expected.put(uuid, asyncSkin);
                        storage.saveSkinAsync(uuid, asyncSkin);
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
                // Compare the payload, not equals(): the value is the observable
                // update; equals() would add signature/source noise.
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
