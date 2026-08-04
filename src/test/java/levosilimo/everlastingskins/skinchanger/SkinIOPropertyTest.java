/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.JsonUtils;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Group;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Model-based property tests for the per-UUID skin store.
 * <p>
 * Reference model: an ordered map {@code uuid -> payload} with latest-wins
 * semantics; a delete is a tombstone that beats any earlier write and must
 * not be resurrected by a deferred drain. After {@code flushPending} the
 * disk must equal the model exactly: one {@code <uuid>.json} per live entry
 * carrying the latest payload, nothing for deleted entries. Properties apply
 * random concurrent op scripts and check the flushed disk against the model,
 * after QuickCheck (Claessen &amp; Hughes, ICFP 2000): the model is the
 * executable specification and the store is checked against it.
 * <p>
 * Tombstone semantics follow the LSM-tree write buffer (O'Neil et al., Acta
 * Informatica 33, 1996): a delete must shadow earlier records for the key,
 * including records still buffered for a deferred flush. The
 * restart-after-delete and drain-race properties encode the crash-consistency
 * framing of Pillai et al. (All File Systems Are Not Created Equal, OSDI
 * 2014): a reader sees the previous committed state or the new one, never a
 * partial record and never a resurrected tombstone.
 * <p>
 * Every property starts from a fresh temp directory and a reset
 * {@link SkinMetrics}; assertions use counter deltas rather than wall clocks
 * so the debounce timing stays deterministic under CI load.
 */
class SkinIOPropertyTest {

    /**
     * Serializes metric-observing property sections: jqwik may shrink a
     * failing property on a background thread while the next property's tries
     * run, so the shared SkinMetrics counters need a mutual-exclusion fence
     * between property bodies.
     */
    private static final Object METRICS_LOCK = new Object();

    private static final UUID[] UUID_POOL = {
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            UUID.fromString("00000000-0000-0000-0000-000000000004")
    };


