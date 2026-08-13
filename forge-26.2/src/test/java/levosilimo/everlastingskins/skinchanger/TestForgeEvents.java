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
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.lang.reflect.Field;
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
     *
     * <p>26.x (authlib 9) skin application swaps the REAL {@code Player.gameProfile}
     * field instead of mutating the profile in place, so getGameProfile() must
     * answer from that field — otherwise the applied skin would be invisible to
     * the test's assertions. If Mockito cannot stub the accessor the real
     * implementation reads the field anyway, so the field-backing is correct
     * in both cases.
     */
    static ServerPlayer mockPlayer(UUID uuid, String name) {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(uuid);
        setGameProfileField(player, new GameProfile(uuid, name));
        when(player.getGameProfile()).thenAnswer(inv -> getGameProfileField(player));
        return player;
    }

    private static void setGameProfileField(ServerPlayer player, GameProfile profile) {
        try {
            Field field = Player.class.getDeclaredField("gameProfile");
            field.setAccessible(true);
            field.set(player, profile);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set Player.gameProfile on mock", e);
        }
    }

    private static GameProfile getGameProfileField(ServerPlayer player) {
        try {
            Field field = Player.class.getDeclaredField("gameProfile");
            field.setAccessible(true);
            return (GameProfile) field.get(player);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read Player.gameProfile on mock", e);
        }
    }
}
