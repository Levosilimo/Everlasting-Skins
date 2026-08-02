/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.harness;

import com.mojang.authlib.GameProfile;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.skinchanger.SkinStorage;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.server.management.UserListOps;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.relauncher.FMLInjectionData;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Minimal server facade for integration tests. 1.12.2 has no in-process
 * MinecraftServer option (full-boot only), so the server and world are mocked
 * while the real ServerCommandManager, EntityPlayerMP and storage stack run.
 */
public class TestServerContext implements AutoCloseable {

    // ForgeHooks/Items throw "Accessed Items before Bootstrap!" unless the
    // vanilla registry bootstrap has run; the real server does this at boot.
    static {
        net.minecraft.init.Bootstrap.register();
    }

    private final Path tempDir;
    private final List<EntityPlayerMP> onlinePlayers = new ArrayList<>();

    public MinecraftServer server;
    public ServerCommandManager commandManager;
    public PlayerList playerList;
    public WorldServer world;
    public SkinRestorer skinRestorer;
    public SkinStorage storage;

    public TestServerContext(Path tempDir) {
        this.tempDir = tempDir;

        server = mock(MinecraftServer.class);
        commandManager = new ServerCommandManager(server);
        playerList = mock(PlayerList.class);
        when(server.getCommandManager()).thenReturn(commandManager);
        when(server.getPlayerList()).thenReturn(playerList);
        when(playerList.getPlayerByUsername(anyString())).thenAnswer(inv -> {
            for (EntityPlayerMP online : onlinePlayers) {
                if (online.getName().equals(inv.getArgument(0))) {
                    return online;
                }
            }
            return null;
        });
        when(server.getFile(anyString()))
            .thenAnswer(inv -> tempDir.resolve((String) inv.getArgument(0)).toFile());
        // Run scheduled tasks inline so async skin application stays deterministic.
        when(server.addScheduledTask(any(Runnable.class))).thenAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return true;
        });

        world = newWorld(0);

        skinRestorer = new SkinRestorer();
        setMinecraftHome(tempDir);
        SkinRestorer.onServerStarting(new FMLServerStartingEvent(server));
        storage = SkinRestorer.getSkinStorage();
    }

    public EntityPlayerMP newPlayer(String name) {
        EntityPlayerMP player = TestPlayerFactory.create(server, world, name);
        onlinePlayers.add(player);
        return player;
    }

    public EntityPlayerMP newPlayer(String name, WorldServer playerWorld) {
        return TestPlayerFactory.create(server, playerWorld, name);
    }

    /**
     * A second world mock for cross-dimension scenarios. Mirrors the stubs of
     * {@link #world} so EntityPlayerMP construction succeeds.
     */
    public WorldServer newWorld(int dimensionId) {
        WorldServer w = mock(WorldServer.class);
        WorldProvider provider = mock(WorldProvider.class);
        BlockPos spawn = new BlockPos(0, 64, 0);
        when(provider.getDimension()).thenReturn(dimensionId);
        when(provider.getRandomizedSpawnPoint()).thenReturn(spawn);
        setProviderField(w, provider);
        when(w.getSpawnPoint()).thenReturn(spawn);
        WorldInfo info = mock(WorldInfo.class);
        when(info.getTerrainType()).thenReturn(WorldType.DEFAULT);
        when(info.isDifficultyLocked()).thenReturn(false);
        when(w.getWorldInfo()).thenReturn(info);
        when(w.getDifficulty()).thenReturn(EnumDifficulty.NORMAL);
        when(w.getCollisionBoxes(any(Entity.class), any(AxisAlignedBB.class)))
            .thenReturn(Collections.emptyList());
        // SkinRefreshTask untracks/re-tracks the target via the EntityTracker
        // when Config.refreshViaEntityTracker is enabled.
        when(w.getEntityTracker()).thenReturn(mock(EntityTracker.class));
        return w;
    }

    /**
     * Grants the player op level 2 so canUseCommand(2, ...) passes and the
     * vanilla permission gate inside SkinCommand admits the command.
     */
    public void makeOp(EntityPlayerMP player) {
        when(playerList.canSendCommands(any(GameProfile.class))).thenReturn(true);
        when(playerList.getOppedPlayers()).thenReturn(mock(UserListOps.class));
        when(server.getOpPermissionLevel()).thenReturn(2);
    }

    public Path getTempDir() {
        return tempDir;
    }

    /**
     * Restores static mod state between tests. SkinCommand's API fields are
     * reset via SkinCommandTestAccess.resetAPIs() in each test's teardown.
     */
    public void reset() {
        SkinRestorer.setServer(null);
        SkinStorage.resetForTest();
    }

    @Override
    public void close() {
        reset();
    }

    // World.provider is a public final field on 1.12.2; Mockito cannot stub it.
    private static void setProviderField(WorldServer world, WorldProvider provider) {
        try {
            Field field = World.class.getField("provider");
            field.setAccessible(true);
            field.set(world, provider);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot stub World.provider", e);
        }
    }

    // Forge's Configuration reads FMLInjectionData.data()[6] at construction;
    // outside a real FML boot that slot is null.
    private static void setMinecraftHome(Path home) {
        try {
            Field field = FMLInjectionData.class.getDeclaredField("minecraftHome");
            field.setAccessible(true);
            field.set(null, home.toFile());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot stub FMLInjectionData.minecraftHome", e);
        }
    }
}
