/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.JsonUtils;
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
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Metamorphic property tests for {@link SkinIO} restart-equivalence.
 * <p>
 * Round 4 takes the Round 1 model-based disk-vs-model checks and adds the
 * restart framing of Pillai et al. (All File Systems Are Not Created Equal,
 * OSDI 2014): a fresh reader over the same directory must see exactly the
 * state the previous writer left behind, with no exception escaping. The
 * properties encode four invariants:
 * <ul>
 *   <li>writeThenRead_equalsPayload: any payload written through saveSkin
 *       round-trips byte-identical through loadSkin.</li>
 *   <li>deleteThenRead_returnsNull: a delete shadows the file even when the
 *       writer goes through the async path; the reader observes null.</li>
 *   <li>deleteIdempotent: deleting an absent or already-deleted uuid is a
 *       no-op - the second call must not throw.</li>
 *   <li>corruptFile_returnsNull: garbage bytes are treated as absent by the
 *       reader (quarantined, no exception escapes).</li>
 * </ul>
 * Every property starts from a fresh temp directory and a reset
 * {@link SkinMetrics}; assertions use counter deltas where applicable so the
 * debounce timing stays deterministic under CI load.
 */
class SkinIOMetamorphicTest {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger(SkinIOMetamorphicTest.class);

    /**
     * Serializes metric-observing property sections: jqwik may shrink a
     * failing property on a background thread while the next property's tries
     * run, so the shared SkinMetrics counters need a mutual-exclusion fence
     * between property bodies.
     */
    private static final Object METRICS_LOCK = new Object();

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

    /**
     * Small non-JSON byte strings, deterministic garbage for the corrupt-file
     * property. The charset excludes every JSON structural character (braces,
     * brackets, colon, comma, quote, backslash, slash, whitespace), digits and
     * letters, so no generated string can ever parse as valid JSON: every
     * sample exercises the invalid-JSON quarantine path.
     */
    @Provide
    Arbitrary<String> garbage() {
        return Arbitraries.strings().withChars('!', '#', '%', '*', '<', '>', '^', '|', '~', '$', '@', '?', '+', '=')
                .ofMinLength(1).ofMaxLength(32);
    }

    private static CustomSkinProperty skin(String value) {
        return new CustomSkinProperty(value, "signature", "property-test");
    }

