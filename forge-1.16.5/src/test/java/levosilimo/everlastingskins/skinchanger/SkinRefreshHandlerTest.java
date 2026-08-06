/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.broadcast.FakeSkinBroadcaster;
import levosilimo.everlastingskins.broadcast.VanillaSkinBroadcaster;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.permission.TestConfigSupport;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.network.play.server.SPlayerAbilitiesPacket;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.network.play.server.SRespawnPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Broadcast/cascade ordering contract of the 1.16.5 refresh pipeline
 * ({@link SkinRefreshHandler#task}). Pure Mockito unit test: players,
 * server, player list and level are mocks; the per-connection packet
 * stream is captured, and the broadcaster seam is exercised via
 * {@link FakeSkinBroadcaster}.
 *
 * <p>1.16.5 cascade shape (pinned here): the target connection receives
 * {@code SRespawnPacket} then the {@code ServerPlayNetHandler#teleport}
 * call (which is what sends the vanilla {@code SPlayerPositionLookPacket}
 * on this version — the handler never builds that packet itself), then the
 * three PlayerList helper sends (level info, permission level, all player
 * info), then an explicit {@code SPlayerAbilitiesPacket} (1.16.5's
 * sendAllPlayerInfo does not include abilities). The teleport arguments
 * must reproduce the player's position/yaw/pitch.
 *
 * <p>Cascade atomicity invariant (R3): the disk flush must be enqueued
 * BEFORE the GameProfile is mutated, so a failed enqueue leaves the
 * applied profile untouched — applied and on-disk skin stay consistent.
 * The invariant lives in {@link SkinRefreshHandler#applyAtomicPersistence}
 * so it is unit-testable without a ServerPlayerEntity.
 */
class SkinRefreshHandlerTest {

    /**
     * The unit-test JVM has no running server. On 1.16.5 the vanilla
     * registry chain is self-initializing, but the order matters:
     * {@code World.<clinit>} → {@code Registry.<clinit>} →
     * {@code MemoryModuleType.<clinit>} → {@code GlobalPos.<clinit>} reads
     * {@code World.RESOURCE_KEY_CODEC} — if {@code World} started the chain
     * it is mid-clinit there and the read is null (NPE). The game initializes
     * {@code Registry} first; force the same order here so {@code World}'s
     * clinit always runs fresh. (1.16.5 has no isBootstrapped gate on
     * registry access, so no flag trick is needed — but {@link
     * Bootstrap#bootStrap()} itself must not be called: Forge patches it to
     * run GameData.vanillaSnapshot(), which needs the FML runtime.)
     */
    static {
        try {
            Class.forName("net.minecraft.util.registry.Registry");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final String TEXTURE_VALUE = "cGF5bG9hZA==";
    private static final String TEXTURE_SIGNATURE = "c2lnbmF0dXJl";
    private static final RegistryKey<World> OVERWORLD = World.OVERWORLD;

    private static final UUID PLAYER_UUID = UUID.randomUUID();
    private static final Property OLD_PROPERTY = new Property("textures", "oldValue", "oldSignature");
    private static final CustomSkinProperty NEW_SKIN =
            new CustomSkinProperty("textures", "newValue", "newSignature", "MojangAPI");

    @TempDir
    Path tempDir;

    private SkinIO skinIO;
    private SkinStorage storage;

    private MinecraftServer server;
    private PlayerList playerlist;
    private ServerWorld overworldLevel;
    private ServerChunkProvider chunkSource;

    private ServerPlayerEntity target;

    private FakeSkinBroadcaster fakeBroadcaster;

    private final Map<ServerPlayerEntity, List<Object>> playerStreams = new HashMap<>();
    private List<Object> targetStream;

    private final List<Object> teleportCalls = new ArrayList<>();

    @BeforeAll
    static void loadConfig() {
        TestConfigSupport.loadDefaults();
    }

    @BeforeEach
    void setUp() throws Exception {
        // Make the ForgeConfigSpec usable outside a loaded config file
        // (same pattern as ConfigTest).
        Config.COMMON_CONFIG.setConfig(
                InMemoryCommentedFormat.defaultInstance().createConfig(HashMap::new));
        Config.BROADCAST_USE_BUNDLE.set(false);
        Config.DIMENSION_SCOPED_BROADCAST.set(false);
        Config.REFRESH_VIA_ENTITY_TRACKER.set(true);

        Path skinDir = tempDir.resolve("EverlastingSkins");
        Files.createDirectories(skinDir);
        skinIO = new SkinIO(skinDir);
        storage = new SkinStorage(skinIO);
        setStaticField(SkinRestorer.class, "skinStorage", storage);
        SkinRestorer.server = null;
        SkinMetrics.INSTANCE.reset();
        SkinRefreshHandler.resetRefreshTaskCount();

        playerStreams.clear();
        teleportCalls.clear();

        overworldLevel = levelMock(OVERWORLD);
        chunkSource = mock(ServerChunkProvider.class);
        when(overworldLevel.getChunkSource()).thenReturn(chunkSource);

        playerlist = mock(PlayerList.class);
        server = mock(MinecraftServer.class);
        when(server.getPlayerList()).thenReturn(playerlist);
        SkinRestorer.server = server;

        target = newPlayer("Target", overworldLevel);

        targetStream = streamFor(target);

        // Faithful PlayerList seams: emit a marker for each helper send so
        // the captured stream pins the cascade order.
        doAnswer(inv -> {
            streamFor(inv.getArgument(0)).add("sendLevelInfo");
            return null;
        }).when(playerlist).sendLevelInfo(any(ServerPlayerEntity.class), any(ServerWorld.class));
        doAnswer(inv -> {
            streamFor(inv.getArgument(0)).add("sendPlayerPermissionLevel");
            return null;
        }).when(playerlist).sendPlayerPermissionLevel(any(ServerPlayerEntity.class));
        doAnswer(inv -> {
            streamFor(inv.getArgument(0)).add("sendAllPlayerInfo");
            return null;
        }).when(playerlist).sendAllPlayerInfo(any(ServerPlayerEntity.class));

        // Inject the recording fake; assertions read its call lists.
        fakeBroadcaster = new FakeSkinBroadcaster();
        SkinRefreshHandler.setBroadcaster(fakeBroadcaster);

        storage.setSkin(target.getUUID(),
                new CustomSkinProperty("textures", TEXTURE_VALUE, TEXTURE_SIGNATURE, "Notch"));
    }

    @AfterEach
    void tearDown() throws Exception {
        Config.BROADCAST_USE_BUNDLE.set(false);
        Config.DIMENSION_SCOPED_BROADCAST.set(false);
        Config.REFRESH_VIA_ENTITY_TRACKER.set(true);
        skinIO.flushPending();
        setStaticField(SkinRestorer.class, "skinStorage", null);
        SkinRestorer.server = null;
        // Restore the production broadcaster so a later test in the same JVM
        // does not see the fake.
        SkinRefreshHandler.setBroadcaster(new VanillaSkinBroadcaster());
    }

    /* ================================================================== */
    /*  A. Broadcaster call shape                                          */
    /* ================================================================== */

    @Test
    @DisplayName("handler invokes broadcastProfileChange then trackerUntrackRetrack (tracker flag enabled)")
    void handlerCalls_broadcastThenTracker() {
        refresh();

        assertEquals(1, fakeBroadcaster.broadcastCalls.size(),
            "exactly one broadcastProfileChange per refresh; calls=" + fakeBroadcaster.broadcastCalls);
        assertEquals(1, fakeBroadcaster.trackerCalls.size(),
            "exactly one trackerUntrackRetrack per refresh; calls=" + fakeBroadcaster.trackerCalls);
        assertEquals(target, fakeBroadcaster.broadcastCalls.get(0).target(),
            "broadcast target must be the refreshed player");
        assertEquals(target, fakeBroadcaster.trackerCalls.get(0),
            "tracker call must target the refreshed player");
    }

    @Test
    @DisplayName("handler always invokes trackerUntrackRetrack; flag check lives in the broadcaster")
    void handlerCalls_trackerEvenWhenFlagDisabled() {
        // The handler unconditionally delegates to broadcaster.trackerUntrackRetrack;
        // the broadcaster reads REFRESH_VIA_ENTITY_TRACKER and is a no-op when off.
        Config.REFRESH_VIA_ENTITY_TRACKER.set(false);
        refresh();

        assertEquals(1, fakeBroadcaster.broadcastCalls.size(),
            "broadcast still runs; calls=" + fakeBroadcaster.broadcastCalls);
        assertEquals(1, fakeBroadcaster.trackerCalls.size(),
            "handler still calls broadcaster.trackerUntrackRetrack; the flag is the broadcaster's concern");
    }

    @Test
    @DisplayName("broadcaster is invoked once per refresh across the 8-flag matrix")
    void configMatrix_handlerInvokesBroadcasterOnce() {
        for (boolean bundle : new boolean[]{false, true}) {
            for (boolean scoped : new boolean[]{false, true}) {
                for (boolean tracker : new boolean[]{false, true}) {
                    Config.BROADCAST_USE_BUNDLE.set(bundle);
                    Config.DIMENSION_SCOPED_BROADCAST.set(scoped);
                    Config.REFRESH_VIA_ENTITY_TRACKER.set(tracker);
                    refresh();

                    String combo = "bundle=" + bundle + ", scoped=" + scoped + ", tracker=" + tracker;
                    assertEquals(1, fakeBroadcaster.broadcastCalls.size(),
                        combo + ": exactly one broadcast");
                    assertEquals(1, fakeBroadcaster.trackerCalls.size(),
                        combo + ": handler always delegates tracker call");
                }
            }
        }
    }

    /* ================================================================== */
    /*  B. Cascade order on the target connection                          */
    /* ================================================================== */

    @Test
    @DisplayName("Cascade order: respawn, teleport, levelInfo, permission, allPlayerInfo, abilities")
    void cascadeOrderFullStream() {
        refresh();

        assertCascadeOrder(targetStream);
    }

    @Test
    @DisplayName("Each cascade packet type is sent exactly once per refresh")
    void eachPacketTypeExactlyOnce() {
        // refresh() clears between runs, so drive task() directly to
        // accumulate two refreshes in one captured stream.
        resetStreams();
        SkinRefreshHandler.task(target);
        SkinRefreshHandler.task(target);

        // Two refreshes: every cascade type appears exactly twice total.
        assertEquals(2, countOfType(targetStream, SRespawnPacket.class));
        assertEquals(2, countOfType(targetStream, "teleport", String.class));
        assertEquals(2, countOfType(targetStream, "sendLevelInfo", String.class));
        assertEquals(2, countOfType(targetStream, "sendPlayerPermissionLevel", String.class));
        assertEquals(2, countOfType(targetStream, "sendAllPlayerInfo", String.class));
        assertEquals(2, countOfType(targetStream, SPlayerAbilitiesPacket.class));
    }

    @Test
    @DisplayName("teleport reproduces the player's position/yaw/pitch (SPlayerPositionLookPacket args)")
    void teleportArgs_matchPlayerPosition() {
        // Give the player a non-trivial position so the teleport args are
        // meaningful; yRot/xRot are public Entity fields on the mock.
        when(target.position()).thenReturn(new Vector3d(10.5, 64.0, -3.25));
        target.yRot = 45f;
        target.xRot = 10f;

        refresh();

        assertEquals(1, teleportCalls.size(), "exactly one teleport per refresh");
        Object[] args = (Object[]) teleportCalls.get(0);
        assertEquals(10.5d, args[0]);
        assertEquals(64.0d, args[1]);
        assertEquals(-3.25d, args[2]);
        assertEquals(45f, args[3]);
        assertEquals(10f, args[4]);
    }

    @Test
    @DisplayName("Tracker untrack/retrack runs before the respawn packet (handler-side ordering)")
    void trackerUntrackRetrack_beforeRespawn() {
        // Use a tracking broadcaster to capture the call moment so we can
        // compare it against the respawn index on the captured stream.
        FakeSkinBroadcaster spyBroadcaster = new FakeSkinBroadcaster() {
            @Override
            public void trackerUntrackRetrack(Entity entity) {
                targetStream.add("trackerUntrackRetrack");
                super.trackerUntrackRetrack(entity);
            }
        };
        SkinRefreshHandler.setBroadcaster(spyBroadcaster);

        refresh();

        int tracker = indexOfType(targetStream, "trackerUntrackRetrack", String.class);
        int respawn = indexOfType(targetStream, SRespawnPacket.class);
        assertTrue(tracker >= 0 && respawn > tracker,
            "tracker must precede respawn; stream=" + targetStream);
    }

    @Test
    @DisplayName("Cascade order holds for all 8 BROADCAST_USE_BUNDLE x DIMENSION_SCOPED_BROADCAST x REFRESH_VIA_ENTITY_TRACKER combos")
    void configMatrix_allCombos_cascadeOrderHolds() {
        for (boolean bundle : new boolean[]{false, true}) {
            for (boolean scoped : new boolean[]{false, true}) {
                for (boolean tracker : new boolean[]{false, true}) {
                    Config.BROADCAST_USE_BUNDLE.set(bundle);
                    Config.DIMENSION_SCOPED_BROADCAST.set(scoped);
                    Config.REFRESH_VIA_ENTITY_TRACKER.set(tracker);
                    refresh();

                    String combo = "bundle=" + bundle + ", scoped=" + scoped + ", tracker=" + tracker;
                    assertCascadeOrder(targetStream, combo);
                    assertEquals(1, fakeBroadcaster.broadcastCalls.size(),
                        combo + ": exactly one broadcast call");
                }
            }
        }
    }

    @Test
    @DisplayName("task() increments the refresh counter")
    void task_incrementsRefreshCount() {
        SkinRefreshHandler.resetRefreshTaskCount();
        refresh();

        assertEquals(1, SkinRefreshHandler.getRefreshTaskCount());
    }

    /* ================================================================== */
    /*  C. Cascade atomicity (persistence) contract                       */
    /* ================================================================== */

    @Test
    @DisplayName("saveSkinAsync failure leaves the applied GameProfile textures untouched")
    void saveSkinAsyncFailure_leavesProfileUntouched() throws Exception {
        GameProfile profile = profileWithTextures(OLD_PROPERTY);
        SkinStorage failing = mock(SkinStorage.class);
        when(failing.getSkin(any(UUID.class))).thenReturn(NEW_SKIN);
        doThrow(new RuntimeException("simulated disk enqueue failure"))
                .when(failing).saveSkinAsync(any(UUID.class), any(CustomSkinProperty.class));
        setStaticField(SkinRestorer.class, "skinStorage", failing);

        long failedBefore = SkinMetrics.INSTANCE.snapshot().refreshesFailed();
        long savesBefore = SkinMetrics.INSTANCE.snapshot().savesSubmitted();

        boolean persisted = SkinRefreshHandler.applyAtomicPersistence(PLAYER_UUID, profile, NEW_SKIN);

        assertFalse(persisted, "a failed enqueue must report failure");
        Collection<Property> textures = texturesOf(profile);
        assertEquals(1, textures.size(), "a failed save must not mutate the profile");
        assertEquals(OLD_PROPERTY, textures.iterator().next(),
                "the applied profile must keep the previous textures when persistence fails");
        assertEquals(failedBefore + 1, SkinMetrics.INSTANCE.snapshot().refreshesFailed(),
                "the partial cascade failure must be recorded");
        assertEquals(savesBefore, SkinMetrics.INSTANCE.snapshot().savesSubmitted(),
                "a failed enqueue must not count as a submitted save");
        assertEquals(0, fakeBroadcaster.broadcastCalls.size(),
            "broadcast must not run when persistence fails");
    }

    @Test
    @DisplayName("applyAtomicPersistence with a throwing profile mutation fails soft")
    void applyAtomicPersistence_mutateProfileThrowing_failsSoft() {
        GameProfile profile = mock(GameProfile.class);
        // Real PropertyMap subclass (Mockito spies on Guava Multimaps are
        // forbidden by @DoNotMock); only removeAll throws, everything else
        // delegates to the real backing map.
        PropertyMap properties = new PropertyMap() {
            @Override
            public Collection<Property> removeAll(Object key) {
                throw new RuntimeException("simulated profile mutation failure");
            }
        };
        properties.put("textures", OLD_PROPERTY);
        when(profile.getProperties()).thenReturn(properties);
        storage.setSkin(PLAYER_UUID, NEW_SKIN);
        long failedBefore = SkinMetrics.INSTANCE.snapshot().refreshesFailed();

        boolean persisted = SkinRefreshHandler.applyAtomicPersistence(PLAYER_UUID, profile, NEW_SKIN);

        assertFalse(persisted, "a failed profile mutation must report failure");
        assertEquals(failedBefore + 1, SkinMetrics.INSTANCE.snapshot().refreshesFailed(),
                "the failed mutation must be recorded");
        Collection<Property> textures = texturesOf(profile);
        assertEquals(1, textures.size(), "a failed mutation must leave the profile untouched");
        assertEquals(OLD_PROPERTY, textures.iterator().next(),
                "the applied profile must keep its previous textures");
    }

    @Test
    @DisplayName("successful save applies the stored skin to the GameProfile")
    void saveSkinAsyncSuccess_appliesStoredSkin() throws Exception {
        GameProfile profile = profileWithTextures(OLD_PROPERTY);
        storage.setSkin(PLAYER_UUID, NEW_SKIN);

        long failedBefore = SkinMetrics.INSTANCE.snapshot().refreshesFailed();
        long savesBefore = SkinMetrics.INSTANCE.snapshot().savesSubmitted();

        boolean persisted = SkinRefreshHandler.applyAtomicPersistence(PLAYER_UUID, profile, NEW_SKIN);
        skinIO.flushPending();

        assertTrue(persisted, "a successful enqueue must report success");
        Collection<Property> textures = texturesOf(profile);
        assertEquals(1, textures.size());
        assertEquals(NEW_SKIN.getOriginalProperty(), textures.iterator().next(),
                "the applied profile must match the saved skin value");
        assertEquals(failedBefore, SkinMetrics.INSTANCE.snapshot().refreshesFailed(),
                "a successful refresh must not record a failure");
        assertEquals(savesBefore + 1, SkinMetrics.INSTANCE.snapshot().savesSubmitted(),
                "the cascade must enqueue exactly one save");
    }

    @Test
    @DisplayName("restart equivalence: after the cascade the on-disk skin equals the in-memory skin")
    void afterCascade_ondiskSkinEqualsInMemorySkin() throws Exception {
        GameProfile profile = new GameProfile(PLAYER_UUID, "Alice");
        storage.setSkin(PLAYER_UUID, NEW_SKIN);

        boolean persisted = SkinRefreshHandler.applyAtomicPersistence(PLAYER_UUID, profile, NEW_SKIN);
        skinIO.flushPending();

        assertTrue(persisted, "the cascade must report success");
        CustomSkinProperty fromDisk = skinIO.loadSkin(PLAYER_UUID);
        assertNotNull(fromDisk, "the cascade must have persisted the skin to disk");
        assertEquals(NEW_SKIN, fromDisk,
                "the on-disk skin after the cascade must equal the stored in-memory skin");
        assertEquals(storage.getSkin(PLAYER_UUID), fromDisk,
                "a restart reloading from disk must reproduce the in-memory skin");
    }

    /* ================================================================== */
    /*  D. /skin clear with no Mojang profile                                */
    /* ================================================================== */

    @Test
    @DisplayName("clear path also invokes the broadcaster and the cascade")
    void clearPath_invokesBroadcasterAndCascade() {
        // Stored skin is null: drop the applied textures property and
        // run the broadcast+cascade so observers re-learn the cleared
        // profile and the target reverts to the default skin.
        storage.setSkin(target.getUUID(), null);
        refresh();

        assertEquals(1, fakeBroadcaster.broadcastCalls.size(),
            "clear path also broadcasts; calls=" + fakeBroadcaster.broadcastCalls);
        assertEquals(1, fakeBroadcaster.trackerCalls.size(),
            "clear path also retracks");
        assertCascadeOrder(targetStream);
    }

    /* ================================================================== */
    /*  E. Wire-up verification                                            */
    /* ================================================================== */

    @Test
    @DisplayName("getBroadcaster returns the wired instance")
    void broadcasterGetterReturnsWired() {
        assertEquals(fakeBroadcaster, SkinRefreshHandler.getBroadcaster(),
            "the broadcaster the handler uses must be the one we injected");
    }

    /* ================================================================== */
    /*  F. Pure logic: tryRestoreFromMojang + deriveReason                 */
    /* ================================================================== */

    @Nested
    @DisplayName("tryRestoreFromMojang")
    static class TryRestoreFromMojang {

        private static final String PLAYER_NAME = "Steve";
        private static final String STORED_SOURCE = "Notch";
        private static final String FAKE_VALUE = "validTextureValue";
        private static final String FAKE_SIG = "validSignature";

        static class FakeMojangAPI implements MojangAPI {
            final Map<String, CustomSkinProperty> skins = new HashMap<>();

            void addSkin(String name, CustomSkinProperty skin) {
                skins.put(name.toLowerCase(), skin);
            }

            @Override
            public Optional<MojangSkinDataResult> getSkin(String nameOrUniqueId) {
                CustomSkinProperty skin = skins.get(nameOrUniqueId.toLowerCase());
                if (skin == null) return Optional.empty();
                return Optional.of(new MojangSkinDataResult(UUID.randomUUID(), skin));
            }

            @Override
            public Optional<UUID> getUUID(String playerName) {
                return Optional.empty();
            }

            @Override
            public Optional<CustomSkinProperty> getProfile(ProfileLookup lookup) {
                return Optional.empty();
            }
        }

        @Test
        @DisplayName("returns skin when Mojang has a profile for storedSource")
        void restoreFromStoredSource() {
            FakeMojangAPI api = new FakeMojangAPI();
            api.addSkin(STORED_SOURCE, new CustomSkinProperty(FAKE_VALUE, FAKE_SIG, STORED_SOURCE));

            SkinRefreshHandler.MojangRestoreResult result =
                SkinRefreshHandler.tryRestoreFromMojang(api, STORED_SOURCE, PLAYER_NAME);

            assertNotNull(result);
            assertEquals(FAKE_VALUE, result.skin.getOriginalProperty().getValue());
            assertEquals(STORED_SOURCE, result.licensedUsername);
        }

        @Test
        @DisplayName("uses playerName when storedSource is null")
        void fallbackFromNullSource() {
            FakeMojangAPI api = new FakeMojangAPI();
            api.addSkin(PLAYER_NAME, new CustomSkinProperty(FAKE_VALUE, FAKE_SIG, PLAYER_NAME));

            SkinRefreshHandler.MojangRestoreResult result =
                SkinRefreshHandler.tryRestoreFromMojang(api, null, PLAYER_NAME);

            assertNotNull(result);
            assertEquals(FAKE_VALUE, result.skin.getOriginalProperty().getValue());
            assertEquals(PLAYER_NAME, result.licensedUsername);
        }

        @Test
        @DisplayName("uses playerName when storedSource is empty")
        void fallbackFromEmptySource() {
            FakeMojangAPI api = new FakeMojangAPI();
            api.addSkin(PLAYER_NAME, new CustomSkinProperty(FAKE_VALUE, FAKE_SIG, PLAYER_NAME));

            SkinRefreshHandler.MojangRestoreResult result =
                SkinRefreshHandler.tryRestoreFromMojang(api, "", PLAYER_NAME);

            assertNotNull(result);
            assertEquals(FAKE_VALUE, result.skin.getOriginalProperty().getValue());
            assertEquals(PLAYER_NAME, result.licensedUsername);
        }

        @Test
        @DisplayName("returns null when Mojang has no profile for the username")
        void mojangNoProfile() {
            FakeMojangAPI api = new FakeMojangAPI();

            SkinRefreshHandler.MojangRestoreResult result =
                SkinRefreshHandler.tryRestoreFromMojang(api, STORED_SOURCE, PLAYER_NAME);

            assertNull(result);
        }

        @Test
        @DisplayName("returns null when Mojang skin isEmpty (default skin value)")
        void mojangEmptySkin() {
            CustomSkinProperty emptySkin = new CustomSkinProperty("textures", "", "", STORED_SOURCE);
            FakeMojangAPI api = new FakeMojangAPI();
            api.addSkin(STORED_SOURCE, emptySkin);

            SkinRefreshHandler.MojangRestoreResult result =
                SkinRefreshHandler.tryRestoreFromMojang(api, STORED_SOURCE, PLAYER_NAME);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("deriveReason")
    static class DeriveReason {

        @Test
        @DisplayName("username with no custom source uses the plain message")
        void username_nullSource() {
            assertEquals("No skin found", SkinRefreshHandler.deriveReason(SkinActionType.username, null));
        }

        @Test
        @DisplayName("username with a custom source names the source")
        void username_withSource() {
            assertEquals("No skin found for \"Notch\"",
                SkinRefreshHandler.deriveReason(SkinActionType.username, "Notch"));
        }

        @Test
        @DisplayName("url with a non-namemc source reports MineSkin rejection")
        void url_nonNameMcSource() {
            assertEquals("MineSkin rejected the URL",
                SkinRefreshHandler.deriveReason(SkinActionType.url, "https://example.com/skin.png"));
        }

        @Test
        @DisplayName("url with a namemc profile source is sanitized to the username")
        void url_nameMcProfileSource() {
            // sanitizeSkinInput rewrites the namemc profile URL to the
            // username; deriveReason then reports that username.
            assertEquals("No skin found for \"Notch\"",
                SkinRefreshHandler.deriveReason(SkinActionType.url, "https://namemc.com/profile/Notch.1"));
        }

        @Test
        @DisplayName("random uses the no-random-username message")
        void random() {
            assertEquals("No random username available",
                SkinRefreshHandler.deriveReason(SkinActionType.random, null));
        }
    }

    /* ================================================================== */
    /*  Fixture                                                            */
    /* ================================================================== */

    /** Runs one refresh and resets all captured streams first. */
    private void refresh() {
        resetStreams();
        fakeBroadcaster.reset();
        SkinRefreshHandler.task(target);
    }

    private void resetStreams() {
        targetStream.clear();
        teleportCalls.clear();
    }

    private ServerPlayerEntity newPlayer(String name, ServerWorld level) {
        UUID uuid = UUID.randomUUID();
        ServerPlayerEntity player = mock(ServerPlayerEntity.class);
        when(player.getUUID()).thenReturn(uuid);
        when(player.getGameProfile()).thenReturn(new GameProfile(uuid, name));
        when(player.getLevel()).thenReturn(level);
        when(player.position()).thenReturn(Vector3d.ZERO);
        when(player.getServer()).thenReturn(server);

        // Field-backed state production reads directly (mock fields are null
        // because Mockito skips constructors).
        ServerPlayNetHandler connection = mock(ServerPlayNetHandler.class);
        setField(player, "connection", connection);
        PlayerInteractionManager gameMode = mock(PlayerInteractionManager.class);
        when(gameMode.getGameModeForPlayer()).thenReturn(GameType.SURVIVAL);
        when(gameMode.getPreviousGameModeForPlayer()).thenReturn(GameType.SURVIVAL);
        setField(player, "gameMode", gameMode);
        setField(player, "abilities", new PlayerAbilities());
        return player;
    }

    /**
     * Ordered capture of everything sent to this player's connection.
     * Idempotent per player: the capture doAnswer is attached once and the
     * same list is returned on every call.
     */
    private List<Object> streamFor(ServerPlayerEntity player) {
        return playerStreams.computeIfAbsent(player, p -> {
            List<Object> stream = new ArrayList<>();
            doAnswer(inv -> {
                stream.add(inv.getArgument(0));
                return null;
            }).when(p.connection).send(any(IPacket.class));
            doAnswer(inv -> {
                // 1.16.5: the position packet travels inside
                // ServerPlayNetHandler#teleport; capture its arguments and
                // emit a stream marker in the vanilla respawn position.
                teleportCalls.add(inv.getArguments());
                stream.add("teleport");
                return null;
            }).when(p.connection).teleport(anyDouble(), anyDouble(), anyDouble(), anyFloat(), anyFloat());
            return stream;
        });
    }

    private static ServerWorld levelMock(RegistryKey<World> dimension) {
        ServerWorld level = mock(ServerWorld.class);
        when(level.dimension()).thenReturn(dimension);
        when(level.dimensionType()).thenReturn(mock(DimensionType.class));
        when(level.getSeed()).thenReturn(0L);
        when(level.isDebug()).thenReturn(false);
        when(level.isFlat()).thenReturn(false);
        return level;
    }

    private static void assertCascadeOrder(List<Object> stream) {
        assertCascadeOrder(stream, "");
    }

    private static void assertCascadeOrder(List<Object> stream, String combo) {
        int respawn = indexOfType(stream, SRespawnPacket.class);
        int teleport = indexOfType(stream, "teleport", String.class);
        int levelInfo = indexOfType(stream, "sendLevelInfo", String.class);
        int permission = indexOfType(stream, "sendPlayerPermissionLevel", String.class);
        int allPlayerInfo = indexOfType(stream, "sendAllPlayerInfo", String.class);
        int abilities = indexOfType(stream, SPlayerAbilitiesPacket.class);
        assertTrue(respawn >= 0 && teleport > respawn && levelInfo > teleport
                && permission > levelInfo && allPlayerInfo > permission && abilities > allPlayerInfo,
            combo + ": expected respawn < teleport < levelInfo < permission < allPlayerInfo < abilities; stream=" + stream);
    }

    private static int indexOfType(List<?> events, Class<?> type) {
        for (int i = 0; i < events.size(); i++) {
            if (type.isInstance(events.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfType(List<?> events, Object marker, Class<?> type) {
        for (int i = 0; i < events.size(); i++) {
            if (type.isInstance(events.get(i)) && events.get(i).equals(marker)) {
                return i;
            }
        }
        return -1;
    }

    private static long countOfType(List<?> events, Class<?> type) {
        return events.stream().filter(type::isInstance).count();
    }

    private static long countOfType(List<?> events, Object marker, Class<?> type) {
        return events.stream().filter(e -> type.isInstance(e) && e.equals(marker)).count();
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = ServerPlayerEntity.class.getField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set ServerPlayerEntity." + fieldName, e);
        }
    }

    private static void setStaticField(Class<?> clazz, String name, Object value) throws Exception {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static GameProfile profileWithTextures(Property textures) {
        GameProfile profile = new GameProfile(PLAYER_UUID, "Alice");
        profile.getProperties().put("textures", textures);
        return profile;
    }

    private static Collection<Property> texturesOf(GameProfile profile) {
        return profile.getProperties().get("textures");
    }
}
