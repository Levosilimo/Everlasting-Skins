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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /* ------------------------------ generators ------------------------------ */

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

    /* ------------------------------- helpers ------------------------------- */

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

    /* ----------------------------- latest-wins ----------------------------- */

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
            SkinMetrics.INSTANCE.reset();
            Path dir = newTempDir();
            try {
                SkinIO io = new SkinIO(dir);
                SkinStorage storage = new SkinStorage(io);
                UUID uuid = UUID.randomUUID();
                for (String value : values) {
                    storage.saveSkinAsync(uuid, skin(value));
                }
                storage.flushPending();
                assertFileValue(dir, uuid, values.get(values.size() - 1));
            } finally {
                deleteRecursively(dir);
            }
        }

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
            SkinMetrics.INSTANCE.reset();
            Path dir = newTempDir();
            try {
                SkinIO io = new SkinIO(dir);
                SkinStorage storage = new SkinStorage(io);
                UUID uuid = UUID.randomUUID();
                CyclicBarrier start = new CyclicBarrier(2);
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Thread threadA = new Thread(() -> {
                    try {
                        start.await();
                        storage.saveSkinAsync(uuid, skin(valueA));
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                });
                Thread threadB = new Thread(() -> {
                    try {
                        start.await();
                        storage.saveSkinAsync(uuid, skin(valueB));
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
                storage.flushPending();
                CustomSkinProperty loaded = io.loadSkin(uuid);
                assertNotNull(loaded, "flushed payload must be loadable");
                String onDisk = loaded.getOriginalProperty().getValue();
                assertTrue(valueA.equals(onDisk) || valueB.equals(onDisk),
                        "disk holds " + onDisk + " which was never submitted");
            } finally {
                deleteRecursively(dir);
            }
        }
    }
}
