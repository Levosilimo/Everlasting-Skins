/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.permission.TestConfigSupport;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Direct test of the production {@link VanillaSkinBroadcaster}: pins the
 * packet sequence the broadcaster emits for each combination of
 * {@code BROADCAST_USE_BUNDLE}, {@code DIMENSION_SCOPED_BROADCAST} and
 * the explicit-observer vs broadcast-all variants. These contract
 * guarantees are what {@link
 * levosilimo.everlastingskins.skinchanger.SkinRefreshHandler#task}
 * inherits by calling the broadcaster; the handler test asserts the
 * call shape, this one asserts the packets.
 */
class VanillaSkinBroadcasterTest {

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

    private MinecraftServer server;
    private PlayerList playerlist;
    private ServerLevel overworldLevel;
    private ServerLevel netherLevel;
    private ServerChunkCache chunkSource;

    private ServerPlayer target;
    private ServerPlayer observer1;
    private ServerPlayer netherObserver;

    private final List<Object> targetStream = new ArrayList<>();
    private final List<Object> observer1Stream = new ArrayList<>();
    private final List<Object> netherStream = new ArrayList<>();
    private final List<Packet<?>> globalSink = new ArrayList<>();
    private final List<String> chunkTrace = new ArrayList<>();

    private VanillaSkinBroadcaster broadcaster;

    @BeforeAll
    static void loadConfig() {
        TestConfigSupport.loadDefaults();
    }

    @BeforeEach
    void setUp() throws Exception {
        Config.COMMON_CONFIG.setConfig(
                InMemoryCommentedFormat.defaultInstance().createConfig(HashMap::new));
        Config.BROADCAST_USE_BUNDLE.set(false);
        Config.DIMENSION_SCOPED_BROADCAST.set(false);
        Config.REFRESH_VIA_ENTITY_TRACKER.set(true);

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
        netherObserver = newPlayer("NetherObserver", netherLevel);

        List<ServerPlayer> online = List.of(target, observer1, netherObserver);
        when(playerlist.getPlayers()).thenReturn(online);

        attachStream(target, targetStream);
        attachStream(observer1, observer1Stream);
        attachStream(netherObserver, netherStream);

        doAnswer(inv -> {
            Packet<?> packet = inv.getArgument(0);
            globalSink.add(packet);
            for (ServerPlayer onlinePlayer : online) {
                streamFor(onlinePlayer).add(packet);
            }
            return null;
        }).when(playerlist).broadcastAll(any(Packet.class));

        doAnswer(inv -> {
            Packet<?> packet = inv.getArgument(0);
            ResourceKey<Level> dim = inv.getArgument(1);
            globalSink.add(packet);
            for (ServerPlayer onlinePlayer : online) {
                if (onlinePlayer.level().dimension().equals(dim)) {
                    streamFor(onlinePlayer).add(packet);
                }
            }
            return null;
        }).when(playerlist).broadcastAll(any(Packet.class), any(ResourceKey.class));

        broadcaster = new VanillaSkinBroadcaster();
    }

    @AfterEach
    void tearDown() throws Exception {
        Config.BROADCAST_USE_BUNDLE.set(false);
        Config.DIMENSION_SCOPED_BROADCAST.set(false);
        Config.REFRESH_VIA_ENTITY_TRACKER.set(true);
        SkinRestorer.server = null;
    }

    /* ================================================================== */
    /*  Packet sequence contract                                          */
    /* ================================================================== */

    @Test
    @DisplayName("REMOVE is emitted strictly before ADD in non-bundle mode")
    void removeStrictlyBeforeAdd_nonBundle() {
        broadcaster.broadcastProfileChange(target.getGameProfile(), target);

        assertEquals(2, globalSink.size(), "exactly one REMOVE + one ADD");
        assertTrue(globalSink.get(0) instanceof ClientboundPlayerInfoRemovePacket,
            "first broadcast must be REMOVE; sink=" + globalSink);
        assertTrue(globalSink.get(1) instanceof ClientboundPlayerInfoUpdatePacket,
            "second broadcast must be ADD; sink=" + globalSink);
        int removeIdx = indexOfType(targetStream, ClientboundPlayerInfoRemovePacket.class);
        int addIdx = indexOfType(targetStream, ClientboundPlayerInfoUpdatePacket.class);
        assertTrue(removeIdx >= 0 && addIdx == removeIdx + 1,
            "REMOVE+ADD must be neighbors in the captured stream; stream=" + targetStream);
    }

    @Test
    @DisplayName("Bundle mode: REMOVE+ADD travel inside one ClientboundBundlePacket (atomicity)")
    void bundleAtomicity_removeAddInSingleBundle() {
        Config.BROADCAST_USE_BUNDLE.set(true);
        broadcaster.broadcastProfileChange(target.getGameProfile(), target);

        List<ClientboundBundlePacket> bundles = filterType(targetStream, ClientboundBundlePacket.class);
        assertEquals(1, bundles.size(), "exactly one bundle expected; stream=" + targetStream);
        List<Packet<?>> inner = new ArrayList<>();
        bundles.get(0).subPackets().forEach(inner::add);
        assertEquals(2, inner.size(), "bundle must contain exactly REMOVE+ADD; inner=" + inner);
        assertTrue(inner.get(0) instanceof ClientboundPlayerInfoRemovePacket,
            "bundle[0] must be REMOVE; inner=" + inner);
        assertTrue(inner.get(1) instanceof ClientboundPlayerInfoUpdatePacket,
            "bundle[1] must be ADD; inner=" + inner);
        assertEquals(0, countOfType(targetStream, ClientboundPlayerInfoRemovePacket.class),
            "no standalone REMOVE outside the bundle");
        assertEquals(0, countOfType(targetStream, ClientboundPlayerInfoUpdatePacket.class),
            "no standalone ADD outside the bundle");
    }

    @Test
    @DisplayName("Explicit-observer variant sends REMOVE+ADD only to the given observers")
    void explicitObserver_variantReachesOnlyGivenSet() {
        ServerPlayer[] onlyOverworld = new ServerPlayer[]{target, observer1};
        broadcaster.broadcastProfileChange(target.getGameProfile(), target, onlyOverworld);

        assertTrue(!observer1Stream.isEmpty(),
            "observer1 must receive the broadcast; stream=" + observer1Stream);
        assertEquals(0, netherStream.size(),
            "nether observer must receive nothing when not in the explicit set; stream=" + netherStream);
    }

    @Test
    @DisplayName("Explicit-observer variant with DIMENSION_SCOPED_BROADCAST still excludes other dimensions")
    void explicitObserver_dimScoped_excludesCrossDim() {
        Config.DIMENSION_SCOPED_BROADCAST.set(true);
        ServerPlayer[] allPlayers = new ServerPlayer[]{target, observer1, netherObserver};
        broadcaster.broadcastProfileChange(target.getGameProfile(), target, allPlayers);

        assertEquals(0, netherStream.size(),
            "nether observer must receive nothing when dim scoping is on; stream=" + netherStream);
        assertTrue(!observer1Stream.isEmpty(),
            "same-dim observer must receive the broadcast; stream=" + observer1Stream);
    }

    @Test
    @DisplayName("DIMENSION_SCOPED_BROADCAST=true excludes nether observer in broadcast-all mode")
    void dimScoped_broadcastAll_excludesCrossDim() {
        Config.DIMENSION_SCOPED_BROADCAST.set(true);
        broadcaster.broadcastProfileChange(target.getGameProfile(), target);

        assertEquals(0, netherStream.size(),
            "nether observer must receive nothing; stream=" + netherStream);
        assertTrue(!observer1Stream.isEmpty(),
            "same-dim observer must receive the broadcast; stream=" + observer1Stream);
    }

    @Test
    @DisplayName("DIMENSION_SCOPED_BROADCAST=false delivers to all online players including nether")
    void dimScoped_false_deliversEverywhere() {
        Config.DIMENSION_SCOPED_BROADCAST.set(false);
        broadcaster.broadcastProfileChange(target.getGameProfile(), target);

        assertTrue(!netherStream.isEmpty(),
            "nether observer must receive the broadcast when not dim scoped; stream=" + netherStream);
        assertTrue(!observer1Stream.isEmpty(),
            "observer1 must receive the broadcast; stream=" + observer1Stream);
    }

    @Test
    @DisplayName("Bundle + DIMENSION_SCOPED_BROADCAST=true delivers the bundle only to same-dim observers")
    void bundleAndDimScoped_onlySameDimObservers() {
        Config.BROADCAST_USE_BUNDLE.set(true);
        Config.DIMENSION_SCOPED_BROADCAST.set(true);
        broadcaster.broadcastProfileChange(target.getGameProfile(), target);

        assertEquals(0, netherStream.size(),
            "nether observer must receive nothing; stream=" + netherStream);
        assertEquals(1, countOfType(observer1Stream, ClientboundBundlePacket.class),
            "observer1 must receive the bundle; stream=" + observer1Stream);
    }

    /* ================================================================== */
    /*  Tracker untrack/retrack                                           */
    /* ================================================================== */

    @Test
    @DisplayName("trackerUntrackRetrack drives removeEntity then addEntity when flag is on")
    void trackerUntrackRetrack_runsWhenEnabled() {
        broadcaster.trackerUntrackRetrack(target);

        assertEquals(List.of("removeEntity", "addEntity"), chunkTrace,
            "chunk-source untrack must precede retrack");
    }

    @Test
    @DisplayName("trackerUntrackRetrack is a no-op when REFRESH_VIA_ENTITY_TRACKER is off")
    void trackerUntrackRetrack_skippedWhenDisabled() {
        Config.REFRESH_VIA_ENTITY_TRACKER.set(false);
        broadcaster.trackerUntrackRetrack(target);

        assertEquals(Collections.emptyList(), chunkTrace,
            "no chunk-source interaction expected");
        verify(chunkSource, never()).removeEntity(any(Entity.class));
        verify(chunkSource, never()).addEntity(any(Entity.class));
    }

    @Test
    @DisplayName("trackerUntrackRetrack ignores non-ServerPlayer entities")
    void trackerUntrackRetrack_ignoresNonServerPlayer() {
        Entity other = mock(Entity.class);
        broadcaster.trackerUntrackRetrack(other);

        assertEquals(Collections.emptyList(), chunkTrace,
            "non-ServerPlayer entities must not be untracked");
    }

    /* ================================================================== */
    /*  ADD packet payload                                                 */
    /* ================================================================== */

    @Test
    @DisplayName("ADD packet carries the player's GameProfile")
    void addPacket_carriesTargetProfile() {
        // Add a textures property to the target's profile so the assertion
        // is non-trivial.
        target.getGameProfile().getProperties().put("textures",
            new Property("textures", TEXTURE_VALUE, TEXTURE_SIGNATURE));
        broadcaster.broadcastProfileChange(target.getGameProfile(), target);

        ClientboundPlayerInfoUpdatePacket add = filterType(targetStream, ClientboundPlayerInfoUpdatePacket.class)
            .stream()
            .filter(p -> p.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER))
            .findFirst()
            .orElse(null);
        assertNotNull(add, "ADD packet missing from stream=" + targetStream);
        boolean hasTexture = add.entries().stream()
            .anyMatch(e -> e.profile() != null
                && e.profile().getProperties().get("textures").stream()
                    .anyMatch(prop -> TEXTURE_VALUE.equals(prop.value())));
        assertTrue(hasTexture, "ADD packet must carry the textures property");
    }

    /* ================================================================== */
    /*  Helpers                                                            */
    /* ================================================================== */

    private ServerPlayer newPlayer(String name, ServerLevel level) {
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(uuid);
        when(player.getGameProfile()).thenReturn(new GameProfile(uuid, name));
        when(player.level()).thenReturn(level);
        // The 51.0.8-compatible broadcaster reads the level through
        // ServerPlayer.serverLevel() (Entity.level() is typed Level), so the
        // mock must stub the ServerLevel-typed accessor as well.
        when(player.serverLevel()).thenReturn(level);
        when(player.getTabListDisplayName()).thenReturn(null);
        when(player.getChatSession()).thenReturn(null);
        ServerGamePacketListenerImpl connection = mock(ServerGamePacketListenerImpl.class);
        setField(player, "connection", connection);
        when(player.getServer()).thenReturn(server);
        // ClientboundPlayerInfoUpdatePacket's Entry constructor reads
        // p_252094_.gameMode.getGameModeForPlayer(); field-back it with a
        // mock that returns SURVIVAL.
        ServerPlayerGameMode gameMode = mock(ServerPlayerGameMode.class);
        when(gameMode.getGameModeForPlayer()).thenReturn(GameType.SURVIVAL);
        setField(player, "gameMode", gameMode);
        return player;
    }

    private List<Object> streamFor(ServerPlayer player) {
        if (player == target) return targetStream;
        if (player == observer1) return observer1Stream;
        if (player == netherObserver) return netherStream;
        throw new IllegalArgumentException("unknown player: " + player);
    }

    private void attachStream(ServerPlayer player, List<Object> stream) {
        doAnswer(inv -> {
            stream.add(inv.getArgument(0));
            return null;
        }).when(player.connection).send(any(Packet.class));
    }

    private static ServerLevel levelMock(ResourceKey<Level> dimension) {
        ServerLevel level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(dimension);
        return level;
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
}
