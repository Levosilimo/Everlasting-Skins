/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import com.mojang.authlib.GameProfile;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.core.Holder;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Broadcast/cascade ordering contract of the 1.21 refresh pipeline
 * ({@link SkinRefreshHandler#task}). Pure Mockito unit test: players,
 * server, player list and level are mocks; the packet stream is captured
 * per connection in send order and PlayerList helpers are replaced by
 * faithful seams that emit the vanilla packet they would send
 * (difficulty via sendLevelInfo, permission-level via
 * sendPlayerPermissionLevel).
 *
 * <p>Contract pinned here:
 * <ul>
 *   <li>REMOVE is sent strictly before ADD; in non-bundle mode they are
 *       neighbors in the captured stream, in bundle mode they travel inside
 *       one ClientboundBundlePacket (atomicity).</li>
 *   <li>Cascade order on the target connection:
 *       REMOVE, ADD, respawn, position, difficulty, permission, abilities —
 *       each packet type exactly once per refresh.</li>
 *   <li>Tracker untrack/retrack (chunk-source removeEntity/addEntity) runs
 *       before the respawn packet, and only when
 *       REFRESH_VIA_ENTITY_TRACKER is enabled.</li>
 *   <li>The full cascade order holds under every combination of
 *       BROADCAST_USE_BUNDLE x DIMENSION_SCOPED_BROADCAST x
 *       REFRESH_VIA_ENTITY_TRACKER.</li>
 *   <li>Self-reception and multi-observer delivery, with cross-dimension
 *       observers excluded when DIMENSION_SCOPED_BROADCAST is enabled.</li>
 * </ul>
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
    private static final ResourceKey<Level> NETHER = Level.NETHER;

    @TempDir
    Path tempDir;

    private SkinIO skinIO;
    private SkinStorage storage;

    private MinecraftServer server;
    private PlayerList playerlist;
    private ServerLevel overworldLevel;
    private ServerLevel netherLevel;
    private ServerChunkCache chunkSource;

    private ServerPlayer target;
    private ServerPlayer observer1;
    private ServerPlayer observer2;
    private ServerPlayer netherObserver;
    private UUID targetUuid;

    /** Ordered per-connection streams (packets + PlayerList-seam markers). */
    private final Map<ServerPlayer, List<Object>> playerStreams = new HashMap<>();
    private List<Object> targetStream;
    private List<Object> observer1Stream;
    private List<Object> observer2Stream;
    private List<Object> netherStream;
    private List<Packet<?>> globalSink;
    private List<String> chunkTrace;

    @BeforeEach
    void setUp() {
        // Make the ForgeConfigSpec usable outside a loaded config file
        // (same pattern as ConfigTest).
        Config.COMMON_CONFIG.setConfig(
                InMemoryCommentedFormat.defaultInstance().createConfig(HashMap::new));
        Config.BROADCAST_USE_BUNDLE.set(false);
        Config.DIMENSION_SCOPED_BROADCAST.set(false);
        Config.REFRESH_VIA_ENTITY_TRACKER.set(true);

        skinIO = new SkinIO(tempDir);
        storage = new SkinStorage(skinIO);
        setStaticField(SkinRestorer.class, "skinStorage", storage);

        playerStreams.clear();
        globalSink = new ArrayList<>();
        chunkTrace = new ArrayList<>();

        overworldLevel = levelMock(OVERWORLD);
        netherLevel = levelMock(NETHER);
        chunkSource = mock(ServerChunkCache.class);
        doAnswer(inv -> { chunkTrace.add("removeEntity"); return null; })
            .when(chunkSource).removeEntity(any(Entity.class));
        doAnswer(inv -> { chunkTrace.add("addEntity"); return null; })
            .when(chunkSource).addEntity(any(Entity.class));
        when(overworldLevel.getChunkSource()).thenReturn(chunkSource);
        when(netherLevel.getChunkSource()).thenReturn(chunkSource);

        playerlist = mock(PlayerList.class);
        server = mock(MinecraftServer.class);
        when(server.getPlayerList()).thenReturn(playerlist);
        SkinRestorer.server = server;

        target = newPlayer("Target", overworldLevel);
        observer1 = newPlayer("ObserverOne", overworldLevel);
        observer2 = newPlayer("ObserverTwo", overworldLevel);
        netherObserver = newPlayer("NetherObserver", netherLevel);
        targetUuid = target.getUUID();

        List<ServerPlayer> online = List.of(target, observer1, observer2, netherObserver);
        when(playerlist.getPlayers()).thenReturn(online);

        targetStream = streamFor(target);
        observer1Stream = streamFor(observer1);
        observer2Stream = streamFor(observer2);
        netherStream = streamFor(netherObserver);

        // Non-bundle broadcast: records into the global sink and delivers to
        // every online player's connection (vanilla PlayerList.broadcastAll).
        doAnswer(inv -> {
            Packet<?> packet = inv.getArgument(0);
            globalSink.add(packet);
            for (ServerPlayer onlinePlayer : online) {
                streamFor(onlinePlayer).add(packet);
            }
            return null;
        }).when(playerlist).broadcastAll(any(Packet.class));

        // Dimension-scoped broadcast: same, filtered by the player's level.
        doAnswer(inv -> {
            Packet<?> packet = inv.getArgument(0);
            ResourceKey<Level> dim = inv.getArgument(1);
            globalSink.add(packet);
            for (ServerPlayer onlinePlayer : online) {
                if (onlinePlayer.serverLevel().dimension().equals(dim)) {
                    streamFor(onlinePlayer).add(packet);
                }
            }
            return null;
        }).when(playerlist).broadcastAll(any(Packet.class), any(ResourceKey.class));

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

        storage.setSkin(targetUuid,
                new CustomSkinProperty("textures", TEXTURE_VALUE, TEXTURE_SIGNATURE, "Notch"));
    }

    @AfterEach
    void tearDown() {
        Config.BROADCAST_USE_BUNDLE.set(false);
        Config.DIMENSION_SCOPED_BROADCAST.set(false);
        Config.REFRESH_VIA_ENTITY_TRACKER.set(true);
        SkinRestorer.server = null;
        skinIO.flushPending();
    }

    /* ================================================================== */
    /*  A. Packet sequence contract                                       */
    /* ================================================================== */

    @Test
    @DisplayName("REMOVE is sent strictly before ADD (non-bundle broadcast)")
    void removeStrictlyBeforeAdd() {
        refresh();

        assertEquals(2, globalSink.size(), "exactly one REMOVE + one ADD broadcast expected");
        assertTrue(globalSink.get(0) instanceof ClientboundPlayerInfoRemovePacket,
            "first broadcast must be REMOVE; sink=" + globalSink);
        assertTrue(globalSink.get(1) instanceof ClientboundPlayerInfoUpdatePacket,
            "second broadcast must be ADD; sink=" + globalSink);

        int removeIdx = indexOfType(targetStream, ClientboundPlayerInfoRemovePacket.class);
        int addIdx = indexOfType(targetStream, ClientboundPlayerInfoUpdatePacket.class);
        assertTrue(removeIdx >= 0 && addIdx > removeIdx,
            "REMOVE must strictly precede ADD; stream=" + targetStream);
    }

    @Test
    @DisplayName("REMOVE+ADD are neighbors in the captured stream (non-bundle)")
    void removeAddAreNeighbors() {
        refresh();

        int removeIdx = indexOfType(targetStream, ClientboundPlayerInfoRemovePacket.class);
        int addIdx = indexOfType(targetStream, ClientboundPlayerInfoUpdatePacket.class);
        assertEquals(removeIdx + 1, addIdx,
            "no packet may sit between REMOVE and ADD; stream=" + targetStream);
    }

    @Test
    @DisplayName("Bundle mode: REMOVE+ADD travel inside one ClientboundBundlePacket (atomicity)")
    void bundleAtomicity_removeAddInSingleBundle() {
        Config.BROADCAST_USE_BUNDLE.set(true);
        refresh();

        List<ClientboundBundlePacket> bundles = filterType(targetStream, ClientboundBundlePacket.class);
        assertEquals(1, bundles.size(), "exactly one bundle expected; stream=" + targetStream);
        List<Packet<?>> inner = new ArrayList<>();
        bundles.get(0).subPackets().forEach(inner::add);
        assertEquals(2, inner.size(), "bundle must contain exactly REMOVE+ADD; inner=" + inner);
        assertTrue(inner.get(0) instanceof ClientboundPlayerInfoRemovePacket,
            "bundle[0] must be REMOVE; inner=" + inner);
        assertTrue(inner.get(1) instanceof ClientboundPlayerInfoUpdatePacket,
            "bundle[1] must be ADD; inner=" + inner);
        // Atomicity: no standalone info packets outside the bundle.
        assertEquals(0, countOfType(targetStream, ClientboundPlayerInfoRemovePacket.class),
            "no standalone REMOVE outside the bundle");
        assertEquals(0, countOfType(targetStream, ClientboundPlayerInfoUpdatePacket.class),
            "no standalone ADD outside the bundle");
    }

    @Test
    @DisplayName("Cascade order: REMOVE, ADD, respawn, position, difficulty, permission, abilities")
    void cascadeOrderFullStream() {
        refresh();

        assertCascadeOrder(targetStream);
    }

    @Test
    @DisplayName("Each packet type is sent exactly once per refresh")
    void eachPacketTypeExactlyOnce() {
        // refresh() clears between runs, so drive task() directly to
        // accumulate two refreshes in one captured stream.
        resetStreams();
        SkinRefreshHandler.task(target);
        SkinRefreshHandler.task(target);

        // Two refreshes: every type appears exactly twice total (lib-22
        // matcher-bugfix guard: no duplicate sends).
        assertEquals(2, countOfType(targetStream, ClientboundPlayerInfoRemovePacket.class));
        assertEquals(2, countOfType(targetStream, ClientboundPlayerInfoUpdatePacket.class));
        assertEquals(2, countOfType(targetStream, ClientboundRespawnPacket.class));
        assertEquals(2, countOfType(targetStream, ClientboundPlayerPositionPacket.class));
        assertEquals(2, countOfType(targetStream, ClientboundChangeDifficultyPacket.class));
        assertEquals(2, countOfType(targetStream, ClientboundEntityEventPacket.class));
        assertEquals(2, countOfType(targetStream, ClientboundPlayerAbilitiesPacket.class));
    }

    @Test
    @DisplayName("Tracker untrack/retrack runs before the respawn packet")
    void trackerUntrackRetrack_beforeRespawn() {
        refresh();

        assertEquals(List.of("removeEntity", "addEntity"), chunkTrace,
            "chunk-source untrack must precede retrack");
        // The tracker step is part of recordObserverBroadcast, which runs
        // before recordCascade: removeEntity, addEntity, then the respawn
        // send on the connection.
        org.mockito.InOrder order = inOrder(chunkSource, target.connection);
        order.verify(chunkSource).removeEntity(target);
        order.verify(chunkSource).addEntity(target);
        order.verify(target.connection).send(any(ClientboundRespawnPacket.class));
    }

    @Test
    @DisplayName("Tracker is never touched when REFRESH_VIA_ENTITY_TRACKER is disabled")
    void trackerSkipped_whenDisabled() {
        Config.REFRESH_VIA_ENTITY_TRACKER.set(false);
        refresh();

        assertEquals(Collections.emptyList(), chunkTrace, "no chunk-source interaction expected");
        verify(chunkSource, never()).removeEntity(target);
        verify(chunkSource, never()).addEntity(target);
        // The cascade itself must be unaffected.
        assertCascadeOrder(targetStream);
    }

    /* ================================================================== */
    /*  B. Config flag matrix                                             */
    /* ================================================================== */

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
                    if (bundle) {
                        assertEquals(1, countOfType(targetStream, ClientboundBundlePacket.class),
                            combo + ": exactly one bundle");
                    } else {
                        assertEquals(1, countOfType(targetStream, ClientboundPlayerInfoRemovePacket.class),
                            combo + ": exactly one REMOVE");
                        assertEquals(1, countOfType(targetStream, ClientboundPlayerInfoUpdatePacket.class),
                            combo + ": exactly one ADD");
                    }
                    assertEquals(tracker ? List.of("removeEntity", "addEntity") : Collections.emptyList(),
                        chunkTrace, combo + ": tracker contract");
                }
            }
        }
    }

    @Test
    @DisplayName("DIMENSION_SCOPED_BROADCAST=true: cross-dimension observers receive nothing (bundle and non-bundle)")
    void dimensionScoped_netherObserverExcluded() {
        Config.DIMENSION_SCOPED_BROADCAST.set(true);
        for (boolean bundle : new boolean[]{false, true}) {
            Config.BROADCAST_USE_BUNDLE.set(bundle);
            refresh();

            assertTrue(netherStream.isEmpty(),
                "bundle=" + bundle + ": nether observer must receive nothing; stream=" + netherStream);
            // Same-dimension observers still receive.
            if (bundle) {
                assertEquals(1, countOfType(observer1Stream, ClientboundBundlePacket.class),
                    "bundle=" + bundle + ": same-dimension observer must get the bundle");
            } else {
                assertEquals(1, countOfType(observer1Stream, ClientboundPlayerInfoRemovePacket.class),
                    "bundle=" + bundle + ": same-dimension observer must get REMOVE");
                assertEquals(1, countOfType(observer1Stream, ClientboundPlayerInfoUpdatePacket.class),
                    "bundle=" + bundle + ": same-dimension observer must get ADD");
            }
        }
    }

    @Test
    @DisplayName("DIMENSION_SCOPED_BROADCAST=false: cross-dimension observers receive (bundle and non-bundle)")
    void dimensionScoped_netherObserverReceives() {
        Config.DIMENSION_SCOPED_BROADCAST.set(false);
        for (boolean bundle : new boolean[]{false, true}) {
            Config.BROADCAST_USE_BUNDLE.set(bundle);
            refresh();

            if (bundle) {
                assertEquals(1, countOfType(netherStream, ClientboundBundlePacket.class),
                    "bundle=" + bundle + ": nether observer must get the bundle");
            } else {
                assertEquals(1, countOfType(netherStream, ClientboundPlayerInfoRemovePacket.class),
                    "bundle=" + bundle + ": nether observer must get REMOVE");
                assertEquals(1, countOfType(netherStream, ClientboundPlayerInfoUpdatePacket.class),
                    "bundle=" + bundle + ": nether observer must get ADD");
            }
        }
    }

    /* ================================================================== */
    /*  C. Multi-observer + multi-target                                   */
    /* ================================================================== */

    /*  Fixture                                                            */
    /* ================================================================== */

    /** Runs one refresh and resets all captured streams first. */
    private void refresh() {
        resetStreams();
        SkinRefreshHandler.task(target);
    }

    private void resetStreams() {
        targetStream.clear();
        observer1Stream.clear();
        observer2Stream.clear();
        netherStream.clear();
        globalSink.clear();
        chunkTrace.clear();
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
     * same list is returned on every call (broadcast seams call this per
     * delivered packet).
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
        int removeIdx = indexOfType(stream, ClientboundPlayerInfoRemovePacket.class);
        int addIdx = indexOfType(stream, ClientboundPlayerInfoUpdatePacket.class);
        int respawn = indexOfType(stream, ClientboundRespawnPacket.class);
        int position = indexOfType(stream, ClientboundPlayerPositionPacket.class);
        int difficulty = indexOfType(stream, ClientboundChangeDifficultyPacket.class);
        int permission = indexOfType(stream, ClientboundEntityEventPacket.class);
        int abilities = indexOfType(stream, ClientboundPlayerAbilitiesPacket.class);
        // In bundle mode the REMOVE/ADD indices are -1 (they live inside the
        // bundle, asserted by the caller); require only the cascade tail
        // relative order plus broadcast-before-respawn.
        assertTrue(respawn >= 0 && position > respawn && difficulty > position
                && permission > difficulty && abilities > permission,
            combo + ": expected respawn < position < difficulty < permission < abilities; stream=" + stream);
        assertTrue(respawn > Math.max(removeIdx, addIdx),
            combo + ": broadcast (REMOVE/ADD or bundle) must precede the respawn; stream=" + stream);
    }

    private static void assertRemoveAddPair(List<Object> stream, String who) {
        int removeIdx = indexOfType(stream, ClientboundPlayerInfoRemovePacket.class);
        int addIdx = indexOfType(stream, ClientboundPlayerInfoUpdatePacket.class);
        assertTrue(removeIdx >= 0 && addIdx == removeIdx + 1,
            who + " must receive REMOVE then ADD as neighbors; stream=" + stream);
    }

    private static void assertAddCarriesNewTexture(List<Object> stream) {
        ClientboundPlayerInfoUpdatePacket add = filterType(stream, ClientboundPlayerInfoUpdatePacket.class)
                .stream()
                .filter(p -> p.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER))
                .findFirst()
                .orElse(null);
        assertTrue(add != null, "ADD packet missing from stream=" + stream);
        boolean hasTexture = add.entries().stream()
                .anyMatch(e -> e.profile() != null
                        && e.profile().getProperties().get("textures").stream()
                                .anyMatch(prop -> TEXTURE_VALUE.equals(prop.value())));
        assertTrue(hasTexture, "ADD packet must carry the new textures property");
    }

    private static int indexOfType(List<?> events, Class<?> type) {
        for (int i = 0; i < events.size(); i++) {
            if (type.isInstance(events.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static long countOfType(List<?> events, Class<?> type) {
        return events.stream().filter(type::isInstance).count();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> filterType(List<?> events, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Object event : events) {
            if (type.isInstance(event)) {
                result.add((T) event);
            }
        }
        return result;
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

    private static void setStaticField(Class<?> type, String fieldName, Object value) {
        try {
            Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set " + type.getSimpleName() + "." + fieldName, e);
        }
    }
}