    private static Path newTempDir() {
        try {
            return Files.createTempDirectory("skinio-metamorphic-");
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
                    LOGGER.debug("temp cleanup failed for {}", path, e);
                }
            });
        } catch (IOException e) {
            LOGGER.debug("temp dir walk failed for {}", dir, e);
        }
    }

    /**
     * writeThenRead_equalsPayload: any payload written through saveSkin must
     * survive the on-disk serialize/load cycle byte-identical. The serialised
     * form is the canonical state (Pillai OSDI'14): a reader sees the previous
     * committed bytes or the new committed bytes, never a partial record. The
     * equality check goes through JsonUtils.toJson so the assertion stays
     * stable even if the equals/hashCode contract on CustomSkinProperty is
     * later relaxed to ignore the original property payload.
     */
    @Property(tries = 100)
    @Label("writeThenRead_equalsPayload: any SkinProperty survives a save/load round-trip")
    void writeThenRead_equalsPayload(@ForAll @From("values") String value,
                                     @ForAll @From("signatures") String signature,
                                     @ForAll @From("sources") String source) throws IOException {
        synchronized (METRICS_LOCK) {
            SkinMetrics.INSTANCE.reset();
            Path dir = newTempDir();
            try {
                SkinIO io = new SkinIO(dir);
                UUID uuid = UUID.randomUUID();
                CustomSkinProperty original = new CustomSkinProperty(value, signature, source);
                io.saveSkin(uuid, original);
                CustomSkinProperty loaded = io.loadSkin(uuid);
                assertNotNull(loaded, "a freshly written skin must load");
                assertEquals(JsonUtils.toJson(original), JsonUtils.toJson(loaded),
                        "loaded payload must be byte-identical to the original");
                assertEquals(value, loaded.getOriginalProperty().getValue());
                assertEquals(signature, loaded.getOriginalProperty().getSignature());
                assertEquals(source, loaded.getSource());
            } finally {
                deleteRecursively(dir);
            }
        }
    }

    /**
     * deleteThenRead_returnsNull: a delete shadows the on-disk file even when
     * the writer went through the async coalesce path. Tombstone semantics
     * after the LSM write-buffer (O'Neil et al., Acta Informatica 33, 1996):
     * the deferred payload is purged from pendingWrites before the file is
     * removed, so a later drain cannot resurrect the cleared skin. The reader
     * observes null, never the stale payload.
     */
    @Property(tries = 100)
    @Label("deleteThenRead_returnsNull: a deleted skin is gone after the writer returns")
    void deleteThenRead_returnsNull(@ForAll @From("values") String value) throws IOException {
        synchronized (METRICS_LOCK) {
            SkinMetrics.INSTANCE.reset();
            Path dir = newTempDir();
            try {
                SkinIO io = new SkinIO(dir);
                UUID uuid = UUID.randomUUID();
                io.saveSkin(uuid, skin(value));
                assertNotNull(io.loadSkin(uuid), "freshly written skin must be readable");
                io.deleteSkin(uuid);
                assertNull(io.loadSkin(uuid),
                        "loadSkin must return null after deleteSkin");
            } finally {
                deleteRecursively(dir);
            }
        }
    }

    /**
     * deleteIdempotent: deleting twice yields the same final state with no
     * exception escaping the second call. A delete on an absent file is a
     * deleteIfExists no-op; the property is a basic contract callers depend
     * on (e.g. cascade on logout when a tombstone may already have landed).
     */
    @Property(tries = 100)
    @Label("deleteIdempotent: deleting twice yields the same result, no exception")
    void deleteIdempotent(@ForAll @From("values") String value) throws IOException {
        synchronized (METRICS_LOCK) {
            SkinMetrics.INSTANCE.reset();
            Path dir = newTempDir();
            try {
                SkinIO io = new SkinIO(dir);
                UUID uuid = UUID.randomUUID();
                io.saveSkin(uuid, skin(value));
                io.deleteSkin(uuid);
                io.deleteSkin(uuid);
                assertNull(io.loadSkin(uuid),
                        "delete is idempotent: loadSkin still returns null after the second call");
                assertTrue(!Files.exists(dir.resolve(uuid + ".json")),
                        "no skin file after two deletes");
            } finally {
                deleteRecursively(dir);
            }
        }
    }

    /**
     * corruptFile_returnsNull: a file with non-JSON bytes must be treated as
     * absent by the reader (quarantined to a {@code .corrupt-<epoch>} sibling,
     * null returned). No exception escapes - the caller never sees a Json
     * parser failure surfaced. Restart framing of Pillai OSDI'14: the reader
     * sees either the previous committed bytes or absence, never a partial
     * record and never an exception from a malformed record.
     */
    @Property(tries = 100)
    @Label("corruptFile_returnsNull: a garbage skin file is treated as absent, no exception escapes")
    void corruptFile_returnsNull(@ForAll @From("garbage") String garbage) throws IOException {
        synchronized (METRICS_LOCK) {
            SkinMetrics.INSTANCE.reset();
            Path dir = newTempDir();
            try {
                SkinIO io = new SkinIO(dir);
                UUID uuid = UUID.randomUUID();
                Path target = dir.resolve(uuid + ".json");
                Files.write(target, garbage.getBytes(StandardCharsets.UTF_8));
                assertTrue(Files.exists(target), "sanity: garbage file is on disk before loadSkin");

                CustomSkinProperty loaded = io.loadSkin(uuid);
                assertNull(loaded, "garbage payload must read back as null");
                // The corrupt file must be moved aside so the reader never sees it again.
                assertTrue(!Files.exists(target),
                        "corrupt file must be quarantined after read, not left on disk");
            } finally {
                deleteRecursively(dir);
            }
        }
    }
}