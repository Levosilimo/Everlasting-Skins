/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless factory for Forge lifecycle events used by SkinRestorerTest.
 * All four event constructors are single-argument (verified via javap), so
 * they are trivially constructible from Mockito mocks of MinecraftServer /
 * ServerPlayer with no running server.
 */
final class TestForgeEvents {

    private TestForgeEvents() {
    }

    static ServerStartingEvent newServerStartingEvent(MinecraftServer server) {
        return new ServerStartingEvent(server);
    }

    static ServerStoppingEvent newServerStoppingEvent(MinecraftServer server) {
        return new ServerStoppingEvent(server);
    }

    static PlayerEvent.PlayerLoggedInEvent newPlayerLoggedInEvent(ServerPlayer player) {
        return new PlayerEvent.PlayerLoggedInEvent(player);
    }

    static PlayerEvent.PlayerLoggedOutEvent newPlayerLoggedOutEvent(ServerPlayer player) {
        return new PlayerEvent.PlayerLoggedOutEvent(player);
    }

    /** Mock server with an empty online player list. */
    static MinecraftServer mockServer(Path tempDir) {
        return mockServer(tempDir, Collections.emptyList());
    }

    /**
     * Mock server: getFile("EverlastingSkins") redirects the save dir to
     * tempDir/EverlastingSkins; getPlayerList().getPlayers() returns the given
     * list (defaults to empty).
     */
    static MinecraftServer mockServer(Path tempDir, List<ServerPlayer> onlinePlayers) {
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getFile("EverlastingSkins")).thenReturn(tempDir.resolve("EverlastingSkins"));
        PlayerList playerList = mock(PlayerList.class);
        when(playerList.getPlayers()).thenReturn(onlinePlayers);
        when(server.getPlayerList()).thenReturn(playerList);
        return server;
    }

    /**
     * Mock player: getUUID() and getGameProfile() are the only accessors the
     * SkinRestorer handlers read on the synchronous stored-skin branches.
     */
    static ServerPlayer mockPlayer(UUID uuid, String name) {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(uuid);
        when(player.getGameProfile()).thenReturn(new GameProfile(uuid, name));
        return player;
    }
}
