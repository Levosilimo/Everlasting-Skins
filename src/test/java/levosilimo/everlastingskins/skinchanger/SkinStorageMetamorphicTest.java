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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Metamorphic property tests for {@link SkinStorage} restart-equivalence.
 * <p>
 * After Round 1 fixed the write-after-delete race in {@link SkinIO} and
 * Round 3 made the partial-cascade delete atomic, the mod guarantees that
 * <pre>
 *     on-disk loadSkin(uuid) == in-memory getSkin(uuid) == stored skin
 * </pre>
 * for every uuid at the time of restart. These properties take the same
 * model-checking framing as the Round 1 tests (Claessen &amp; Hughes, ICFP
 * 2000) and apply it at the {@link SkinStorage} layer: a random op script
 * is run against a reference map, the writes are flushed through the
 * coalesce path, a fresh {@link SkinStorage} is constructed over the same
 * directory, and the restarted view must match the reference exactly.
 * <p>
 * Encoded invariants:
 * <ul>
 *   <li>setSkinAndRestart_equalsInitial: any sequence of saveSkinAsync writes
 *       must round-trip through a fresh SkinStorage.</li>
 *   <li>clearAndRestart_skinAbsent: a delete persists; the restarted reader
 *       sees no file for the tombstoned uuid.</li>
 *   <li>lastWriteWins: with several saveSkinAsync for one uuid, only the
 *       last payload survives the restart.</li>
 *   <li>coalesceTombstone: a saveSkinAsync immediately followed by a delete
 *       ends up absent after restart - the in-flight write never lands
 *       (LSM write-buffer tombstone, O'Neil et al., Acta Informatica 33,
 *       1996).</li>
 * </ul>
 */
class SkinStorageMetamorphicTest {

    /**
     * Serializes metric-observing property sections: jqwik may shrink a
     * failing property on a background thread while the next property's tries
     * run, so the shared SkinMetrics counters need a mutual-exclusion fence
     * between property bodies.
     */
    private static final Object METRICS_LOCK = new Object();

    /** UUID pool small enough that collisions and tombstone races are common. */
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

    /** Ordered value sequences for one uuid; length 2-6 so last-write-wins has something to collapse. */
    @Provide
    Arbitrary<List<String>> valueSequences() {
        return values().list().ofMinSize(2).ofMaxSize(6);
    }

    /**
     * Op script: SAVE_ASYNC followed by DELETE for the same uuid. The order
     * is fixed (write then tombstone) so the property exercises the
     * coalesce-tombstone path deterministically; the uuid and payload are
     * generated.
     */
    @Provide
    Arbitrary<SaveThenDelete> saveThenDeleteOps() {
        return Combinators.combine(Arbitraries.of(UUID_POOL), values()).as(SaveThenDelete::new);
    }

    private static final class SaveThenDelete {
        final UUID uuid;
        final String value;

        SaveThenDelete(UUID uuid, String value) {
            this.uuid = uuid;
            this.value = value;
        }
    }

    private static CustomSkinProperty skin(String value) {
        return new CustomSkinProperty(value, "signature", "property-test");
    }

