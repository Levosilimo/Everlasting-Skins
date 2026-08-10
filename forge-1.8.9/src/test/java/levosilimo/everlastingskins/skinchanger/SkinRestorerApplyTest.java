/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.internal.util.reflection.FieldSetter;

import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-JUnit tests for the 1.8.9 apply path: login-apply, logout-persist
 * and the mid-session apply with the standard 1.8-1.12 skin-changer re-send
 * (tab-list REMOVE+ADD to all players, in-world respawn to tracking
 * observers, in-place target respawn). Memory #1115: deterministic fakes
 * only — no live server, no HTTP, no threads.
 */
class SkinRestorerApplyTest {

    private static final UUID PLAYER = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

    @TempDir
    Path tempDir;

    private SkinStorage storage;
    private SkinRestorer restorer;
    private EntityPlayerMP player;
    private GameProfile profile;
    private MinecraftServer server;
    private ServerConfigurationManager config;
    private World world;
    private NetHandlerPlayServer netHandler;

    @BeforeEach
    void setUp() {
        storage = new SkinStorage(new SkinIO(tempDir));
        SkinRestorer.setStorageForTest(storage);
        restorer = new SkinRestorer();
        profile = new GameProfile(PLAYER, "TestPlayer");
        player = mock(EntityPlayerMP.class);
        when(player.getPersistentID()).thenReturn(PLAYER);
        when(player.getGameProfile()).thenReturn(profile);
        when(player.getEntityId()).thenReturn(42);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.getActivePotionEffects()).thenReturn(Collections.emptyList());
        // Entity public fields on the mock (the packet ctors read them).
        InventoryPlayer inventory = mock(InventoryPlayer.class);
        when(inventory.getCurrentItem()).thenReturn(null);
        player.inventory = inventory;
        // NOTE: the world is mocked as plain World, not WorldServer — the
        // WorldServer static init (Items/Blocks registries) requires the
        // LaunchWrapper Bootstrap chain, which cannot run in a bare JVM.
        // The tracker fan-out branch of the cascade (instanceof WorldServer)
        // is therefore not unit-covered; it mirrors the 1.12.2 lane.
        world = mock(World.class);
        when(world.getDifficulty()).thenReturn(EnumDifficulty.PEACEFUL);
        WorldInfo worldInfo = mock(WorldInfo.class);
        when(world.getWorldInfo()).thenReturn(worldInfo);
        when(worldInfo.getTerrainType()).thenReturn(WorldType.DEFAULT);
        player.worldObj = world;
        netHandler = mock(NetHandlerPlayServer.class);
        player.playerNetServerHandler = netHandler;
        // theItemInWorldManager is final in 1.8.9 — set via reflection.
        WorldSettings.GameType gameType = WorldSettings.GameType.SURVIVAL;
        net.minecraft.server.management.ItemInWorldManager itemManager =
            mock(net.minecraft.server.management.ItemInWorldManager.class);
        when(itemManager.getGameType()).thenReturn(gameType);
        try {
            FieldSetter.setField(player, EntityPlayerMP.class.getDeclaredField("theItemInWorldManager"), itemManager);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("1.8.9 EntityPlayerMP.theItemInWorldManager missing", e);
        }
        server = mock(MinecraftServer.class);
        config = mock(ServerConfigurationManager.class);
        when(server.getConfigurationManager()).thenReturn(config);
        SkinRestorer.setServerForTest(server);
    }

    @AfterEach
    void tearDown() {
        SkinStorage.resetForTest();
    }

    private static CustomSkinProperty skin(String name, String source) {
        return new CustomSkinProperty(name, "value", "signature", source);
    }

    private static void assertTexturesContain(GameProfile p, CustomSkinProperty s) {
        assertTrue(p.getProperties().get("textures").contains(s.getOriginalProperty()));
    }

    @Test
    void loginAppliesStoredSkinToProfile() {
        CustomSkinProperty stored = skin("Notch", "MojangAPI");
        storage.setSkin(PLAYER, stored);
        restorer.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player));
        assertTexturesContain(profile, stored);
    }

    @Test
    void loginLeavesProfileUntouchedWithoutStoredSkin() {
        restorer.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player));
        assertTrue(profile.getProperties().get("textures").isEmpty());
    }

    @Test
    void loginIgnoresNonServerPlayerEvents() {
        EntityPlayer nonServerPlayer = mock(EntityPlayer.class);
        restorer.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(nonServerPlayer));
        restorer.onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(nonServerPlayer));
        // No exception, no interaction with the player surface.
    }

    @Test
    void logoutPersistsStoredSkin() {
        CustomSkinProperty stored = skin("Notch", "MojangAPI");
        storage.setSkin(PLAYER, stored);
        SkinStorage spyStorage = spy(storage);
        SkinRestorer.setStorageForTest(spyStorage);
        restorer.onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(player));
        verify(spyStorage).saveSkin(PLAYER);
    }

    @Test
    void logoutSkipsSaveWithoutStoredSkin() {
        SkinStorage spyStorage = spy(storage);
        SkinRestorer.setStorageForTest(spyStorage);
        restorer.onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(player));
        verify(spyStorage, never()).saveSkin(PLAYER);
    }

    @Test
    void applySkinMutatesProfilePersistsAndResendsToObservers() {
        CustomSkinProperty applied = skin("Notch", "MojangAPI");
        SkinRestorer.applySkin(player, applied);
        // Stored + applied to the GameProfile textures property.
        assertEquals(applied, storage.getSkin(PLAYER));
        assertTexturesContain(profile, applied);
        // Tab-list REMOVE+ADD to ALL players.
        verify(config, times(2)).sendPacketToAllPlayers(any(S38PacketPlayerListItem.class));
        // In-place respawn for the target's own view.
        verify(netHandler).sendPacket(any(S07PacketRespawn.class));
    }

    @Test
    void clearSkinStripsTexturesAndResends() {
        CustomSkinProperty applied = skin("Notch", "MojangAPI");
        storage.setSkin(PLAYER, applied);
        profile.getProperties().put("textures", new Property("textures", "value", "signature"));
        SkinRestorer.clearSkin(player);
        assertNull(storage.getSkin(PLAYER));
        assertTrue(profile.getProperties().get("textures").isEmpty());
        verify(config, times(2)).sendPacketToAllPlayers(any(S38PacketPlayerListItem.class));
        verify(netHandler).sendPacket(any(S07PacketRespawn.class));
    }

    @Test
    void applySkinSurvivesMissingServerContext() {
        SkinRestorer.setServerForTest(null);
        CustomSkinProperty applied = skin("Notch", "MojangAPI");
        SkinRestorer.applySkin(player, applied);
        // Storage + profile still applied; the re-send silently degrades.
        assertEquals(applied, storage.getSkin(PLAYER));
        assertTexturesContain(profile, applied);
        verify(config, never()).sendPacketToAllPlayers(any());
    }
}
