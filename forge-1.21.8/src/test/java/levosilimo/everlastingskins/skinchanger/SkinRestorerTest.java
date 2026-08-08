/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import com.google.common.util.concurrent.MoreExecutors;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.forge21.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.SkinIO;
import levosilimo.everlastingskins.skinchanger.SkinStorage;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lifecycle tests for {@link SkinRestorer} driven through the REAL
 * {@code @SubscribeEvent} handlers with headless {@link TestForgeEvents}
 * event instances (no running Minecraft server / no FML runtime).
 *
 * <p>The {@link Bootstrap#bootStrap()} guard is the same reflection hack as
 * {@code SkinRefreshHandlerTest}: the unit-test JVM has no server, so mocking
 * any registry-touching class (ServerPlayer) would otherwise throw
 * "Not bootstrapped". Calling bootStrap() itself would trigger Forge's patched
 * GameData.vanillaSnapshot(), which needs the FML runtime.
 */
class SkinRestorerTest {

    // static block FIRST: flag the vanilla bootstrap as done so ServerPlayer
    // mocks do not throw 'Not bootstrapped'. Mirrors SkinRefreshHandlerTest
    // lines 124-132.
    static {
        try {
            Field bootstrapFlag = Bootstrap.class.getDeclaredField("isBootstrapped");
            bootstrapFlag.setAccessible(true);
            bootstrapFlag.setBoolean(null, true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @TempDir
    Path tempDir;

    private SkinRestorer restorer;
    private MinecraftServer server;
    private UUID testUuid;

    @BeforeEach
    void setUp() throws Exception {
        testUuid = UUID.randomUUID();
        // Make the ForgeConfigSpec usable outside a loaded config file
        // (exact pattern from SkinRefreshHandlerTest.setUp).
        Config.COMMON_CONFIG.setConfig(
                InMemoryCommentedFormat.defaultInstance().createConfig(HashMap::new));

        // Reset singleton static state for test isolation (all helpers exist in :common).
        SkinStorage.resetForTest();
        SkinMetrics.INSTANCE.reset();
        SkinIO.shutdown();
        setStaticField(SkinRestorer.class, "skinStorage", null);
        setStaticField(SkinRestorer.class, "skinIO", null);
        SkinRestorer.server = null;
        // Install the synchronous test executor; never fires on the
        // synchronous stored-skin branch this suite exercises, but ships the
        // seam per spec for a future async default-skin test.
        SkinRestorer.setLoginExecutorForTest(MoreExecutors.newDirectExecutorService());

        restorer = new SkinRestorer();
        server = TestForgeEvents.mockServer(tempDir, Collections.emptyList());
    }

    @AfterEach
    void tearDown() throws Exception {
        SkinStorage.resetForTest();
        SkinMetrics.INSTANCE.reset();
        SkinIO.shutdown();
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
    //  Init  (real onInitializeServer handler)
    // ======================================================================

    @Nested
    @DisplayName("onInitializeServer — storage initialisation")
    class Init {

        @Test
        @DisplayName("Handler creates the EverlastingSkins data directory")
        void createsStorageDirectory() {
            Path skinDir = tempDir.resolve("EverlastingSkins");
            restorer.onInitializeServer(TestForgeEvents.newServerStartingEvent(server));

            assertTrue(Files.exists(skinDir), "Storage directory must exist");
            assertTrue(Files.isDirectory(skinDir), "Storage path must be a directory");
        }

        @Test
        @DisplayName("Handler wires skinStorage/skinIO; a saved+flushed skin lands on disk")
        void wiresStorage() throws Exception {
            restorer.onInitializeServer(TestForgeEvents.newServerStartingEvent(server));
            SkinStorage storage = SkinRestorer.getSkinStorage();
            assertNotNull(storage, "getSkinStorage() must be wired after handler");

            storage.setSkin(testUuid, new CustomSkinProperty("wire-val", "wire-sig", "wire"));
            storage.saveSkin(testUuid);
            SkinRestorer.getSkinStorage().flushPending();

            Path target = tempDir.resolve("EverlastingSkins").resolve(testUuid + ".json");
            assertTrue(Files.exists(target), "Wired SkinIO must write <uuid>.json to the save dir");
        }

        @Test
        @DisplayName("getSkinStorage() returns null before the handler runs")
        void nullBeforeInit() throws Exception {
            // Explicitly clear any state; assert the pre-init contract.
            SkinStorage.resetForTest();
            setStaticField(SkinRestorer.class, "skinStorage", null);
            setStaticField(SkinRestorer.class, "skinIO", null);

            assertNull(SkinRestorer.getSkinStorage());
        }
    }

    // ======================================================================
    //  Player login  (real onPlayerLoggedIn — synchronous stored-skin branch)
    // ======================================================================

    @Nested
    @DisplayName("onPlayerLoggedIn — skin apply on join (synchronous branch)")
    class PlayerLogin {

        @Test
        @DisplayName("Stored custom skin is applied to the profile via the real handler")
        void appliesStoredSkinToProfile() {
            restorer.onInitializeServer(TestForgeEvents.newServerStartingEvent(server));
            CustomSkinProperty skin = new CustomSkinProperty("dGV4dHVyZS12YWw=", "tex-sig", "Mojang");
            // Player WITH a stored custom skin => hasCustomSkin=true and
            // DEFAULT_SKINS_APPLY_FOR_PREMIUM=false (default) => applyDefault=false
            // => synchronous branch (no Mojang fetch, no executor).
            SkinRestorer.getSkinStorage().setSkin(testUuid, skin);

            ServerPlayer player = TestForgeEvents.mockPlayer(testUuid, "Player");
            restorer.onPlayerLoggedIn(TestForgeEvents.newPlayerLoggedInEvent(player));

            var textures = player.getGameProfile().getProperties().get("textures");
            assertNotNull(textures, "stored skin must land in the profile textures");
            assertFalse(textures.isEmpty(), "profile must carry at least one texture property");
            assertEquals(skin.getOriginalProperty().value(), textures.iterator().next().value(),
                    "profile texture value must equal the stored skin value");
        }

        @Test
        @DisplayName("Skin property is non-null and non-empty when skin file exists")
        void skinPropertyReadyForProfile() {
            restorer.onInitializeServer(TestForgeEvents.newServerStartingEvent(server));
            SkinRestorer.getSkinStorage().setSkin(testUuid,
                    new CustomSkinProperty("cHJvZmlsZS12YWw=", "profile-sig", "profile"));

            CustomSkinProperty skin = SkinRestorer.getSkinStorage().getSkin(testUuid);

            assertNotNull(skin);
            assertNotNull(skin.getOriginalProperty());
            assertNotNull(skin.getOriginalProperty().value());
            assertFalse(skin.getOriginalProperty().value().isEmpty());
            assertNotNull(skin.getOriginalProperty().signature());
        }

        @Test
        @DisplayName("No stored skin leaves hasDefaultSkin() true")
        void noSkinDefaults() {
            restorer.onInitializeServer(TestForgeEvents.newServerStartingEvent(server));

            assertTrue(SkinRestorer.getSkinStorage().hasDefaultSkin(testUuid));
        }
    }

    // ======================================================================
    //  Player logout  (real onPlayerLoggedOut — save to disk)
    // ======================================================================

    @Nested
    @DisplayName("onPlayerLoggedOut — skin save on disconnect")
    class PlayerLogout {

        @Test
        @DisplayName("Cached skin is written to disk via the real logout handler")
        void savesCachedSkinToDisk() {
            restorer.onInitializeServer(TestForgeEvents.newServerStartingEvent(server));
            SkinRestorer.getSkinStorage().setSkin(testUuid,
                    new CustomSkinProperty("logout-val", "logout-sig", "logout"));

            ServerPlayer player = TestForgeEvents.mockPlayer(testUuid, "Player");
            restorer.onPlayerLoggedOut(TestForgeEvents.newPlayerLoggedOutEvent(player));

            Path target = tempDir.resolve("EverlastingSkins").resolve(testUuid + ".json");
            assertTrue(Files.exists(target), "Skin file must exist after logout");
        }

        @Test
        @DisplayName("Noop when player has no cached skin")
        void noopForUncachedPlayer() {
            restorer.onInitializeServer(TestForgeEvents.newServerStartingEvent(server));

            ServerPlayer player = TestForgeEvents.mockPlayer(testUuid, "Player");
            restorer.onPlayerLoggedOut(TestForgeEvents.newPlayerLoggedOutEvent(player));

            Path target = tempDir.resolve("EverlastingSkins").resolve(testUuid + ".json");
            assertFalse(Files.exists(target), "No file should be written for an uncached player");
        }

        @Test
        @DisplayName("Multiple logouts overwrite the same file")
        void overwritesOnRepeatedLogout() {
            restorer.onInitializeServer(TestForgeEvents.newServerStartingEvent(server));
            SkinIO io = new SkinIO(tempDir.resolve("EverlastingSkins"));

            SkinRestorer.getSkinStorage().setSkin(testUuid,
                    new CustomSkinProperty("first", "sig1", "src1"));
            ServerPlayer player = TestForgeEvents.mockPlayer(testUuid, "Player");
            restorer.onPlayerLoggedOut(TestForgeEvents.newPlayerLoggedOutEvent(player));

            SkinRestorer.getSkinStorage().setSkin(testUuid,
                    new CustomSkinProperty("second", "sig2", "src2"));
            restorer.onPlayerLoggedOut(TestForgeEvents.newPlayerLoggedOutEvent(player));

            CustomSkinProperty reloaded = io.loadSkin(testUuid);
            assertNotNull(reloaded);
            assertEquals("second", reloaded.getOriginalProperty().value());
        }
    }

    // ======================================================================
    //  Server stopping  (real onServerStopping — bulk save)
    // ======================================================================

    @Nested
    @DisplayName("onServerStopping — bulk save all cached skins")
    class ServerStopping {

        @Test
        @DisplayName("All skin-backed online players are saved to disk via the real handler")
        void savesAllCachedSkins() {
            restorer.onInitializeServer(TestForgeEvents.newServerStartingEvent(server));
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();
            SkinRestorer.getSkinStorage().setSkin(uuid1,
                    new CustomSkinProperty("sv1", "ssig1", "src1"));
            SkinRestorer.getSkinStorage().setSkin(uuid2,
                    new CustomSkinProperty("sv2", "ssig2", "src2"));

            MinecraftServer stoppingServer = TestForgeEvents.mockServer(tempDir, List.of(
                    TestForgeEvents.mockPlayer(uuid1, "P1"),
                    TestForgeEvents.mockPlayer(uuid2, "P2")));
            SkinRestorer.server = stoppingServer;
            restorer.onServerStopping(TestForgeEvents.newServerStoppingEvent(stoppingServer));

            Path dir = tempDir.resolve("EverlastingSkins");
            assertTrue(Files.exists(dir.resolve(uuid1 + ".json")));
            assertTrue(Files.exists(dir.resolve(uuid2 + ".json")));
        }

        @Test
        @DisplayName("Noop when no players are online")
        void noopWhenEmpty() throws Exception {
            restorer.onInitializeServer(TestForgeEvents.newServerStartingEvent(server));

            MinecraftServer stoppingServer = TestForgeEvents.mockServer(tempDir, Collections.emptyList());
            SkinRestorer.server = stoppingServer;
            restorer.onServerStopping(TestForgeEvents.newServerStoppingEvent(stoppingServer));

            Path dir = tempDir.resolve("EverlastingSkins");
            Path[] files;
            try (var stream = Files.list(dir)) {
                files = stream.toArray(Path[]::new);
            }
            assertEquals(0, files.length, "No files should exist after noop bulk save");
        }

        @Test
        @DisplayName("Skin-backed player persists; empty player produces no file")
        void partialSave() {
            restorer.onInitializeServer(TestForgeEvents.newServerStartingEvent(server));
            UUID uuidA = UUID.randomUUID();
            UUID uuidB = UUID.randomUUID();
            SkinRestorer.getSkinStorage().setSkin(uuidA,
                    new CustomSkinProperty("a-val", "a-sig", "a"));

            MinecraftServer stoppingServer = TestForgeEvents.mockServer(tempDir, List.of(
                    TestForgeEvents.mockPlayer(uuidA, "PA"),
                    TestForgeEvents.mockPlayer(uuidB, "PB")));
            SkinRestorer.server = stoppingServer;
            restorer.onServerStopping(TestForgeEvents.newServerStoppingEvent(stoppingServer));

            Path dir = tempDir.resolve("EverlastingSkins");
            assertTrue(Files.exists(dir.resolve(uuidA + ".json")));
            assertFalse(Files.exists(dir.resolve(uuidB + ".json")));
        }
    }

    // ======================================================================
    //  Edge cases  (direct storage assertions)
    // ======================================================================

    @Nested
    @DisplayName("Edge cases — empty / null skin handling")
    class EdgeCases {

        @Test
        @DisplayName("Null skin set removes from cache and disk")
        void nullSkinRemoves() {
            restorer.onInitializeServer(TestForgeEvents.newServerStartingEvent(server));
            SkinStorage storage = SkinRestorer.getSkinStorage();
            storage.setSkin(testUuid, new CustomSkinProperty("val", "sig", "src"));
            storage.saveSkin(testUuid);
            assertTrue(Files.exists(tempDir.resolve("EverlastingSkins").resolve(testUuid + ".json")));

            storage.setSkin(testUuid, null);

            assertNull(storage.getSkin(testUuid));
            assertFalse(Files.exists(tempDir.resolve("EverlastingSkins").resolve(testUuid + ".json")));
        }

        @Test
        @DisplayName("Empty skin value treated as absent (not applied)")
        void emptySkinNotApplied() {
            restorer.onInitializeServer(TestForgeEvents.newServerStartingEvent(server));
            SkinStorage storage = SkinRestorer.getSkinStorage();

            var empty = new CustomSkinProperty("textures", "", "", "stub");
            storage.setSkin(testUuid, empty);

            assertTrue(empty.isEmpty());
            assertTrue(storage.hasDefaultSkin(testUuid));
        }
    }
}

