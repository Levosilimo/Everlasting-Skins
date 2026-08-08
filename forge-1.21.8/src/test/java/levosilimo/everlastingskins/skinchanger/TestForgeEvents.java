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
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless factory for Forge lifecycle events used by SkinRestorerTest.
 * All four event constructors are single-argument, so they are trivially
 * constructible from Mockito mocks of MinecraftServer / ServerPlayer with no
 * running server and no FML runtime.
 *
 * <p>Package-private by design: it is a test-support helper for the
 * skinchanger test package only.
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

    /**
     * Mock server: getFile("EverlastingSkins") redirects the save dir to
     * tempDir/EverlastingSkins; getPlayerList().getPlayers() returns the given
     * list (defaults to empty). MinecraftServer.getFile(String) is a mapped
     * name that compiles against the same (re)obfuscated MC classes that
     * SkinRefreshHandlerTest already stubs.
     */
    static MinecraftServer mockServer(Path tempDir, List<ServerPlayer> players) {
        Path skinDir = tempDir.resolve("EverlastingSkins");
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getFile("EverlastingSkins")).thenReturn(skinDir);
        PlayerList playerlist = mock(PlayerList.class);
        when(playerlist.getPlayers())
                .thenReturn(players != null ? players : Collections.emptyList());
        when(server.getPlayerList()).thenReturn(playerlist);
        return server;
    }

    /**
     * Mock player with a mutable GameProfile (new instance each call) so
     * SkinRestorer.onPlayerLoggedIn can mutate its textures property map.
     */
    static ServerPlayer mockPlayer(UUID uuid, String name) {
        // Headless JVM: force Forge's vanilla registries to exist before the
        // ServerPlayer superclass chain is initialized. Block.<clinit> calls
        // GameData$BlockCallbacks.getBlockStateIDMap(), which NPEs on a null
        // BLOCKS registry unless ForgeRegistries.<clinit> (-> GameData.init())
        // has run. The real server path runs it via FML; the flag hack in
        // SkinRestorerTest skips Bootstrap.bootStrap(), so the registries must
        // be created here instead.
        ForgeRegistries.BLOCKS.getClass();
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(uuid);
        when(player.getGameProfile()).thenReturn(new GameProfile(uuid, name));
        return player;
    }
}
