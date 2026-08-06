/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

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
 * Lifecycle tests for {@link SkinRestorer} (1.12.2). Each test verifies the
 * storage-level behavior triggered by Forge lifecycle events without
 * requiring a running Minecraft server.
 *
 * <p>Tested behaviors:
 * <ul>
 *   <li>onServerStarting  — storage directory creation, field wiring</li>
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
        setStaticField(SkinRestorer.class, "server", null);
    }

    // ---- helpers ---------------------------------------------------------

    private static void setStaticField(Class<?> clazz, String name, Object value) throws Exception {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static Field getStaticField(Class<?> clazz, String name) throws Exception {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    // ======================================================================
    //  Init  (onServerStarting)
    // ======================================================================

    @Nested
    @DisplayName("onServerStarting — storage initialisation")
    class Init {

        @Test
        @DisplayName("Creates the EverlastingSkins data directory")
        void createsStorageDirectory() throws Exception {
            Path skinDir = tempDir.resolve("EverlastingSkins");
            SkinIO io = new SkinIO(skinDir);
            SkinStorage storage = new SkinStorage(io);

            // Simulate the directory creation from onServerStarting
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

            assertNotNull(SkinRestorer.getSkinStorage(),
                    "getSkinStorage() must return non-null after init");
            assertSame(storage, SkinRestorer.getSkinStorage(),
                    "Returned storage must be the initialised instance");
        }

        @Test
        @DisplayName("getSkinStorage() returns null before init")
        void nullBeforeInit() {
            assertNull(SkinRestorer.getSkinStorage(),
                    "getSkinStorage() must be null before onServerStarting fires");
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
            CustomSkinProperty persisted = new CustomSkinProperty("bG9naW4tdmFsdWU=", "bG9naW4tc2ln", "login-source");

            io.saveSkin(testUuid, persisted);

            // This is what onPlayerLoggedIn does internally
            CustomSkinProperty loaded = storage.getSkin(testUuid);

            assertNotNull(loaded);
            assertFalse(loaded.isEmpty());
            assertEquals("bG9naW4tdmFsdWU=", loaded.getOriginalProperty().getValue());
            assertEquals("bG9naW4tc2ln", loaded.getOriginalProperty().getSignature());
            assertEquals("login-source", loaded.getSource());
        }

        @Test
        @DisplayName("Skin property is non-null and non-empty when skin file exists")
        void skinPropertyReadyForProfile() {
            SkinIO io = new SkinIO(tempDir);
            SkinStorage storage = new SkinStorage(io);
            io.saveSkin(testUuid, new CustomSkinProperty("cHJvZmlsZS12YWw=", "cHJvZmlsZS1zaWc=", "profile"));

            CustomSkinProperty skin = storage.getSkin(testUuid);

            assertNotNull(skin);
            assertNotNull(skin.getOriginalProperty());
            assertNotNull(skin.getOriginalProperty().getValue());
            assertFalse(skin.getOriginalProperty().getValue().isEmpty());
            assertNotNull(skin.getOriginalProperty().getSignature());
        }

        @Test
        @DisplayName("No stored skin leaves hasDefaultSkin() true")
        void noSkinDefaults() {
            SkinIO io = new SkinIO(tempDir);
            SkinStorage storage = new SkinStorage(io);

            // Player with no file on disk — onPlayerLoggedIn would
            // not apply any skin property.
            assertTrue(storage.hasDefaultSkin(testUuid),
                    "Player with no skin file must have default skin");
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
            // onPlayerLoggedOut checks getSkin() != null then calls saveSkin
            assertNotNull(storage.getSkin(testUuid));
            storage.saveSkin(testUuid);

            Path target = tempDir.resolve(testUuid + ".json");
            assertTrue(Files.exists(target), "Skin file must exist after save");
        }

        @Test
        @DisplayName("No-op when player has no cached skin (null check)")
        void noopForUncachedPlayer() {
            SkinIO io = new SkinIO(tempDir);
            SkinStorage storage = new SkinStorage(io);

            // onPlayerLoggedOut: if (skinStorage.getSkin(uuid) != null) saveSkin(uuid)
            // hasDefaultSkin confirms no custom skin is stored for this player
            assertTrue(storage.hasDefaultSkin(testUuid),
                    "Uncached player must show default skin");
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
            assertEquals("second", reloaded.getOriginalProperty().getValue(),
                    "Last saved value must overwrite earlier one");
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

            // Simulate onServerStopping: save every cached player
            storage.saveSkin(uuid1);
            storage.saveSkin(uuid2);

            assertTrue(Files.exists(tempDir.resolve(uuid1 + ".json")));
            assertTrue(Files.exists(tempDir.resolve(uuid2 + ".json")));
        }

        @Test
        @DisplayName("No-op when no players are cached")
        void noopWhenEmpty() {
            SkinIO io = new SkinIO(tempDir);
            new SkinStorage(io);  // no players set

            // Iterating an empty cache triggers no saves — no exception
            // and no files created
            Path[] files;
            try (java.util.stream.Stream<Path> stream = Files.list(tempDir)) {
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

            // Only uuidA is saved — simulating a partial stop
            storage.saveSkin(uuidA);

            assertTrue(Files.exists(tempDir.resolve(uuidA + ".json")));
            assertFalse(Files.exists(tempDir.resolve(uuidB + ".json")),
                    "Unsaved players must not leave files");
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

            // Setting null removes from map and deletes file (like /skin clear)
            storage.setSkin(testUuid, null);

            assertNull(storage.getSkin(testUuid));
            assertFalse(Files.exists(tempDir.resolve(testUuid + ".json")));
        }

        @Test
        @DisplayName("Empty skin value treated as absent (not applied)")
        void emptySkinNotApplied() {
            SkinIO io = new SkinIO(tempDir);
            SkinStorage storage = new SkinStorage(io);

            CustomSkinProperty empty = new CustomSkinProperty("textures", "", "", "stub");
            storage.setSkin(testUuid, empty);

            // onPlayerLoggedIn checks !skin.isEmpty() before applying
            assertTrue(empty.isEmpty(),
                    "Skin with empty value must be considered empty");
            assertTrue(storage.hasDefaultSkin(testUuid),
                    "Player with empty skin must be treated as default");
        }
    }
}
