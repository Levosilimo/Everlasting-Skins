/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.broadcast.SkinProfileBroadcaster;
import levosilimo.everlastingskins.broadcast.VanillaProfileBroadcaster;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketEntityEffect;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.network.play.server.SPacketServerDifficulty;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.WorldInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Refresh-pipeline call-shape contract for the 1.10.2 {@link SkinRefreshTask}
 * (memory #1115: deterministic fakes only — a real EntityPlayerMP built
 * over mocked server/world/connection, storage via the SkinRestorer test
 * seam, packet fan-out via the {@link SkinProfileBroadcaster} seam; no live
 * server, no HTTP).
 *
 * <p>Contract pinned here: the task enqueues the disk flush BEFORE mutating
 * the GameProfile (atomicity), then mutates the profile, re-broadcasts the
 * tab-list entry once, and runs the same-dimension respawn cascade
 * (respawn < position < difficulty < permission-level < abilities).
 */
class SkinRefreshTaskTest {

    private static final UUID TARGET_UUID = UUID.fromString("11223344-5566-7788-99aa-bbccddeeff00");
    private static final Property OLD_PROPERTY = new Property("textures", "oldValue", "oldSignature");
    private static final CustomSkinProperty NEW_SKIN =
            new CustomSkinProperty("textures", "newValue", "newSignature", "MojangAPI");

    private MinecraftServer server;
    private PlayerList playerList;
    private WorldServer world;
    private EntityPlayerMP target;
    private NetHandlerPlayServer connection;
    private SkinStorage storage;
    private RecordingBroadcaster broadcaster;

    /** Recording fake for the per-viewer fan-out seam. */
    static final class RecordingBroadcaster implements SkinProfileBroadcaster {
        int broadcastCalls;

        @Override
        public void broadcastProfileChange(EntityPlayerMP target) {
            broadcastCalls++;
        }
    }

    @BeforeEach
    void setUp() {
        SkinMetrics.INSTANCE.reset();
        // ForgeHooks/Items throw "Accessed Blocks before Bootstrap!" unless
        // the vanilla registry bootstrap has run; the real server does this
        // at boot (mirror of the mc1.12.2 test harness).
        net.minecraft.init.Bootstrap.register();

        server = mock(MinecraftServer.class);
        playerList = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(playerList);

        world = mock(WorldServer.class);
        when(world.getDifficulty()).thenReturn(EnumDifficulty.PEACEFUL);
        WorldInfo worldInfo = mock(WorldInfo.class);
        when(world.getWorldInfo()).thenReturn(worldInfo);
        when(worldInfo.getTerrainType()).thenReturn(WorldType.DEFAULT);
        when(worldInfo.isDifficultyLocked()).thenReturn(false);
        when(world.getSpawnPoint()).thenReturn(new BlockPos(0, 64, 0));
        when(world.getCollisionBoxes(any(Entity.class), any(AxisAlignedBB.class)))
            .thenReturn(Collections.emptyList());
        // World.provider is a public final field on 1.10.2; Mockito cannot
        // stub it, so the real player constructor reads it via reflection.
        WorldProvider provider = mock(WorldProvider.class);
        when(provider.getDimension()).thenReturn(0);
        when(provider.getRandomizedSpawnPoint()).thenReturn(new BlockPos(0, 64, 0));
        setProviderField(world, provider);

        GameProfile profile = new GameProfile(TARGET_UUID, "Target");
        PlayerInteractionManager interactionManager = mock(PlayerInteractionManager.class);
        when(interactionManager.getGameType()).thenReturn(GameType.SURVIVAL);
        target = new EntityPlayerMP(server, world, profile, interactionManager);
        connection = mock(NetHandlerPlayServer.class);
        target.connection = connection;

        storage = mock(SkinStorage.class);
        when(storage.saveSkinAsync(any(UUID.class), any(CustomSkinProperty.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        SkinRestorer.setSkinStorageForTest(storage);
        SkinRestorer.setServerForTest(server);

        broadcaster = new RecordingBroadcaster();
        SkinRefreshTask.setBroadcaster(broadcaster);
    }

    @AfterEach
    void tearDown() {
        SkinRefreshTask.setBroadcaster(new VanillaProfileBroadcaster());
        SkinRestorer.setSkinStorageForTest(null);
        SkinRestorer.setServerForTest(null);
    }

    private void applyTexture(Property property) {
        target.getGameProfile().getProperties().put("textures", property);
    }

    /** World.provider is a public final field on 1.10.2; Mockito cannot stub it. */
    private static void setProviderField(WorldServer world, WorldProvider provider) {
        try {
            Field field = World.class.getField("provider");
            field.setAccessible(true);
            field.set(world, provider);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot stub World.provider", e);
        }
    }

    @Test
    void cascadeFlushesBeforeMutatingAndRebroadcasts() {
        applyTexture(OLD_PROPERTY);

        SkinRefreshTask.task(target, NEW_SKIN, 0L);

        verify(storage).saveSkinAsync(TARGET_UUID, NEW_SKIN);
        Collection<Property> applied = target.getGameProfile().getProperties().get("textures");
        assertEquals(1, applied.size());
        assertEquals("newValue", applied.iterator().next().getValue());
        assertEquals(1, broadcaster.broadcastCalls);
        verify(connection).sendPacket(any(SPacketRespawn.class));
        verify(connection).sendPacket(any(SPacketServerDifficulty.class));
        // No potions on a fresh player: the effect replay must be a no-op.
        verify(connection, never()).sendPacket(any(SPacketEntityEffect.class));
    }

    @Test
    void clearWithoutAppliedTexturesIsSilentNoOp() {
        SkinRefreshTask.task(target, null, 0L);

        assertEquals(0, broadcaster.broadcastCalls);
        verify(connection, never()).sendPacket(any(Packet.class));
        assertTrue(target.getGameProfile().getProperties().get("textures").isEmpty());
    }

    @Test
    void clearWithStaleTexturesDropsProfileAndRebroadcasts() {
        applyTexture(OLD_PROPERTY);

        SkinRefreshTask.task(target, null, 0L);

        assertTrue(target.getGameProfile().getProperties().get("textures").isEmpty());
        assertEquals(1, broadcaster.broadcastCalls);
        verify(connection).sendPacket(any(SPacketRespawn.class));
    }

    @Test
    void emptyPropertyIsTreatedAsClear() {
        applyTexture(OLD_PROPERTY);
        CustomSkinProperty empty = new CustomSkinProperty("", "", null);

        SkinRefreshTask.task(target, empty, 0L);

        assertTrue(target.getGameProfile().getProperties().get("textures").isEmpty());
        assertEquals(1, broadcaster.broadcastCalls);
    }
}
