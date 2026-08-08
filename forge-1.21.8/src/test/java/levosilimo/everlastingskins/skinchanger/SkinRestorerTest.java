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

}
