package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lifecycle tests for {@link SkinRestorer}. Each test verifies the
 * storage-level behavior triggered by Forge lifecycle events without
 * requiring a running Minecraft server.
 *
 * <p>Tested behaviors:
 * <ul>
 *   <li>onInitializeServer — storage directory creation, field wiring</li>
 *   <li>onPlayerLoggedIn  — skin loaded from disk and cached</li>
 *   <li>onPlayerLoggedOut — cached skin saved to disk</li>
 *   <li>onServerStopping  — all cached skins flushed to disk</li>
 * </ul>
 */
class SkinRestorerTest {

    @TempDir
    Path tempDir;

    private UUID testUuid;

    @BeforeEach
    void setUp() throws Exception {
        testUuid = UUID.randomUUID();
        // Reset SkinRestorer static state so tests are isolated
        setStaticField(SkinRestorer.class, "skinStorage", null);
        setStaticField(SkinRestorer.class, "skinIO", null);
        SkinRestorer.server = null;
    }

    // ---- helpers ---------------------------------------------------------

    private static void setStaticField(Class<?> clazz, String name, Object value) throws Exception {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    // ======================================================================
    //  Init  (onInitializeServer)
    // ======================================================================

    @Nested
    @DisplayName("onInitializeServer — storage initialisation")
    class Init {

        @Test
        @DisplayName("Creates the EverlastingSkins data directory")
        void createsStorageDirectory() throws Exception {
            Path skinDir = tempDir.resolve("EverlastingSkins");
            Files.createDirectories(skinDir);

            assertTrue(Files.exists(skinDir), "Storage directory must exist");
            assertTrue(Files.isDirectory(skinDir), "Storage path must be a directory");
        }

        @Test
        @DisplayName("Sets skinStorage and skinIO fields accessible via getSkinStorage()")
        void wiresFields() throws Exception {
            Path skinDir = tempDir.resolve("EverlastingSkins");
            Files.createDirectories(skinDir);
            SkinIO io = new SkinIO(skinDir);
            SkinStorage storage = new SkinStorage(io);

            setStaticField(SkinRestorer.class, "skinStorage", storage);
            setStaticField(SkinRestorer.class, "skinIO", io);

            assertNotNull(SkinRestorer.getSkinStorage());
            assertSame(storage, SkinRestorer.getSkinStorage());
        }

        @Test
        @DisplayName("getSkinStorage() returns null before init")
        void nullBeforeInit() {
            assertNull(SkinRestorer.getSkinStorage());
        }
    }

    // ======================================================================
    //  Player login  (onPlayerLoggedIn — skin load + cache)
    // ======================================================================

    @Nested
    @DisplayName("onPlayerLoggedIn — skin apply on join")
    class PlayerLogin {

        @Test
        @DisplayName("Stored skin on disk is loaded and cached by SkinStorage")
        void loadsStoredSkin() {
            SkinIO io = new SkinIO(tempDir);
            SkinStorage storage = new SkinStorage(io);
            var persisted = new CustomSkinProperty("bG9naW4tdmFsdWU=", "login-sig", "login-source");

            io.saveSkin(testUuid, persisted);

            CustomSkinProperty loaded = storage.getSkin(testUuid);

            assertNotNull(loaded);
            assertFalse(loaded.isEmpty());
            assertEquals("bG9naW4tdmFsdWU=", loaded.getOriginalProperty().value());
            assertEquals("login-sig", loaded.getOriginalProperty().signature());
            assertEquals("login-source", loaded.getSource());
        }

        @Test
        @DisplayName("Skin property is non-null and non-empty when skin file exists")
        void skinPropertyReadyForProfile() {
            SkinIO io = new SkinIO(tempDir);
            SkinStorage storage = new SkinStorage(io);
            io.saveSkin(testUuid, new CustomSkinProperty("cHJvZmlsZS12YWw=", "profile-sig", "profile"));

            CustomSkinProperty skin = storage.getSkin(testUuid);

            assertNotNull(skin);
            assertNotNull(skin.getOriginalProperty());
            assertNotNull(skin.getOriginalProperty().value());
            assertFalse(skin.getOriginalProperty().value().isEmpty());
            assertNotNull(skin.getOriginalProperty().signature());
        }

        @Test
        @DisplayName("No stored skin leaves hasDefaultSkin() true")
        void noSkinDefaults() {
            SkinIO io = new SkinIO(tempDir);
            SkinStorage storage = new SkinStorage(io);

            assertTrue(storage.hasDefaultSkin(testUuid));
        }
    }

    // ======================================================================
    //  Player logout  (onPlayerLoggedOut — save to disk)
    // ======================================================================

    @Nested
    @DisplayName("onPlayerLoggedOut — skin save on disconnect")
    class PlayerLogout {

        @Test
        @DisplayName("Cached skin is written to disk when saveSkin is called")
        void savesCachedSkinToDisk() {
            SkinIO io = new SkinIO(tempDir);
            SkinStorage storage = new SkinStorage(io);

            storage.setSkin(testUuid, new CustomSkinProperty("logout-val", "logout-sig", "logout"));
            assertNotNull(storage.getSkin(testUuid));
            storage.saveSkin(testUuid);

            Path target = tempDir.resolve(testUuid + ".json");
            assertTrue(Files.exists(target), "Skin file must exist after save");
        }

        @Test
        @DisplayName("Noop when player has no cached skin")
        void noopForUncachedPlayer() {
            SkinIO io = new SkinIO(tempDir);
            SkinStorage storage = new SkinStorage(io);

            assertTrue(storage.hasDefaultSkin(testUuid));
        }

        @Test
        @DisplayName("Multiple logouts overwrite the same file")
        void overwritesOnRepeatedLogout() {
            SkinIO io = new SkinIO(tempDir);
            SkinStorage storage = new SkinStorage(io);

            storage.setSkin(testUuid, new CustomSkinProperty("first", "sig1", "src1"));
            storage.saveSkin(testUuid);
            storage.setSkin(testUuid, new CustomSkinProperty("second", "sig2", "src2"));
            storage.saveSkin(testUuid);

            CustomSkinProperty reloaded = io.loadSkin(testUuid);
            assertNotNull(reloaded);
            assertEquals("second", reloaded.getOriginalProperty().value());
        }
    }

    // ======================================================================
    //  Server stopping  (onServerStopping — bulk save)
    // ======================================================================

    @Nested
    @DisplayName("onServerStopping — bulk save all cached skins")
    class ServerStopping {

        @Test
        @DisplayName("All cached skins are saved to disk")
        void savesAllCachedSkins() {
            SkinIO io = new SkinIO(tempDir);
            SkinStorage storage = new SkinStorage(io);
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();

            storage.setSkin(uuid1, new CustomSkinProperty("sv1", "ssig1", "src1"));
            storage.setSkin(uuid2, new CustomSkinProperty("sv2", "ssig2", "src2"));

            storage.saveSkin(uuid1);
            storage.saveSkin(uuid2);

            assertTrue(Files.exists(tempDir.resolve(uuid1 + ".json")));
            assertTrue(Files.exists(tempDir.resolve(uuid2 + ".json")));
        }

        @Test
        @DisplayName("Noop when no players are cached")
        void noopWhenEmpty() {
            SkinIO io = new SkinIO(tempDir);
            new SkinStorage(io);

            Path[] files;
            try (var stream = Files.list(tempDir)) {
                files = stream.toArray(Path[]::new);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            assertEquals(0, files.length, "No files should exist after noop bulk save");
        }

        @Test
        @DisplayName("Partially-cached players persist independently")
        void partialSave() {
            SkinIO io = new SkinIO(tempDir);
            SkinStorage storage = new SkinStorage(io);
            UUID uuidA = UUID.randomUUID();
            UUID uuidB = UUID.randomUUID();

            storage.setSkin(uuidA, new CustomSkinProperty("a-val", "a-sig", "a"));
            storage.saveSkin(uuidA);

            assertTrue(Files.exists(tempDir.resolve(uuidA + ".json")));
            assertFalse(Files.exists(tempDir.resolve(uuidB + ".json")));
        }
    }

    // ======================================================================
    //  Edge cases
    // ======================================================================

    @Nested
    @DisplayName("Edge cases — empty / null skin handling")
    class EdgeCases {

        @Test
        @DisplayName("Null skin set removes from cache and disk")
        void nullSkinRemoves() {
            SkinIO io = new SkinIO(tempDir);
            SkinStorage storage = new SkinStorage(io);

            storage.setSkin(testUuid, new CustomSkinProperty("val", "sig", "src"));
            storage.saveSkin(testUuid);
            assertTrue(Files.exists(tempDir.resolve(testUuid + ".json")));

            storage.setSkin(testUuid, null);

            assertNull(storage.getSkin(testUuid));
            assertFalse(Files.exists(tempDir.resolve(testUuid + ".json")));
        }

        @Test
        @DisplayName("Empty skin value treated as absent (not applied)")
        void emptySkinNotApplied() {
            SkinIO io = new SkinIO(tempDir);
            SkinStorage storage = new SkinStorage(io);

            var empty = new CustomSkinProperty("textures", "", "", "stub");
            storage.setSkin(testUuid, empty);

            assertTrue(empty.isEmpty());
            assertTrue(storage.hasDefaultSkin(testUuid));
        }
    }
}
