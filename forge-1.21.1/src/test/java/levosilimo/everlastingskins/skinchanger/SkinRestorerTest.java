/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.forge21.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lifecycle tests for {@link SkinRestorer} driven through the real
 * {@code @SubscribeEvent} handlers with headless {@link TestForgeEvents}
 * event instances (no running Minecraft server / no FML runtime).
 *
 * <p>The {@link Bootstrap#bootStrap()} guard is the same reflection hack as
 * {@code SkinRefreshHandlerTest}: the unit-test JVM has no server, so mocking
 * any registry-touching class (ServerPlayer) would otherwise throw
 * "Not bootstrapped".
 *
 * <p>Method order: Init (3) -> PlayerLogin (3) -> PlayerLogout (3) ->
 * ServerStopping (3) -> EdgeCases (2) = 14 tests.
 */
class SkinRestorerTest {

    // static block FIRST: flag the vanilla bootstrap as done so ServerPlayer
    // mocks do not throw 'Not bootstrapped'. Mirrors SkinRefreshHandlerTest
    // lines 124-132. Calling bootStrap() itself would trigger Forge's patched
    // GameData.vanillaSnapshot(), which needs the FML runtime.
    static {
        try {
            Field bootstrapFlag = Bootstrap.class.getDeclaredField("isBootstrapped");
            bootstrapFlag.setAccessible(true);
            bootstrapFlag.setBoolean(null, true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
        // Forge's Block.<clinit> reads BLOCK_STATE_REGISTRY through
        // GameData$BlockCallbacks, which NPEs unless the Forge registries have
        // been created. GameData.<clinit> runs that init; FML does it in a real
        // runtime, the unit-test JVM needs the explicit trigger. Must run AFTER
        // the isBootstrapped flag above (GameData.init reads BuiltInRegistries).
        try {
            Class.forName("net.minecraftforge.registries.GameData");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @TempDir
    Path tempDir;

    private SkinRestorer restorer;
    private MinecraftServer server;
    private SkinIO skinIO;
    private Path skinDir;
    private UUID testUuid;

    @BeforeEach
    void setUp() throws Exception {
        testUuid = UUID.randomUUID();
        resetForTest();

        // Make the ForgeConfigSpec readable outside a loaded config file
        // (same pattern as SkinRefreshHandlerTest.setUp). applyForPremium=false
        // (the default) keeps login on the synchronous stored-skin branch.
        Config.COMMON_CONFIG.setConfig(
                InMemoryCommentedFormat.defaultInstance().createConfig(HashMap::new));
        Config.DEFAULT_SKINS_APPLY_FOR_PREMIUM.set(false);

        server = TestForgeEvents.mockServer(tempDir);
        skinDir = tempDir.resolve("EverlastingSkins");

        // Drive the REAL @SubscribeEvent handler: ServerStartingEvent is a
        // plain object and MinecraftServer is a Mockito mock, so the init
        // path runs without an FML runtime.
        restorer = new SkinRestorer();
        restorer.onInitializeServer(TestForgeEvents.newServerStartingEvent(server));

        assertNotNull(SkinRestorer.getSkinStorage(), "handler must wire skinStorage");
        skinIO = new SkinIO(skinDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        resetForTest();
    }

    // ---- helpers ---------------------------------------------------------

    /** Current handler-wired storage (re-read: nullBeforeInit re-drives init). */
    private static SkinStorage storage() {
        return SkinRestorer.getSkinStorage();
    }

    /** Full singleton/static reset so tests never leak state across the JVM. */
    private static void resetForTest() throws Exception {
        SkinStorage.resetForTest();
        SkinMetrics.INSTANCE.reset();
        SkinIO.shutdown();
        setStaticField(SkinRestorer.class, "skinStorage", null);
        setStaticField(SkinRestorer.class, "skinIO", null);
        SkinRestorer.server = null;
    }

    private static void setStaticField(Class<?> clazz, String name, Object value) throws Exception {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
