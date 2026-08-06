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
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.network.play.server.SPlayerListItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
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
 * Direct test of the production 1.16.5 {@link VanillaSkinBroadcaster}: pins
 * the packet sequence the broadcaster emits for each combination of
 * {@code DIMENSION_SCOPED_BROADCAST} and the explicit-observer vs
 * broadcast-all variants.
 *
 * <p>1.16.5 delta vs the 1.21 lane (pinned here): there is no
 * ClientboundBundlePacket and no separate REMOVE packet — the unified
 * {@code SPlayerListItemPacket} carries both {@code REMOVE_PLAYER} and
 * {@code ADD_PLAYER} actions, and {@code BROADCAST_USE_BUNDLE} is inert
 * (the REMOVE+ADD pair is sent standalone either way).
 */
class VanillaSkinBroadcasterTest {

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
    private static final RegistryKey<World> NETHER = World.NETHER;

    private MinecraftServer server;
    private PlayerList playerlist;
    private ServerWorld overworldLevel;
    private ServerWorld netherLevel;
    private ServerChunkProvider chunkSource;

    private ServerPlayerEntity target;
    private ServerPlayerEntity observer1;
    private ServerPlayerEntity netherObserver;

    private final List<Object> targetStream = new ArrayList<>();
    private final List<Object> observer1Stream = new ArrayList<>();
    private final List<Object> netherStream = new ArrayList<>();
    private final List<IPacket<?>> globalSink = new ArrayList<>();
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
        chunkSource = mock(ServerChunkProvider.class);
        doAnswer(inv -> { chunkTrace.add("removeEntity"); return null; })
            .when(chunkSource).removeEntity(any(Entity.class));
        doAnswer(inv -> { chunkTrace.add("addEntity"); return null; })
            .when(chunkSource).addEntity(any(Entity.class));
        when(overworldLevel.getChunkSource()).thenReturn(chunkSource);
        when(netherLevel.getChunkSource()).thenReturn(chunkSource);

        playerlist = mock(PlayerList.class);
        server = mock(MinecraftServer.class);
        when(server.getPlayerList()).thenReturn(playerlist);

        target = newPlayer("Target", overworldLevel);
        observer1 = newPlayer("ObserverOne", overworldLevel);
        netherObserver = newPlayer("NetherObserver", netherLevel);

        List<ServerPlayerEntity> online = new ArrayList<>();
        online.add(target);
        online.add(observer1);
        online.add(netherObserver);
        when(playerlist.getPlayers()).thenReturn(online);

        attachStream(target, targetStream);
        attachStream(observer1, observer1Stream);
        attachStream(netherObserver, netherStream);

        doAnswer(inv -> {
            IPacket<?> packet = inv.getArgument(0);
            globalSink.add(packet);
            for (ServerPlayerEntity onlinePlayer : online) {
                streamFor(onlinePlayer).add(packet);
            }
            return null;
        }).when(playerlist).broadcastAll(any(IPacket.class));

        doAnswer(inv -> {
            IPacket<?> packet = inv.getArgument(0);
            @SuppressWarnings("unchecked")
            RegistryKey<World> dim = inv.getArgument(1);
            globalSink.add(packet);
            for (ServerPlayerEntity onlinePlayer : online) {
                if (onlinePlayer.getLevel().dimension().equals(dim)) {
                    streamFor(onlinePlayer).add(packet);
                }
            }
            return null;
        }).when(playerlist).broadcastAll(any(IPacket.class), any(RegistryKey.class));