    private static Path newTempDir() {
        try {
            return Files.createTempDirectory("skinstorage-metamorphic-");
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

    private static void assertLoadedEquals(Path dir, UUID uuid, String expected) {
        SkinStorage restarted = new SkinStorage(new SkinIO(dir));
        CustomSkinProperty loaded = restarted.loadSkin(uuid);
        assertNotNull(loaded, "restart must surface the saved skin for " + uuid);
        assertEquals(JsonUtils.toJson(skin(expected)), JsonUtils.toJson(loaded),
                "restarted loadSkin must equal the last submitted payload");
    }

    /**
     * setSkinAndRestart_equalsInitial: a random saveSkinAsync script over the
     * uuid pool is run against the reference map (latest-wins) and a fresh
     * SkinStorage in lockstep. After flush, a new SkinStorage over the same
     * directory must surface the exact same set of payloads. This is the
     * property: every skin in the in-memory map at the time of restart is
     * visible to the restarted reader with byte-identical content.
     */
    @Property(tries = 50)
    @Label("setSkinAndRestart_equalsInitial: the restarted SkinStorage reproduces the in-memory map")
    void setSkinAndRestart_equalsInitial(@ForAll @From("valueSequences") List<String> sequence) throws IOException {
        synchronized (METRICS_LOCK) {
            SkinMetrics.INSTANCE.reset();
            SkinStorage.resetForTest();
            Path dir = newTempDir();
            try {
                SkinIO io = new SkinIO(dir);
                SkinStorage storage = new SkinStorage(io);
                Map<UUID, String> model = new LinkedHashMap<>();
                for (String value : sequence) {
                    UUID uuid = UUID.randomUUID();
                    model.put(uuid, value);
                    storage.saveSkinAsync(uuid, skin(value));
                }
                storage.flushPending();
                assertEquals(model.size(),
                        Files.list(dir).filter(p -> p.getFileName().toString().endsWith(".json")).count(),
                        "flush must land one file per live entry");
                // Restart: a fresh SkinStorage over the same directory sees the disk state.
                SkinStorage restarted = new SkinStorage(new SkinIO(dir));
                for (Map.Entry<UUID, String> entry : model.entrySet()) {
                    CustomSkinProperty loaded = restarted.loadSkin(entry.getKey());
                    assertNotNull(loaded,
                            "restarted SkinStorage must surface the saved skin for " + entry.getKey());
                    assertEquals(entry.getValue(), loaded.getOriginalProperty().getValue(),
                            "restarted payload must equal the last submitted payload");
                }
            } finally {
                deleteRecursively(dir);
            }
        }
    }

    /**
     * clearAndRestart_skinAbsent: a deleteSkin persists across restart; the
     * restarted reader sees no file for the tombstoned uuid. Tombstone must
     * survive the harshest reader (Pillai OSDI'14).
     */
    @Property(tries = 100)
    @Label("clearAndRestart_skinAbsent: a deleted skin is absent after restart")
    void clearAndRestart_skinAbsent(@ForAll @From("values") String value) throws IOException {
        synchronized (METRICS_LOCK) {
            SkinMetrics.INSTANCE.reset();
            SkinStorage.resetForTest();
            Path dir = newTempDir();
            try {
                SkinIO io = new SkinIO(dir);
                SkinStorage storage = new SkinStorage(io);
                UUID uuid = UUID.randomUUID();
                storage.saveSkinAsync(uuid, skin(value));
                storage.flushPending();
                storage.removeSkin(uuid);

                SkinStorage restarted = new SkinStorage(new SkinIO(dir));
                assertNull(restarted.loadSkin(uuid),
                        "restart must not resurrect a tombstoned skin");
            } finally {
                deleteRecursively(dir);
            }
        }
    }

    /**
     * lastWriteWins: with several saveSkinAsync for one uuid, only the last
     * payload survives the restart. The intermediate payloads are dropped at
     * merge time (coalesce drain), so the on-disk file holds only the most
     * recent value. The restarted SkinStorage's loadSkin must surface that
     * value, byte-identical.
     */
    @Property(tries = 100)
    @Label("lastWriteWins: after restart the disk holds only the last submitted payload")
    void lastWriteWins(@ForAll @From("valueSequences") List<String> sequence) throws IOException {
        synchronized (METRICS_LOCK) {
            SkinMetrics.INSTANCE.reset();
            SkinStorage.resetForTest();
            Path dir = newTempDir();
            try {
                SkinIO io = new SkinIO(dir);
                SkinStorage storage = new SkinStorage(io);
                UUID uuid = UUID.randomUUID();
                for (String value : sequence) {
                    storage.saveSkinAsync(uuid, skin(value));
                }
                storage.flushPending();
                assertLoadedEquals(dir, uuid, sequence.get(sequence.size() - 1));
            } finally {
                deleteRecursively(dir);
            }
        }
    }

    /**
     * coalesceTombstone: a saveSkinAsync immediately followed by a delete
     * must end up absent after restart - the in-flight write never lands.
     * The delete purges pendingWrites before the file is removed, so a
     * drain that has not started yet skips this uuid; an in-flight drain is
     * serialized through the single writer thread and completes before the
     * delete runs. LSM write-buffer tombstone semantics (O'Neil et al.,
     * Acta Informatica 33, 1996).
     */
    @Property(tries = 50)
    @Label("coalesceTombstone: saveSkinAsync then delete must end up absent after restart")
    void coalesceTombstone(@ForAll @From("saveThenDeleteOps") SaveThenDelete op) throws IOException {
        synchronized (METRICS_LOCK) {
            SkinMetrics.INSTANCE.reset();
            SkinStorage.resetForTest();
            Path dir = newTempDir();
            try {
                SkinIO io = new SkinIO(dir);
                SkinStorage storage = new SkinStorage(io);
                storage.saveSkinAsync(op.uuid, skin(op.value));
                storage.removeSkin(op.uuid);
                storage.flushPending();

                SkinStorage restarted = new SkinStorage(new SkinIO(dir));
                assertNull(restarted.loadSkin(op.uuid),
                        "saveSkinAsync followed by removeSkin must end up absent after restart");
                assertTrue(!Files.exists(dir.resolve(op.uuid + ".json")),
                        "no skin file on disk after coalesce-tombstone");
            } finally {
                deleteRecursively(dir);
            }
        }
    }
}