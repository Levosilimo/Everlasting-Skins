/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.metrics.Snapshot;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SkinStorageTest {
    private static final String TEST_DEFAULT_VALUE = "testDefaultSkinValue";

    @TempDir
    Path tempDir;

    private SkinIO skinIO;
    private SkinStorage storage;
    private UUID uuid;

    @BeforeEach
    void setUp() {
        skinIO = new SkinIO(tempDir);
        storage = new SkinStorage(skinIO);
        CustomSkinProperty.setDefaultSkinValue(TEST_DEFAULT_VALUE);
        uuid = UUID.randomUUID();
    }

    @Nested
    class CacheBehavior {

        @Test
        @DisplayName("First getSkin loads from SkinIO and caches")
        void firstLoadPopulatesCache() {
            CustomSkinProperty persisted = new CustomSkinProperty("cGVyc2lzdGVk", "sig", "disk");
            skinIO.saveSkin(uuid, persisted);

            CustomSkinProperty result = storage.getSkin(uuid);
            assertNotNull(result);
            assertEquals("cGVyc2lzdGVk", result.getOriginalProperty().getValue());

            Path target = tempDir.resolve(uuid + ".json");
            assertTrue(target.toFile().delete());

            CustomSkinProperty cached = storage.getSkin(uuid);
            assertNotNull(cached);
            assertEquals("cGVyc2lzdGVk", cached.getOriginalProperty().getValue());
        }

        @Test
        @DisplayName("setSkin updates cache without writing to disk")
        void setSkinUpdatesCache() {
            CustomSkinProperty skin = new CustomSkinProperty("cached", "sig", "cache");

            storage.setSkin(uuid, skin);

            CustomSkinProperty result = storage.getSkin(uuid);
            assertNotNull(result);
            assertEquals("cached", result.getOriginalProperty().getValue());
        }

        @Test
        @DisplayName("saveSkin writes cached value to disk")
        void saveSkinWritesToDisk() {
            CustomSkinProperty skin = new CustomSkinProperty("tosave", "sig", "mem");
            storage.setSkin(uuid, skin);
            storage.saveSkin(uuid);

            CustomSkinProperty onDisk = skinIO.loadSkin(uuid);
            assertNotNull(onDisk);
            assertEquals("tosave", onDisk.getOriginalProperty().getValue());
        }
    }

    @Nested
    class DefaultSkin {

        @Test
        @DisplayName("hasDefaultSkin returns true for unset player")
        void defaultForUnsetPlayer() {
            assertTrue(storage.hasDefaultSkin(uuid));
        }

        @Test
        @DisplayName("hasDefaultSkin returns false after setting a custom skin")
        void notDefaultAfterSet() {
            CustomSkinProperty skin = new CustomSkinProperty("custom", "sig", "src");
            storage.setSkin(uuid, skin);

            assertFalse(storage.hasDefaultSkin(uuid));
        }
    }

    @Nested
    class SourceRetrieval {

        @Test
        @DisplayName("getSource returns source from cached skin")
        void sourceFromCache() {
            CustomSkinProperty skin = new CustomSkinProperty("dg==", "s", "my-source");
            storage.setSkin(uuid, skin);

            assertEquals("my-source", storage.getSource(uuid));
        }

        @Test
        @DisplayName("getSource returns source from disk when not cached")
        void sourceFromDisk() {
            CustomSkinProperty skin = new CustomSkinProperty("ZGlzay1zb3VyY2U=", "s", "disk-source");
            skinIO.saveSkin(uuid, skin);

            assertEquals("disk-source", storage.getSource(uuid));
        }

        @Test
        @DisplayName("getSource returns null when no skin exists")
        void sourceNull() {
            assertNull(storage.getSource(uuid));
        }
    }

    @Nested
    @DisplayName("setSkin null removes entry")
    class ResetToDefault {

        @Test
        @DisplayName("setSkin(null) removes entry from map and disk")
        void resetToDefault() {
            CustomSkinProperty custom = new CustomSkinProperty("custom", "sig", "src");
            storage.setSkin(uuid, custom);
            storage.saveSkin(uuid);
            Path skinFile = tempDir.resolve(uuid + ".json");
            assertTrue(skinFile.toFile().exists());
            assertFalse(storage.hasDefaultSkin(uuid));

            storage.setSkin(uuid, null);

            assertNull(storage.getSkin(uuid));
            assertTrue(storage.hasDefaultSkin(uuid));
            assertFalse(skinFile.toFile().exists());
        }
    }

    @Nested
    @DisplayName("removeSkin")
    class RemoveSkin {

        @Test
        @DisplayName("removeSkin removes from map and deletes file")
        void removeSkinTest() {
            CustomSkinProperty custom = new CustomSkinProperty("custom", "sig", "src");
            storage.setSkin(uuid, custom);
            storage.saveSkin(uuid);
            Path skinFile = tempDir.resolve(uuid + ".json");
            assertTrue(skinFile.toFile().exists());

            storage.removeSkin(uuid);

            assertNull(storage.getSkin(uuid));
            assertTrue(storage.hasDefaultSkin(uuid));
            assertFalse(skinFile.toFile().exists());
        }
    }

    @Nested
    @DisplayName("Empty skin handling")
    class EmptySkinHandling {

        @Test
        @DisplayName("loadSkin returns null and deletes file for empty skin")
        void loadEmptySkinReturnsNull() throws Exception {
            String stubJson = "{\"source\":\"legacy\",\"originalProperty\":{\"name\":\"textures\",\"value\":\"" + TEST_DEFAULT_VALUE + "\",\"signature\":\"sig\"}}";
            Files.write(tempDir.resolve(uuid + ".json"), stubJson.getBytes(StandardCharsets.UTF_8));

            assertNull(storage.loadSkin(uuid));
            assertFalse(Files.exists(tempDir.resolve(uuid + ".json")));
        }

        @Test
        @DisplayName("getSource returns null for empty skin")
        void getSourceForEmptySkinReturnsNull() {
            CustomSkinProperty emptySkin = new CustomSkinProperty("textures", "", "", "stub-source");
            storage.setSkin(uuid, emptySkin);

            assertNull(storage.getSource(uuid));
        }

        @Test
        @DisplayName("setSkin with empty skin removes from storage")
        void setEmptySkinRemovesFromStorage() {
            CustomSkinProperty emptySkin = new CustomSkinProperty("textures", "", "", "stub");
            storage.setSkin(uuid, emptySkin);

            assertNull(storage.getSkin(uuid));
            assertTrue(storage.hasDefaultSkin(uuid));
        }

        @Test
        @DisplayName("hasDefaultSkin returns true for empty cached skin")
        void defaultForEmptyCachedSkin() {
            CustomSkinProperty emptySkin = new CustomSkinProperty("textures", "", "", "src");
            storage.setSkin(uuid, emptySkin);

            assertTrue(storage.hasDefaultSkin(uuid));
        }
    }

    @Nested
    @DisplayName("Async drain coalesce")
    class AsyncDrain {

        @BeforeEach
        void resetMetrics() {
            SkinMetrics.INSTANCE.reset();
        }

        @Test
        @DisplayName("drain latch resets after the first drain so later saves persist")
        void saveSkinAsync_drainLatchResetsAfterDrain() throws Exception {
            // Regression test for the PR #121 latch bug (7cc66cf): the latch
            // stuck true after the first drain, silently deferring every later
            // save to logout/shutdown. Fails on the buggy code.
            UUID u1 = UUID.randomUUID();
            UUID u2 = UUID.randomUUID();

            storage.saveSkinAsync(u1, new CustomSkinProperty("textures", "sig1", "src1"));
            assertTrue(awaitFile(tempDir.resolve(u1 + ".json")),
                    "First async save should be persisted after the debounce window");

            storage.saveSkinAsync(u2, new CustomSkinProperty("textures", "sig2", "src2"));
            assertTrue(awaitFile(tempDir.resolve(u2 + ".json")),
                    "Second async save after latch reset should be persisted");
        }

        @Test
        @DisplayName("burst saves for the same UUID coalesce into one disk write")
        void saveSkinAsync_coalescesSameUUIDWrites() throws Exception {
            UUID u = UUID.randomUUID();
            storage.saveSkinAsync(u, new CustomSkinProperty("textures", "sig1", "src1"));
            storage.saveSkinAsync(u, new CustomSkinProperty("textures", "sig2", "src2"));
            storage.saveSkinAsync(u, new CustomSkinProperty("textures", "sig3", "src3"));
            storage.flushPending();

            // Only the last payload should hit disk (realWrites=1, savesCoalesced=2).
            Snapshot s = SkinMetrics.INSTANCE.snapshot();
            assertEquals(1, s.realWrites());
            assertEquals(2, s.savesCoalesced());
            assertEquals(3, s.savesSubmitted());
            assertEquals(3, s.savesCompleted());
            assertEquals(0, s.pendingAsyncWrites());
            assertTrue(Files.exists(tempDir.resolve(u + ".json")));
        }

        @Test
        @DisplayName("removeSkin purges pending writes so the deferred drain cannot resurrect the file")
        void deleteSkin_purgesPendingWrites() throws Exception {
            UUID u = UUID.randomUUID();
            storage.saveSkinAsync(u, new CustomSkinProperty("textures", "sig", "src"));
            storage.removeSkin(u); // before the drain fires
            storage.flushPending();

            assertFalse(Files.exists(tempDir.resolve(u + ".json")),
                    "Purged write must not be resurrected by the deferred drain");
        }
        @Test
        @DisplayName("saveSkinAsync returns a stage that completes on merge; the payload reaches disk after flush")
        void saveSkinAsync_persistsAndReturnsCompletionStage() throws Exception {
            UUID u = UUID.randomUUID();

            CompletableFuture<Void> stage = storage.saveSkinAsync(u, new CustomSkinProperty("textures", "sig", "src"));

            assertNotNull(stage, "saveSkinAsync must return a completion stage");
            stage.get(5, TimeUnit.SECONDS); // completes once the payload has been merged
            storage.flushPending();

            assertTrue(Files.exists(tempDir.resolve(u + ".json")),
                    "async save must persist once the drain has been flushed");
            CustomSkinProperty loaded = skinIO.loadSkin(u);
            assertNotNull(loaded, "the persisted payload must load back");
            assertEquals("src", loaded.getSource());
        }

        @Test
        @DisplayName("flushPending blocks until queued writes have landed on disk")
        void flushPending_waitsForPendingWrites_toFinish() throws Exception {
            UUID u = UUID.randomUUID();
            storage.saveSkinAsync(u, new CustomSkinProperty("textures", "sig", "src"));
            assertTrue(storage.hasPendingWrites(), "payload must be queued right after saveSkinAsync");

            storage.flushPending();

            assertFalse(storage.hasPendingWrites(), "flush must drain the queue");
            assertTrue(Files.exists(tempDir.resolve(u + ".json")),
                    "flush must have landed the write on disk before returning");
        }

        @Test
        @DisplayName("delete after a queued async save: the deferred drain cannot resurrect the file")
        void writeAfterDelete_doesNotResurrect() throws Exception {
            UUID u = UUID.randomUUID();
            Path target = tempDir.resolve(u + ".json");
            storage.saveSkinAsync(u, new CustomSkinProperty("textures", "sig", "src"));
            assertTrue(storage.hasPendingWrites(), "payload must be queued before the delete");

            storage.removeSkin(u); // purges the deferred payload, serializes the delete
            storage.flushPending();

            assertFalse(Files.exists(target),
                    "the deferred drain must not resurrect a deleted file (write-after-delete guard)");
        }

        @Test
        @DisplayName("hasPendingWrites reflects the background queue until flushed")
        void hasPendingWrites_reflectsBackgroundQueue() throws Exception {
            UUID u = UUID.randomUUID();
            assertFalse(storage.hasPendingWrites(), "no queue before any save");

            storage.saveSkinAsync(u, new CustomSkinProperty("textures", "sig", "src"));
            assertTrue(storage.hasPendingWrites(), "queue must be non-empty right after an async save");

            storage.flushPending();
            assertFalse(storage.hasPendingWrites(), "queue must be empty after the flush barrier");
        }
    }
    /** Polls for a file to appear (50ms debounce + load headroom, bounded 2s). */
    private static boolean awaitFile(Path file) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            if (Files.exists(file)) {
                return true;
            }
            Thread.sleep(50);
        }
        return Files.exists(file);
    }

}