        broadcaster = new VanillaSkinBroadcaster();
    }

    @AfterEach
    void tearDown() {
        Config.BROADCAST_USE_BUNDLE.set(false);
        Config.DIMENSION_SCOPED_BROADCAST.set(false);
        Config.REFRESH_VIA_ENTITY_TRACKER.set(true);
    }

    /* ================================================================== */
    /*  Packet sequence contract                                          */
    /* ================================================================== */

    @Test
    @DisplayName("REMOVE is emitted strictly before ADD (single SPlayerListItemPacket pair)")
    void removeStrictlyBeforeAdd() {
        broadcaster.broadcastProfileChange(target.getGameProfile(), target);

        assertEquals(2, globalSink.size(), "exactly one REMOVE + one ADD");
        assertEquals(SPlayerListItemPacket.Action.REMOVE_PLAYER, actionOf(globalSink.get(0)),
            "first broadcast must be REMOVE; sink=" + globalSink);
        assertEquals(SPlayerListItemPacket.Action.ADD_PLAYER, actionOf(globalSink.get(1)),
            "second broadcast must be ADD; sink=" + globalSink);
        int removeIdx = indexOfAction(targetStream, SPlayerListItemPacket.Action.REMOVE_PLAYER);
        int addIdx = indexOfAction(targetStream, SPlayerListItemPacket.Action.ADD_PLAYER);
        assertTrue(removeIdx >= 0 && addIdx == removeIdx + 1,
            "REMOVE+ADD must be neighbors in the captured stream; stream=" + targetStream);
    }

    @Test
    @DisplayName("BROADCAST_USE_BUNDLE is inert on 1.16.5 — the pair is still sent standalone")
    void bundleFlag_inert_pairStillStandalone() {
        Config.BROADCAST_USE_BUNDLE.set(true);
        broadcaster.broadcastProfileChange(target.getGameProfile(), target);

        // No bundle packet exists on 1.16.5; the REMOVE+ADD pair must be
        // delivered as two standalone SPlayerListItemPackets regardless.
        assertEquals(2, globalSink.size(), "exactly one REMOVE + one ADD, no bundle");
        assertEquals(SPlayerListItemPacket.Action.REMOVE_PLAYER, actionOf(globalSink.get(0)));
        assertEquals(SPlayerListItemPacket.Action.ADD_PLAYER, actionOf(globalSink.get(1)));
    }

    @Test
    @DisplayName("Explicit-observer variant sends REMOVE+ADD only to the given observers")
    void explicitObserver_variantReachesOnlyGivenSet() {
        ServerPlayerEntity[] onlyOverworld = new ServerPlayerEntity[]{target, observer1};
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
        ServerPlayerEntity[] allPlayers = new ServerPlayerEntity[]{target, observer1, netherObserver};
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

    /* ================================================================== */
    /*  Tracker untrack/retrack                                           */
    /* ================================================================== */

    @Test
    @DisplayName("trackerUntrackRetrack drives removeEntity then addEntity when flag is on")
    void trackerUntrackRetrack_runsWhenEnabled() {
        broadcaster.trackerUntrackRetrack(target);

        assertEquals(2, chunkTrace.size(), "removeEntity then addEntity expected");
        assertEquals("removeEntity", chunkTrace.get(0));
        assertEquals("addEntity", chunkTrace.get(1));
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

        SPlayerListItemPacket add = filterAction(targetStream, SPlayerListItemPacket.Action.ADD_PLAYER);
        assertNotNull(add, "ADD packet missing from stream=" + targetStream);
        GameProfile carried = add.getEntries().get(0).getProfile();
        assertNotNull(carried, "ADD packet must carry the player's profile");
        boolean hasTexture = carried.getProperties().get("textures").stream()
            .anyMatch(prop -> TEXTURE_VALUE.equals(prop.getValue()));
        assertTrue(hasTexture, "ADD packet must carry the textures property");
    }

    /* ================================================================== */
    /*  Helpers                                                            */
    /* ================================================================== */

    private ServerPlayerEntity newPlayer(String name, ServerWorld level) {
        UUID uuid = UUID.randomUUID();
        ServerPlayerEntity player = mock(ServerPlayerEntity.class);
        when(player.getUUID()).thenReturn(uuid);
        when(player.getGameProfile()).thenReturn(new GameProfile(uuid, name));
        when(player.getLevel()).thenReturn(level);
        when(player.getTabListDisplayName()).thenReturn(null);
        when(player.getServer()).thenReturn(server);
        // SPlayerListItemPacket's entry ctor reads gameMode.getGameModeForPlayer()
        // and the connection's NetworkManager; field-back both.
        ServerPlayNetHandler connection = mock(ServerPlayNetHandler.class);
        setField(player, "connection", connection);
        PlayerInteractionManager gameMode = mock(PlayerInteractionManager.class);
        when(gameMode.getGameModeForPlayer()).thenReturn(GameType.SURVIVAL);
        setField(player, "gameMode", gameMode);
        setField(player, "abilities", new PlayerAbilities());
        return player;
    }

    private List<Object> streamFor(ServerPlayerEntity player) {
        if (player == target) return targetStream;
        if (player == observer1) return observer1Stream;
        if (player == netherObserver) return netherStream;
        throw new IllegalArgumentException("unknown player: " + player);
    }

    private void attachStream(ServerPlayerEntity player, List<Object> stream) {
        doAnswer(inv -> {
            stream.add(inv.getArgument(0));
            return null;
        }).when(player.connection).send(any(IPacket.class));
    }

    private static ServerWorld levelMock(RegistryKey<World> dimension) {
        ServerWorld level = mock(ServerWorld.class);
        when(level.dimension()).thenReturn(dimension);
        return level;
    }

    private static SPlayerListItemPacket.Action actionOf(Object packet) {
        return ((SPlayerListItemPacket) packet).getAction();
    }

    private static int indexOfAction(List<?> events, SPlayerListItemPacket.Action action) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i) instanceof SPlayerListItemPacket
                    && ((SPlayerListItemPacket) events.get(i)).getAction() == action) {
                return i;
            }
        }
        return -1;
    }

    private static SPlayerListItemPacket filterAction(List<?> events, SPlayerListItemPacket.Action action) {
        for (Object event : events) {
            if (event instanceof SPlayerListItemPacket
                    && ((SPlayerListItemPacket) event).getAction() == action) {
                return (SPlayerListItemPacket) event;
            }
        }
        return null;
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
}
