package levosilimo.everlastingskins.skinchanger;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkinStorage tests: cache hit/miss, default skin, set/clear, source
 * retrieval. Operates on {@link UUID} directly. No live endpoints
 * or filesystem state leak between tests.
 */
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
            // Pre-save a skin so SkinIO has something to load
            var persisted = new CustomSkinProperty("persisted", "sig", "disk");
            skinIO.saveSkin(uuid, persisted);

            // First call should load from SkinIO
            CustomSkinProperty result = storage.getSkin(uuid);
            assertNotNull(result);
            assertEquals("persisted", result.getOriginalProperty().value());

            // Second call should return cached value without hitting SkinIO
            // Delete the backing file to prove cache hit
            Path target = tempDir.resolve(uuid + ".json");
            assertTrue(target.toFile().delete());

            CustomSkinProperty cached = storage.getSkin(uuid);
            assertNotNull(cached);
            assertEquals("persisted", cached.getOriginalProperty().value());
        }

        @Test
        @DisplayName("setSkin updates cache without writing to disk")
        void setSkinUpdatesCache() {
            var skin = new CustomSkinProperty("cached", "sig", "cache");

            storage.setSkin(uuid, skin);

            // Should return cache value even though nothing is on disk
            CustomSkinProperty result = storage.getSkin(uuid);
            assertNotNull(result);
            assertEquals("cached", result.getOriginalProperty().value());
        }

        @Test
        @DisplayName("saveSkin writes cached value to disk")
        void saveSkinWritesToDisk() {
            var skin = new CustomSkinProperty("tosave", "sig", "mem");
            storage.setSkin(uuid, skin);
            storage.saveSkin(uuid);

            // Reload from SkinIO directly to verify disk write
            CustomSkinProperty onDisk = skinIO.loadSkin(uuid);
            assertNotNull(onDisk);
            assertEquals("tosave", onDisk.getOriginalProperty().value());
        }
    }

    @Nested
    class DefaultSkin {

        @Test
        @DisplayName("hasDefaultSkin returns true for unset player")
        void defaultForUnsetPlayer() {
            // When nothing is cached or on disk, getSkin returns DEFAULT_SKIN
            assertTrue(storage.hasDefaultSkin(uuid));
        }

        @Test
        @DisplayName("hasDefaultSkin returns false after setting a custom skin")
        void notDefaultAfterSet() {
            var skin = new CustomSkinProperty("custom", "sig", "src");
            storage.setSkin(uuid, skin);

            assertFalse(storage.hasDefaultSkin(uuid));
        }
    }

    @Nested
    class SourceRetrieval {

        @Test
        @DisplayName("getSource returns source from cached skin")
        void sourceFromCache() {
            var skin = new CustomSkinProperty("v", "s", "my-source");
            storage.setSkin(uuid, skin);

            assertEquals("my-source", storage.getSource(uuid));
        }

        @Test
        @DisplayName("getSource returns source from disk when not cached")
        void sourceFromDisk() {
            var skin = new CustomSkinProperty("v", "s", "disk-source");
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
            var custom = new CustomSkinProperty("custom", "sig", "src");
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
            var custom = new CustomSkinProperty("custom", "sig", "src");
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
            var emptySkin = new CustomSkinProperty("textures", "", "", "stub-source");
            storage.setSkin(uuid, emptySkin);

            assertNull(storage.getSource(uuid));
        }

        @Test
        @DisplayName("setSkin with empty skin removes from storage")
        void setEmptySkinRemovesFromStorage() {
            var emptySkin = new CustomSkinProperty("textures", "", "", "stub");
            storage.setSkin(uuid, emptySkin);

            assertNull(storage.getSkin(uuid));
            assertTrue(storage.hasDefaultSkin(uuid));
        }

        @Test
        @DisplayName("hasDefaultSkin returns true for empty cached skin")
        void defaultForEmptyCachedSkin() {
            var emptySkin = new CustomSkinProperty("textures", "", "", "src");
            storage.setSkin(uuid, emptySkin);

            assertTrue(storage.hasDefaultSkin(uuid));
        }
    }
}
