/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.forge26.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.metrics.SkinMetrics;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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
        // Tolerant on 26.x: if a future Forge line drops the legacy callbacks,
        // the class is simply absent and there is nothing to initialise.
        try {
            Class.forName("net.minecraftforge.registries.GameData");
        } catch (ClassNotFoundException ignored) {
            // 26.x Forge removed the legacy registry callbacks; nothing to init.
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

    // ======================================================================
    //  Init  (onInitializeServer — real handler)
    // ======================================================================

    @Nested
    @DisplayName("onInitializeServer — storage initialisation")
    class Init {

        @Test
        @DisplayName("Creates the EverlastingSkins data directory")
        void createsStorageDirectory() {
            assertTrue(Files.exists(skinDir), "handler must create the storage directory");
            assertTrue(Files.isDirectory(skinDir), "storage path must be a directory");
        }

        @Test
        @DisplayName("Wires skinStorage; a set+save+flush lands <uuid>.json on disk")
        void wiresStorage() {
            storage().setSkin(testUuid, new CustomSkinProperty("wire-val", "wire-sig", "wire"));
            storage().saveSkin(testUuid);
            storage().flushPending();

            assertTrue(Files.exists(skinDir.resolve(testUuid + ".json")),
                    "the wired SkinIO must persist the skin to disk");
        }

        @Test
        @DisplayName("getSkinStorage() returns null before the handler runs")
        void nullBeforeInit() throws Exception {
            setStaticField(SkinRestorer.class, "skinStorage", null);
            setStaticField(SkinRestorer.class, "skinIO", null);
            assertNull(SkinRestorer.getSkinStorage(), "no storage before onInitializeServer");

            // Restore the wired state: JUnit does not guarantee nested-class
            // order, and sibling tests read the static through storage().
            restorer.onInitializeServer(TestForgeEvents.newServerStartingEvent(server));
        }
    }

    // ======================================================================
    //  Player login  (onPlayerLoggedIn — real handler, synchronous branch)
    // ======================================================================

    @Nested
    @DisplayName("onPlayerLoggedIn — stored skin applied through the real handler")
    class PlayerLogin {

        @Test
        @DisplayName("Stored custom skin is applied to the GameProfile (synchronous branch)")
        void appliesStoredSkinToProfile() {
            CustomSkinProperty stored = new CustomSkinProperty("login-val", "login-sig", "login");
            storage().setSkin(testUuid, stored);
            ServerPlayer player = TestForgeEvents.mockPlayer(testUuid, "Alice");

            restorer.onPlayerLoggedIn(TestForgeEvents.newPlayerLoggedInEvent(player));

            Collection<Property> textures = player.getGameProfile().properties().get("textures");
            assertEquals(1, textures.size(), "exactly one textures property after apply");
            assertEquals(stored.getOriginalProperty(), textures.iterator().next(),
                    "the applied property must equal the stored skin");
        }

        @Test
        @DisplayName("Skin property is non-null and non-empty when skin file exists")
        void skinPropertyReadyForProfile() {
            // Value must be valid base64: SkinIO.loadSkin drops records that
            // fail CustomSkinProperty.isValid() (corrupt values never reach
            // the profile).
            skinIO.saveSkin(testUuid, new CustomSkinProperty("cHJvZmlsZS12YWw=", "profile-sig", "profile"));

            CustomSkinProperty skin = storage().getSkin(testUuid);

            assertNotNull(skin);
            assertNotNull(skin.getOriginalProperty());
            assertNotNull(skin.getOriginalProperty().value());
            assertFalse(skin.getOriginalProperty().value().isEmpty());
            assertNotNull(skin.getOriginalProperty().signature());
        }

        @Test
        @DisplayName("No stored skin leaves hasDefaultSkin() true")
        void noSkinDefaults() {
            assertTrue(storage().hasDefaultSkin(testUuid));
        }
    }

    // ======================================================================
    //  Player logout  (onPlayerLoggedOut — real handler)
    // ======================================================================

    @Nested
    @DisplayName("onPlayerLoggedOut — cached skin saved through the real handler")
    class PlayerLogout {

        @Test
        @DisplayName("Cached skin is written to disk on logout")
        void savesCachedSkinToDisk() {
            storage().setSkin(testUuid, new CustomSkinProperty("logout-val", "logout-sig", "logout"));
            assertNotNull(storage().getSkin(testUuid), "skin must be cached before logout");

            restorer.onPlayerLoggedOut(TestForgeEvents.newPlayerLoggedOutEvent(
                    TestForgeEvents.mockPlayer(testUuid, "Alice")));

            assertTrue(Files.exists(skinDir.resolve(testUuid + ".json")),
                    "logout must persist the cached skin to disk");
        }

        @Test
        @DisplayName("Noop when player has no cached skin")
        void noopForUncachedPlayer() {
            restorer.onPlayerLoggedOut(TestForgeEvents.newPlayerLoggedOutEvent(
                    TestForgeEvents.mockPlayer(testUuid, "Alice")));

            assertTrue(storage().hasDefaultSkin(testUuid));
            assertFalse(Files.exists(skinDir.resolve(testUuid + ".json")),
                    "no skin file may appear for an uncached player");
        }

        @Test
        @DisplayName("Repeated logouts overwrite the same file")
        void overwritesOnRepeatedLogout() {
            storage().setSkin(testUuid, new CustomSkinProperty("first", "sig1", "src1"));
            restorer.onPlayerLoggedOut(TestForgeEvents.newPlayerLoggedOutEvent(
                    TestForgeEvents.mockPlayer(testUuid, "Alice")));
            storage().setSkin(testUuid, new CustomSkinProperty("second", "sig2", "src2"));
            restorer.onPlayerLoggedOut(TestForgeEvents.newPlayerLoggedOutEvent(
                    TestForgeEvents.mockPlayer(testUuid, "Alice")));

            CustomSkinProperty reloaded = skinIO.loadSkin(testUuid);
            assertNotNull(reloaded);
            assertEquals("second", reloaded.getOriginalProperty().value());
        }
    }

    // ======================================================================
    //  Server stopping  (onServerStopping — real handler)
    // ======================================================================

    @Nested
    @DisplayName("onServerStopping — bulk save through the real handler")
    class ServerStopping {

        @Test
        @DisplayName("All cached skins of online players are saved to disk")
        void savesAllCachedSkins() {
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();
            storage().setSkin(uuid1, new CustomSkinProperty("sv1", "ssig1", "src1"));
            storage().setSkin(uuid2, new CustomSkinProperty("sv2", "ssig2", "src2"));

            driveStopping(List.of(TestForgeEvents.mockPlayer(uuid1, "A"),
                    TestForgeEvents.mockPlayer(uuid2, "B")));

            assertTrue(Files.exists(skinDir.resolve(uuid1 + ".json")));
            assertTrue(Files.exists(skinDir.resolve(uuid2 + ".json")));
        }

        @Test
        @DisplayName("Noop when no players are online")
        void noopWhenEmpty() {
            driveStopping(List.of());

            try (var stream = Files.list(skinDir)) {
                assertEquals(0, stream.count(), "no files may appear with an empty player list");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("Partially-cached players persist independently")
        void partialSave() {
            UUID uuidA = UUID.randomUUID();
            UUID uuidB = UUID.randomUUID();
            storage().setSkin(uuidA, new CustomSkinProperty("a-val", "a-sig", "a"));

            driveStopping(List.of(TestForgeEvents.mockPlayer(uuidA, "A"),
                    TestForgeEvents.mockPlayer(uuidB, "B")));

            assertTrue(Files.exists(skinDir.resolve(uuidA + ".json")));
            assertFalse(Files.exists(skinDir.resolve(uuidB + ".json")));
        }

        /** Stubs the handler's static server with the online roster, then drives the handler. */
        private void driveStopping(List<ServerPlayer> online) {
            SkinRestorer.server = TestForgeEvents.mockServer(tempDir, online);
            restorer.onServerStopping(TestForgeEvents.newServerStoppingEvent(server));
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
            storage().setSkin(testUuid, new CustomSkinProperty("val", "sig", "src"));
            storage().saveSkin(testUuid);
            assertTrue(Files.exists(skinDir.resolve(testUuid + ".json")));

            storage().setSkin(testUuid, null);

            assertNull(storage().getSkin(testUuid));
            assertFalse(Files.exists(skinDir.resolve(testUuid + ".json")));
        }

        @Test
        @DisplayName("Empty skin value treated as absent (not applied)")
        void emptySkinNotApplied() {
            var empty = new CustomSkinProperty("textures", "", "", "stub");
            storage().setSkin(testUuid, empty);

            assertTrue(empty.isEmpty());
            assertTrue(storage().hasDefaultSkin(testUuid));
        }
    }
}
