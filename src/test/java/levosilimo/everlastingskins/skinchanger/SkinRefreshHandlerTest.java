/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.broadcast.FakeSkinBroadcaster;
import levosilimo.everlastingskins.broadcast.VanillaSkinBroadcaster;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.permission.TestConfigSupport;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Broadcast/cascade ordering contract of the 1.21 refresh pipeline
 * ({@link SkinRefreshHandler#task}). Pure Mockito unit test: players,
 * server, player list and level are mocks; the per-connection packet
 * stream (cascade respawn/position/difficulty/permission/abilities only)
 * is captured, and the broadcaster seam is exercised via
 * {@link FakeSkinBroadcaster} which records call shape without
 * re-implementing the REMOVE+ADD+cascade pipeline.
 *
 * <p>Contract pinned here:
 * <ul>
 *   <li>The handler invokes {@code broadcastProfileChange} once per refresh
 *       and then {@code trackerUntrackRetrack} when
 *       {@code REFRESH_VIA_ENTITY_TRACKER} is enabled, in that order.</li>
 *   <li>Cascade order on the target connection:
 *       respawn, position, difficulty, permission, abilities — each packet
 *       type exactly once per refresh.</li>
 *   <li>Cascade order holds under every combination of
 *       BROADCAST_USE_BUNDLE x DIMENSION_SCOPED_BROADCAST x
 *       REFRESH_VIA_ENTITY_TRACKER.</li>
 * </ul>
 *
 * <p>The packet-level REMOVE-before-ADD and bundle-atomicity guarantees
 * are pinned by {@link VanillaSkinBroadcasterTest} on the production
 * broadcaster itself; this test only asserts the handler's call shape.
 *
 * <p>Cascade atomicity invariant (R3): the disk flush must be enqueued BEFORE
 * the GameProfile is mutated, so a failed enqueue leaves the applied profile
 * untouched — applied and on-disk skin stay consistent and the change cannot
 * silently revert on the next server restart. The invariant lives in
 * {@link SkinRefreshHandler#applyAtomicPersistence} so it is unit-testable
 * without a ServerPlayer (1.21 entity classes need the FML runtime).
 */
class SkinRefreshHandlerTest {

    /**
     * The unit-test JVM has no running server, so vanilla's
     * BuiltInRegistries &lt;clinit&gt; would throw "Not bootstrapped" the moment
     * a registry-touching class (ServerLevel) is mocked. Flag the bootstrap
     * as done directly: calling {@link Bootstrap#bootStrap()} would also run
     * Forge's patched GameData.vanillaSnapshot(), which needs the FML
     * runtime this JVM does not have.
     */
    static {
        try {
            Field bootstrapFlag = Bootstrap.class.getDeclaredField("isBootstrapped");
            bootstrapFlag.setAccessible(true);
            bootstrapFlag.setBoolean(null, true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final String TEXTURE_VALUE = "cGF5bG9hZA==";
    private static final String TEXTURE_SIGNATURE = "c2lnbmF0dXJl";
    private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;

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
    private ServerLevel overworldLevel;
    private ServerChunkCache chunkSource;

    private ServerPlayer target;

    private FakeSkinBroadcaster fakeBroadcaster;

    private final Map<ServerPlayer, List<Object>> playerStreams = new HashMap<>();
    private List<Object> targetStream;

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

        playerStreams.clear();

        overworldLevel = levelMock(OVERWORLD);
        chunkSource = mock(ServerChunkCache.class);
        when(overworldLevel.getChunkSource()).thenReturn(chunkSource);

        playerlist = mock(PlayerList.class);
        server = mock(MinecraftServer.class);
        when(server.getPlayerList()).thenReturn(playerlist);
        SkinRestorer.server = server;

        target = newPlayer("Target", overworldLevel);

        targetStream = streamFor(target);

        // Faithful PlayerList seams: emit the packet vanilla would send at
        // this call site into the recipient's stream.
        doAnswer(inv -> {
            ServerPlayer player = inv.getArgument(0);
            streamFor(player).add(new ClientboundChangeDifficultyPacket(Difficulty.NORMAL, false));
            return null;
        }).when(playerlist).sendLevelInfo(any(ServerPlayer.class), any(ServerLevel.class));
        doAnswer(inv -> {
            ServerPlayer player = inv.getArgument(0);
            streamFor(player).add(new ClientboundEntityEventPacket(player, (byte) 26));
            return null;
        }).when(playerlist).sendPlayerPermissionLevel(any(ServerPlayer.class));
        doAnswer(inv -> {
            streamFor(inv.<ServerPlayer>getArgument(0)).add("sendAllPlayerInfo");
            return null;
        }).when(playerlist).sendAllPlayerInfo(any(ServerPlayer.class));
        doAnswer(inv -> {
            streamFor(inv.<ServerPlayer>getArgument(0)).add("sendActivePlayerEffects");
            return null;
        }).when(playerlist).sendActivePlayerEffects(any(ServerPlayer.class));

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
    @DisplayName("handler invokes broadcastProfileChange and skips trackerUntrackRetrack when flag disabled")
    void handlerCalls_broadcastOnly_whenTrackerDisabled() {
        Config.REFRESH_VIA_ENTITY_TRACKER.set(false);
        refresh();

        assertEquals(1, fakeBroadcaster.broadcastCalls.size(),
            "broadcast still runs; calls=" + fakeBroadcaster.broadcastCalls);
        assertEquals(Collections.emptyList(), fakeBroadcaster.trackerCalls,
            "tracker must be skipped when flag is disabled");
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
                    assertEquals(tracker ? 1 : 0, fakeBroadcaster.trackerCalls.size(),
                        combo + ": tracker contract");
                }
            }
        }
    }

    /* ================================================================== */
    /*  B. Cascade order on the target connection                          */
    /* ================================================================== */

    @Test
    @DisplayName("Cascade order: respawn, position, difficulty, permission, abilities")
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

        // Two refreshes: every cascade type appears exactly twice total
        // (lib-22 matcher-bugfix guard: no duplicate sends).
        assertEquals(2, countOfType(targetStream, ClientboundRespawnPacket.class));
        assertEquals(2, countOfType(targetStream, ClientboundPlayerPositionPacket.class));
        assertEquals(2, countOfType(targetStream, ClientboundChangeDifficultyPacket.class));
        assertEquals(2, countOfType(targetStream, ClientboundEntityEventPacket.class));
        assertEquals(2, countOfType(targetStream, ClientboundPlayerAbilitiesPacket.class));
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
        int respawn = indexOfType(targetStream, ClientboundRespawnPacket.class);
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
                .when(failing).saveSkinAsync(any(UUID.class));
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
    }

    private ServerPlayer newPlayer(String name, ServerLevel level) {
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(uuid);
        when(player.getGameProfile()).thenReturn(new GameProfile(uuid, name));
        when(player.serverLevel()).thenReturn(level);
        when(player.level()).thenReturn(level);
        when(player.position()).thenReturn(Vec3.ZERO);
        when(player.getYRot()).thenReturn(0f);
        when(player.getXRot()).thenReturn(0f);
        when(player.getAbilities()).thenReturn(new Abilities());
        // Precompute BEFORE when(): nested mock interactions during stubbing
        // (spawnInfo calls level.dimension()) trip Mockito's unfinished-
        // stubbing guard.
        CommonPlayerSpawnInfo info = spawnInfo(level);
        when(player.createCommonSpawnInfo(any(ServerLevel.class))).thenReturn(info);
        when(player.getTabListDisplayName()).thenReturn(null);
        when(player.getChatSession()).thenReturn(null);

        // Field-backed state production reads directly (mock fields are null
        // because Mockito skips constructors).
        ServerGamePacketListenerImpl connection = mock(ServerGamePacketListenerImpl.class);
        setField(player, "connection", connection);
        setField(player, "server", server);
        ServerPlayerGameMode gameMode = mock(ServerPlayerGameMode.class);
        when(gameMode.getGameModeForPlayer()).thenReturn(GameType.SURVIVAL);
        setField(player, "gameMode", gameMode);
        return player;
    }

    /**
     * Ordered capture of everything sent to this player's connection.
     * Idempotent per player: the capture doAnswer is attached once and the
     * same list is returned on every call.
     */
    private List<Object> streamFor(ServerPlayer player) {
        return playerStreams.computeIfAbsent(player, p -> {
            List<Object> stream = new ArrayList<>();
            doAnswer(inv -> {
                stream.add(inv.getArgument(0));
                return null;
            }).when(p.connection).send(any(Packet.class));
            return stream;
        });
    }

    private static ServerLevel levelMock(ResourceKey<Level> dimension) {
        ServerLevel level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(dimension);
        return level;
    }

    private static CommonPlayerSpawnInfo spawnInfo(ServerLevel level) {
        return new CommonPlayerSpawnInfo(
                mock(Holder.class), level.dimension(), 0L,
                GameType.SURVIVAL, GameType.SURVIVAL,
                false, false, Optional.empty(), 0);
    }

    private static void assertCascadeOrder(List<Object> stream) {
        assertCascadeOrder(stream, "");
    }

    private static void assertCascadeOrder(List<Object> stream, String combo) {
        int respawn = indexOfType(stream, ClientboundRespawnPacket.class);
        int position = indexOfType(stream, ClientboundPlayerPositionPacket.class);
        int difficulty = indexOfType(stream, ClientboundChangeDifficultyPacket.class);
        int permission = indexOfType(stream, ClientboundEntityEventPacket.class);
        int abilities = indexOfType(stream, ClientboundPlayerAbilitiesPacket.class);
        assertTrue(respawn >= 0 && position > respawn && difficulty > position
                && permission > difficulty && abilities > permission,
            combo + ": expected respawn < position < difficulty < permission < abilities; stream=" + stream);
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

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = ServerPlayer.class.getField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set ServerPlayer." + fieldName, e);
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
