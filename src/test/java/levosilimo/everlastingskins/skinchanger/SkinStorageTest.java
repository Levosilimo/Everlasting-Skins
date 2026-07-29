package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkinStorage tests: cache hit/miss, default skin, set/clear, source
 * retrieval. No live endpoints or filesystem state leak between tests.
 */
class SkinStorageTest {

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

    @Nested
    class CacheBehavior {

        @Test
        @DisplayName("First getSkin loads from SkinIO and caches")
        void firstLoadPopulatesCache() {
            CustomSkinProperty persisted = new CustomSkinProperty("persisted", "sig", "disk");
            skinIO.saveSkin(uuid, persisted);

            CustomSkinProperty result = storage.getSkin(uuid);
            assertNotNull(result);
            assertEquals("persisted", result.getOriginalProperty().getValue());

            Path target = tempDir.resolve(uuid + ".json");
            assertTrue(target.toFile().delete());

            CustomSkinProperty cached = storage.getSkin(uuid);
            assertNotNull(cached);
            assertEquals("persisted", cached.getOriginalProperty().getValue());
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
            CustomSkinProperty skin = new CustomSkinProperty("v", "s", "my-source");
            storage.setSkin(uuid, skin);

            assertEquals("my-source", storage.getSource(uuid));
        }

        @Test
        @DisplayName("getSource returns source from disk when not cached")
        void sourceFromDisk() {
            CustomSkinProperty skin = new CustomSkinProperty("v", "s", "disk-source");
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
    @DisplayName("setSkin null resets to default")
    class ResetToDefault {

        @Test
        @DisplayName("setSkin(null) resets to default skin")
        void resetToDefault() {
            CustomSkinProperty custom = new CustomSkinProperty("custom", "sig", "src");
            storage.setSkin(uuid, custom);
            assertFalse(storage.hasDefaultSkin(uuid));

            storage.setSkin(uuid, null);
            assertTrue(storage.hasDefaultSkin(uuid));
        }
    }
}
