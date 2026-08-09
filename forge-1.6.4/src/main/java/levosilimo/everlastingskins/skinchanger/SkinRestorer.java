/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import levosilimo.everlastingskins.broadcast.SkinBroadcaster;
import levosilimo.everlastingskins.command.SkinRestorerCommand;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.server.MinecraftServer;
import net.minecraft.src.EntityPlayerMP;
import net.minecraft.src.INetworkManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Server-side skin lifecycle for the 1.6.4 lane: wires the shared
 * {@link SkinStorage} on server start, re-broadcasts the saved skin on
 * login, persists it on disconnect, and flushes on shutdown.
 *
 * <p>Skin storage is keyed by UUID only (memory #1123) — and 1.6.4 is a
 * pre-UUID-migration player model: no {@code getGameProfile()}, no
 * {@code getPersistentID()}, no player-resolvable account UUID. The binding
 * boundary derives a deterministic OFFLINE-MODE UUID from the username via
 * {@link #uuidOf(String)} (the vanilla {@code "OfflinePlayer:"} v3
 * convention), so :common's UUID-keyed {@link SkinStorage}/{@link SkinIO}
 * contract is satisfied unchanged and the player object never enters :common.
 *
 * <p>Restore mechanism: 1.6.4 has no GameProfile textures to inject — the
 * legacy skins.minecraft.net username-keyed path is dead (Mojang shutdown),
 * so the lane re-broadcasts the stored skin over the FML 7.x channel on
 * login ({@link SkinBroadcaster}), the notification surface for companion
 * clients. This is the lane's net-new adapter logic (no sibling lane has it).
 */
public final class SkinRestorer {

    private static final String OFFLINE_PREFIX = "OfflinePlayer:";

    private static volatile SkinStorage storage;
    private static volatile MinecraftServer server;

    private SkinRestorer() {}

    /**
     * Deterministic username-derived storage key (vanilla offline-mode v3
     * convention). 1.6.4 has no account UUID; this bridge keeps :common's
     * UUID-keyed contract intact at the binding boundary.
     */
    public static UUID uuidOf(String username) {
        return UUID.nameUUIDFromBytes(
            (OFFLINE_PREFIX + username).getBytes(StandardCharsets.UTF_8));
    }

    public static void onServerStarting(FMLServerStartingEvent event) {
        server = event.getServer();
        Path dataDir = server.getFile("EverlastingSkins").toPath();
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            System.err.println("EverlastingSkins: failed to create skin data directory: " + e);
        }
        SkinIO skinIO = new SkinIO(dataDir);
        // Startup integrity sweep: quarantine bit-rotten records before the
        // first player logs in (mirrors the 1.8.9 lane's init).
        skinIO.validateAllFiles();
        storage = new SkinStorage(skinIO);
        event.registerServerCommand(new SkinRestorerCommand());
    }

    public static void onServerStopping(FMLServerStoppingEvent event) {
        MinecraftServer srv = server;
        SkinStorage s = storage;
        if (srv == null || s == null) {
            return;
        }
        for (Object o : srv.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) o;
                UUID uuid = uuidOf(player.getCommandSenderName());
                if (s.getSkin(uuid) != null) {
                    s.saveSkin(uuid);
                }
            }
        }
        s.flushPending();
    }

    /**
     * Player-keyed login: re-broadcast the stored skin over the channel when
     * one exists (the 1.6.4 restore surface — no GameProfile to mutate).
     */
    public static void onPlayerLoggedIn(EntityPlayerMP player) {
        SkinStorage s = storage;
        if (s == null) {
            return;
        }
        UUID uuid = uuidOf(player.getCommandSenderName());
        CustomSkinProperty skin = s.getSkin(uuid);
        if (skin != null) {
            SkinBroadcaster.broadcastProfileChange(player);
        }
    }

    /**
     * Manager-keyed disconnect (FML 7.x {@code connectionClosed} carries no
     * player): resolve the player from the manager and persist their skin.
     */
    public static void onConnectionClosed(INetworkManager manager) {
        MinecraftServer srv = server;
        SkinStorage s = storage;
        if (srv == null || s == null || srv.getConfigurationManager() == null) {
            return;
        }
        for (Object o : srv.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) o;
                if (player.playerNetServerHandler != null
                    && manager == player.playerNetServerHandler.netManager) {
                    UUID uuid = uuidOf(player.getCommandSenderName());
                    if (s.getSkin(uuid) != null) {
                        s.saveSkin(uuid);
                    }
                    return;
                }
            }
        }
    }

    /** Command-layer accessor: stores the skin and applies it to storage. */
    public static void applySkin(UUID uuid, CustomSkinProperty skin) {
        SkinStorage s = storage;
        if (s == null) {
            return;
        }
        if (skin == null || skin.isEmpty()) {
            clearSkin(uuid);
            return;
        }
        s.setSkin(uuid, skin);
    }

    /** Command-layer accessor: removes the stored skin. */
    public static void clearSkin(UUID uuid) {
        SkinStorage s = storage;
        if (s != null) {
            s.removeSkin(uuid);
        }
    }

    /** Command-layer accessor: the stored skin's source label (null when unset). */
    public static String getSource(UUID uuid) {
        SkinStorage s = storage;
        return s == null ? null : s.getSource(uuid);
    }

    /** Test seam — mirrors the mc1.12.2 SkinRestorer.setServer pattern. */
    public static void setServerForTest(MinecraftServer server) {
        SkinRestorer.server = server;
    }

    /** Test seam — injects storage without a full FML lifecycle. */
    public static void setStorageForTest(SkinStorage storage) {
        SkinRestorer.storage = storage;
    }
}