    @Provide
    Arbitrary<String> values() {
        // Valid payloads: loadSkin() rejects values that are not decodable base64.
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(16)
                .map(s -> Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)));
    }

    @Provide
    Arbitrary<String> signatures() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(0).ofMaxLength(12);
    }

    @Provide
    Arbitrary<String> sources() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(8).injectNull(0.25);
    }

    /** Ordered save sequences for one uuid; length 2-8 so later values supersede earlier ones. */
    @Provide
    Arbitrary<List<String>> valueSequences() {
        return values().list().ofMinSize(2).ofMaxSize(8);
    }

    /** 100-value burst: every save must land inside the 50ms debounce window. */
    @Provide
    Arbitrary<List<String>> burstValues() {
        return values().list().ofSize(100);
    }

    /**
     * Generated script for the drain-race property: payload plus delete delay.
     * The delay range 40-60ms straddles the 50ms drain debounce, so roughly
     * half the steps issue the delete while the drain write is in flight.
     */
    @Provide
    Arbitrary<List<RaceStep>> raceScripts() {
        return Combinators.combine(
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(65536).ofMaxLength(262144)
                        .map(s -> Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8))),
                Arbitraries.integers().between(40, 60))
                .as(RaceStep::new)
                .list().ofSize(4);
    }

    private static final class RaceStep {
        final String value;
        final int delayMs;

        RaceStep(String value, int delayMs) {
            this.value = value;
            this.delayMs = delayMs;
        }
    }

    private enum OpType {
        SAVE_ASYNC, SAVE_SYNC, DELETE
    }

    private static final class Op {
        final OpType type;
        final UUID uuid;
        final String value;

        Op(OpType type, UUID uuid, String value) {
            this.type = type;
            this.uuid = uuid;
            this.value = value;
        }
    }

    /** Random op scripts over a small uuid pool so collisions and tombstones are frequent. */
    @Provide
    Arbitrary<List<Op>> opScripts() {
        return Combinators.combine(
                Arbitraries.of(OpType.values()),
                Arbitraries.of(UUID_POOL),
                values())
                .as(Op::new)
                .list().ofMinSize(1).ofMaxSize(40);
    }


    private static CustomSkinProperty skin(String value) {
        return new CustomSkinProperty(value, "signature", "property-test");
    }

    private static Path newTempDir() {
        try {
            return Files.createTempDirectory("skinio-property-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    EverlastingSkins.logger.debug("temp cleanup failed for {}", path, e);
                }
            });
        } catch (IOException e) {
            EverlastingSkins.logger.debug("temp dir walk failed for {}", dir, e);
        }
    }

    private static void assertFileValue(Path dir, UUID uuid, String expected) throws IOException {
        Path target = dir.resolve(uuid + ".json");
        assertTrue(Files.exists(target), "missing skin file: " + target);
        String content = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        assertEquals(JsonUtils.toJson(skin(expected)), content, "disk payload must be the latest submitted payload");
    }

    private static void sleepUnchecked(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    private static void applyOps(List<Op> ops, SkinIO io, Map<UUID, String> model, Object lock,
                                 AtomicReference<Throwable> failure) {
        try {
            for (Op op : ops) {
                synchronized (lock) {
                    switch (op.type) {
                        case SAVE_ASYNC:
                            model.put(op.uuid, op.value);
                            io.saveSkinAsync(op.uuid, skin(op.value));
                            break;
                        case SAVE_SYNC:
                            model.put(op.uuid, op.value);
                            io.saveSkin(op.uuid, skin(op.value));
                            break;
                        case DELETE:
                            model.remove(op.uuid);
                            io.deleteSkin(op.uuid);
                            break;
                        default:
                            throw new IllegalStateException("unknown op " + op.type);
                    }
                }
            }
        } catch (Throwable t) {
            failure.compareAndSet(null, t);
        }
    }

    private static void assertDiskMatchesModel(Path dir, Map<UUID, String> model) throws IOException {
        Map<String, String> byName = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                String name = file.getFileName().toString();
                if (name.endsWith(".tmp")) {
                    fail("stray temp file after flush: " + file);
                }
                if (!name.endsWith(".json")) {
                    fail("unexpected file in storage dir: " + file);
                }
                byName.put(name.substring(0, name.length() - 5),
                        new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            }
        }
        assertEquals(model.size(), byName.size(),
                "disk file count must match the model");
        for (Map.Entry<UUID, String> entry : model.entrySet()) {
            String content = byName.get(entry.getKey().toString());
            assertTrue(content != null, "model uuid missing on disk: " + entry.getKey());
            assertEquals(JsonUtils.toJson(skin(entry.getValue())), content,
                    "disk payload must match the model for " + entry.getKey());
        }
    }


    @Group
    class DrainIdempotence {

        /**
         * Model: the drain latch and per-UUID coalescing guarantee at most one
         * real disk write per debounce window, so a 100-save burst inside one
         * window yields exactly one write, 100 recorded submissions, and the
         * last payload on disk. Asserted on the SkinMetrics real-write counter
         * (not wall clocks) so the debounce timing is CI-tolerant.
         */
        @Property(tries = 100)
        @Label("coalescing bound: one debounce window collapses 100 saves into exactly one disk write")
        void drainIdempotence(@ForAll @From("burstValues") List<String> burst) throws IOException {
        synchronized (METRICS_LOCK) {
                SkinMetrics.INSTANCE.reset();
                Path dir = newTempDir();
                try {
                    SkinIO io = new SkinIO(dir);
                    UUID uuid = UUID.randomUUID();
                    for (String value : burst) {
                        io.saveSkinAsync(uuid, skin(value));
                    }
                    io.flushPending();
                    assertEquals(1, SkinMetrics.INSTANCE.snapshot().realWrites(),
                            "100 saves in one debounce window must produce exactly one disk write");
                    assertEquals(burst.size(), SkinMetrics.INSTANCE.snapshot().savesSubmitted(),
                            "every merge must be recorded as submitted");
                    assertFileValue(dir, uuid, burst.get(burst.size() - 1));
                } finally {
                    deleteRecursively(dir);
                }
        
        }}
    }


    @Group
    class DeleteBeatsWrite {

        /**
         * Model: a delete is a tombstone that beats any earlier write, so a
         * deferred async payload purged by the delete must never reach disk,
         * even after a subsequent flush. LSM write-buffer tombstone semantics
         * (O'Neil et al., Acta Informatica 33, 1996).
         */
        @Property(tries = 100)
        @Label("delete beats write: a deferred async payload cannot resurrect the file")
        void deleteBeatsWrite(@ForAll @From("values") String value) throws IOException {
        synchronized (METRICS_LOCK) {
                SkinMetrics.INSTANCE.reset();
                Path dir = newTempDir();
                try {
                    SkinIO io = new SkinIO(dir);
                    UUID uuid = UUID.randomUUID();
                    io.saveSkinAsync(uuid, skin(value));
                    io.deleteSkin(uuid);
                    io.flushPending();
                    assertFalse(Files.exists(dir.resolve(uuid + ".json")),
                            "deleted skin must stay deleted after flush");
                } finally {
                    deleteRecursively(dir);
                }
        
        }}

        /**
         * Model: the delete is serialized through the single writer thread, so
         * it cannot be interleaved between an in-flight drain's write and its
         * atomic rename; the file must be absent once the delete returns. The
         * drain fires 50ms after the save, so each step issues the delete with
         * a generated 40-60ms delay straddling that window. The race window
         * cannot be latched on the 1.21 API (the drain write is private, with
         * no test seam), so the property keeps generated sleeps as the probe;
         * 12 tries x 4 steps stays under the 5s flake budget while the
         * serialized delete keeps the outcome deterministic.
         * Crash-consistency framing per Pillai et al. (OSDI 2014): no
         * write-after-delete resurrection. The delay is generated data, so a
         * failing script shrinks to a minimal reproduction.
         */
        @Property(tries = 12)
        @Label("delete beats an in-flight drain: no write-after-delete resurrection")
        void deleteBeatsInFlightDrain(@ForAll @From("raceScripts") List<RaceStep> steps) throws IOException {
        synchronized (METRICS_LOCK) {
                SkinMetrics.INSTANCE.reset();
                Path dir = newTempDir();
                try {
                    SkinIO io = new SkinIO(dir);
                    for (RaceStep step : steps) {
                        UUID uuid = UUID.randomUUID();
                        io.saveSkinAsync(uuid, skin(step.value));
                        sleepUnchecked(step.delayMs);
                        io.deleteSkin(uuid);
                        io.flushPending();
                        Path target = dir.resolve(uuid + ".json");
                        assertFalse(Files.exists(target), () -> "write-after-delete: " + target
                                + " resurrected by a concurrent drain, content="
                                + readIfPresent(target));
                    }
                } finally {
                    deleteRecursively(dir);
                }
        
        }}

        private static String readIfPresent(Path target) {
            try {
                return Files.exists(target)
                        ? new String(Files.readAllBytes(target), StandardCharsets.UTF_8)
                        : "<absent>";
            } catch (IOException e) {
                return "<unreadable>";
            }
        }
    }


    @Group
    class SerializeLoadRoundTrip {

        /**
         * Model: the wire format is the state, so serialize -> load ->
         * re-serialize must be byte-identical and the file bytes must equal
         * the serialized payload. This is the crash-consistency baseline of
         * Pillai et al. (OSDI 2014): whatever a reader sees is either the
         * previous committed bytes or the new committed bytes, never a
         * partially written record.
         */
        @Property(tries = 200)
        @Label("serialize-load round-trip is byte-identical")
        void roundTripBytes(@ForAll @From("values") String value,
                            @ForAll @From("signatures") String signature,
                            @ForAll @From("sources") String source) throws IOException {
        synchronized (METRICS_LOCK) {
                SkinMetrics.INSTANCE.reset();
                Path dir = newTempDir();
                try {
                    SkinIO io = new SkinIO(dir);
                    SkinStorage storage = new SkinStorage(io);
                    UUID uuid = UUID.randomUUID();
                    CustomSkinProperty skin = new CustomSkinProperty(value, signature, source);
                    io.saveSkin(uuid, skin);
                    String serialized = JsonUtils.toJson(skin);
                    String onDisk = new String(Files.readAllBytes(dir.resolve(uuid + ".json")), StandardCharsets.UTF_8);
                    assertEquals(serialized, onDisk, "file bytes must equal the serialized payload");
                    CustomSkinProperty loaded = storage.loadSkin(uuid);
                    assertNotNull(loaded, "a valid payload must load");
                    assertEquals(serialized, JsonUtils.toJson(loaded),
                            "re-serialized payload must be byte-identical to the original");
                    assertEquals(value, loaded.getOriginalProperty().value());
                    assertEquals(signature, loaded.getOriginalProperty().signature());
                    assertEquals(source, loaded.getSource());
                } finally {
                    deleteRecursively(dir);
                }
        
        }}
    }


    @Group
    class RestartAfterDelete {

        /**
         * Model: a tombstone must survive a restart, so a fresh store over the
         * same directory must see no file for the deleted uuid. Encodes the
         * crash-consistency framing of Pillai et al. (OSDI 2014): restart is
         * the harshest reader, and it must never observe a resurrected
         * tombstone.
         */
        @Property(tries = 100)
        @Label("restart after delete: a fresh store sees no resurrected skin")
        void restartAfterDelete(@ForAll @From("values") String value) throws IOException {
        synchronized (METRICS_LOCK) {
                SkinMetrics.INSTANCE.reset();
                Path dir = newTempDir();
                try {
                    SkinIO io = new SkinIO(dir);
                    UUID uuid = UUID.randomUUID();
                    io.saveSkinAsync(uuid, skin(value));
                    io.deleteSkin(uuid);
                    io.flushPending();
                    SkinStorage restarted = new SkinStorage(new SkinIO(dir));
                    assertTrue(restarted.loadSkin(uuid) == null,
                            "restart must not resurrect a deleted skin");
                } finally {
                    deleteRecursively(dir);
                }
        
        }}
    }


    @Group
    class ModelEquivalence {

        /**
         * Model: the full specification. A random concurrent op script (async
         * save, sync save, delete over a small uuid pool) is applied against
         * the reference map under a shared lock that fixes submission order;
         * the flush must reproduce the model exactly - one file per live
         * entry carrying the latest payload, no file for deleted entries, no
         * stray temp files. The script runs on two threads so the deferred
         * drain interleaves with later ops (QuickCheck-style model checking,
         * Claessen &amp; Hughes, ICFP 2000).
         */
        @Property(tries = 100)
        @Label("model equivalence: flushed disk equals the reference model for any op script")
        void modelEquivalence(@ForAll @From("opScripts") List<Op> script)
                throws InterruptedException, IOException {
        synchronized (METRICS_LOCK) {
                SkinMetrics.INSTANCE.reset();
                Path dir = newTempDir();
                try {
                    SkinIO io = new SkinIO(dir);
                    Map<UUID, String> model = new LinkedHashMap<>();
                    Object lock = new Object();
                    AtomicReference<Throwable> failure = new AtomicReference<>();
                    int mid = script.size() / 2;
                    Thread left = new Thread(() -> applyOps(script.subList(0, mid), io, model, lock, failure));
                    Thread right = new Thread(() -> applyOps(script.subList(mid, script.size()), io, model, lock, failure));
                    left.start();
                    right.start();
                    left.join();
                    right.join();
                    if (failure.get() != null) {
                        throw new AssertionError("op application failed", failure.get());
                    }
                    io.flushPending();
                    assertDiskMatchesModel(dir, model);
                } finally {
                    deleteRecursively(dir);
                }
        
        }}
    }


    @Group
    class LatestWins {

        /**
         * Model: the map is latest-wins, so a sequence of async saves for one
         * uuid must leave exactly the last submitted payload on disk after
         * flush. Intermediate payloads are dropped at merge time.
         */
        @Property(tries = 100)
        @Label("latest-wins: after flush the disk holds the last submitted payload")
        void latestWins(@ForAll @From("valueSequences") List<String> values) throws IOException {
        synchronized (METRICS_LOCK) {
                SkinMetrics.INSTANCE.reset();
                Path dir = newTempDir();
                try {
                    SkinIO io = new SkinIO(dir);
                    UUID uuid = UUID.randomUUID();
                    for (String value : values) {
                        io.saveSkinAsync(uuid, skin(value));
                    }
                    io.flushPending();
                    assertFileValue(dir, uuid, values.get(values.size() - 1));
                } finally {
                    deleteRecursively(dir);
                }
        
        }}

        /**
         * Model: two concurrent submits race for the same key; the flushed
         * disk must hold one of the submitted payloads in full, never a mix
         * or a partial record. A CyclicBarrier lets both threads enter the
         * memtable at the same instant, so tries explore put orderings.
         */
        @Property(tries = 100)
        @Label("concurrent saves: flushed disk holds one submitted payload, never a mix")
        void concurrentLatestWins(@ForAll @From("values") String valueA, @ForAll @From("values") String valueB)
                throws InterruptedException, IOException {
        synchronized (METRICS_LOCK) {
                SkinMetrics.INSTANCE.reset();
                Path dir = newTempDir();
                try {
                    SkinIO io = new SkinIO(dir);
                    UUID uuid = UUID.randomUUID();
                    CyclicBarrier start = new CyclicBarrier(2);
                    AtomicReference<Throwable> failure = new AtomicReference<>();
                    Thread threadA = new Thread(() -> {
                        try {
                            start.await();
                            io.saveSkinAsync(uuid, skin(valueA));
                        } catch (Throwable t) {
                            failure.compareAndSet(null, t);
                        }
                    });
                    Thread threadB = new Thread(() -> {
                        try {
                            start.await();
                            io.saveSkinAsync(uuid, skin(valueB));
                        } catch (Throwable t) {
                            failure.compareAndSet(null, t);
                        }
                    });
                    threadA.start();
                    threadB.start();
                    threadA.join();
                    threadB.join();
                    if (failure.get() != null) {
                        throw new AssertionError("concurrent save failed", failure.get());
                    }
                    io.flushPending();
                    CustomSkinProperty loaded = io.loadSkin(uuid);
                    assertNotNull(loaded, "flushed payload must be loadable");
                    String onDisk = loaded.getOriginalProperty().value();
                    assertTrue(valueA.equals(onDisk) || valueB.equals(onDisk),
                            "disk holds " + onDisk + " which was never submitted");
                } finally {
                    deleteRecursively(dir);
                }
        
        }}
    }
}
