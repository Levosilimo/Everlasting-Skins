/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import levosilimo.everlastingskins.forge1710.EverlastingSkins;
import levosilimo.everlastingskins.forge1710.config.Config;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Server-side skin lifecycle for the 1.7.10 lane: wires the shared
 * {@link SkinStorageProvider} on server start, applies the saved skin on
 * login, persists it on logout, and flushes on shutdown.
 *
 * <p>Skin storage is keyed by UUID only (memory #1123): the UUID comes from
 * {@code player.getGameProfile().getId()} — 1.7.10 has GameProfile and no
 * {@code getPersistentID()} (that is 1.8+). The player object never enters
 * :common.
 */
public final class SkinRestorer {

    private static volatile SkinStorageProvider provider;
    private static volatile MinecraftServer server;

    private SkinRestorer() {}

    public static void onServerStarting(FMLServerStartingEvent event) {
        server = event.getServer();
        Path dataDir = server.getFile("EverlastingSkins").toPath();
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            EverlastingSkins.logger.error("Failed to create skin data directory", e);
        }
        provider = new SkinStorageProvider(new SkinStorage(new SkinIO(dataDir)));
        Config.load(new File(server.getFile("config"), "everlastingskins.cfg"));
        event.registerServerCommand(new SkinCommand(provider));
    }

    public static void onServerStopping(FMLServerStoppingEvent event) {
        MinecraftServer srv = server;
        SkinStorageProvider p = provider;
        if (srv == null || p == null) {
            return;
        }
        for (Object o : srv.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) o;
                UUID uuid = player.getGameProfile().getId();
                if (p.getSkin(uuid) != null) {
                    p.raw().saveSkin(uuid);
                }
            }
        }
        p.raw().flushPending();
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        SkinStorageProvider p = provider;
        if (p == null || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        UUID uuid = player.getGameProfile().getId();
        CustomSkinProperty skin = p.getSkin(uuid);
        if (skin != null) {
            player.getGameProfile().getProperties().removeAll("textures");
            player.getGameProfile().getProperties().put("textures", skin.getOriginalProperty());
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SkinStorageProvider p = provider;
        if (p == null || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        UUID uuid = player.getGameProfile().getId();
        if (p.getSkin(uuid) != null) {
            p.raw().saveSkin(uuid);
        }
    }

    /** Test seam — mirrors the mc1.12.2 SkinRestorer.setServer pattern. */
    public static void setServerForTest(MinecraftServer server) {
        SkinRestorer.server = server;
    }

    /** Test seam — injects a provider without a full FML lifecycle. */
    public static void setProviderForTest(SkinStorageProvider provider) {
        SkinRestorer.provider = provider;
    }
}
