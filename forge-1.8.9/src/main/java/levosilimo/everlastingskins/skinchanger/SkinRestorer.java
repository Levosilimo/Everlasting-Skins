/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.GameProfile;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.UUIDUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import net.minecraft.network.play.server.S1DPacketEntityEffect;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 1.8.9 binding for the {@code :common} {@link SkinStorage}.
 *
 * <p>Storage constraint (memory #1123): SkinStorage is keyed by UUID only —
 * no EntityPlayer/GameProfile object ever crosses the lane boundary into
 * :common. This class extracts the player UUID at the lane edge:
 * {@link EntityPlayer#getPersistentID()} (inherited from Entity, dashed
 * form) and {@link EntityPlayer#getGameProfile()} (authlib surface) are
 * the 1.8.9 legacy APIs; 32-char no-dash UUID strings are normalized via
 * {@link UUIDUtils#convertToDashed(String)}.
 *
 * <p>Skin lifecycle (parity investigation): the instance is registered on
 * the FML event bus at server start — {@link #onPlayerLoggedIn} applies the
 * saved skin on login, {@link #onPlayerLoggedOut} persists it on disconnect,
 * and {@link #applySkin}/{@link #clearSkin} drive the mid-session apply with
 * the standard 1.8-1.12 skin-changer re-send (tab-list REMOVE+ADD to all
 * players, in-world respawn to tracking observers, in-place target respawn).
 */
public class SkinRestorer {

    private static volatile SkinStorage skinStorage;
    private static volatile MinecraftServer server;

    public SkinRestorer() {}

    @Nullable
    public static SkinStorage getSkinStorage() {
        return skinStorage;
    }

    public static MinecraftServer getServer() {
        return server;
    }

    /** Test-only: replaces the static server reference without a full FML lifecycle. */
    public static void setServerForTest(MinecraftServer testServer) {
        server = testServer;
    }

    /** Test-only: injects a storage without a full FML lifecycle. */
    public static void setStorageForTest(SkinStorage testStorage) {
        skinStorage = testStorage;
    }

    /** Server-start bootstrap: creates the data dir and the storage. */
    public static void init(FMLServerStartingEvent event) {
        server = event.getServer();
        Path dataDir = server.getFile("EverlastingSkins").toPath();
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            EverlastingSkins.LOGGER.error("Failed to create skin data directory", e);
        }
        SkinIO skinIO = new SkinIO(dataDir);
        // Startup integrity sweep: quarantine bit-rotten records before the
        // first player logs in, so SkinStorage only ever sees verified files.
        skinIO.validateAllFiles();
        skinStorage = new SkinStorage(skinIO);
        EverlastingSkins.LOGGER.info("Skin storage ready at {}", dataDir);
    }

    /** Server-stop flush: persist all queued writes before shutdown. */
    public static void onServerStopping() {
        SkinStorage storage = skinStorage;
        if (storage != null) {
            storage.flushPending();
        }
    }

    /** Player UUID from the 1.8.9 Entity surface (dashed form). */
    public static UUID uuidOf(EntityPlayer player) {
        return player.getPersistentID();
    }

    /** Player UUID from the legacy GameProfile surface (authlib). */
    public static UUID profileIdOf(EntityPlayer player) {
        return player.getGameProfile().getId();
    }

    /**
     * Normalizes a raw UUID string for storage-keying: dashed or 32-char
     * no-dash input both map to the canonical dashed UUID.
     *
     * @throws IllegalArgumentException if the input is not a UUID
     */
    public static UUID normalizeUuid(String rawUuid) {
        return UUIDUtils.tryParseUniqueId(rawUuid)
            .orElseThrow(() -> new IllegalArgumentException("Invalid UUID: " + rawUuid));
    }

    /**
     * Applies the saved skin on login. The profile mutation happens before
     * the player is visible to observers, so there is no vanilla-skin flash.
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        SkinStorage storage = skinStorage;
        if (storage == null || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        SkinMetrics.INSTANCE.recordPlayerJoined();
        CustomSkinProperty skin = storage.getSkin(profileIdOf(player));
        if (skin != null && !skin.isEmpty()) {
            mutateProfile(player, skin);
        }
        // No skin on disk: leave the profile unmutated — the client renders
        // the default Steve/Alex from the UUID hash.
    }

    /** Saves the player's skin on disconnect so it persists across sessions. */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SkinStorage storage = skinStorage;
        if (storage == null || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        SkinMetrics.INSTANCE.recordPlayerLeft();
        UUID uuid = profileIdOf(player);
        if (storage.getSkin(uuid) != null) {
            storage.saveSkin(uuid);
        }
    }

    /**
     * Mid-session apply: stores the skin, mutates the GameProfile's
     * {@code textures} property and re-sends the profile to every observer.
     * Must run on the server thread (callers marshal via addScheduledTask).
     */
    public static void applySkin(EntityPlayerMP player, CustomSkinProperty skin) {
        SkinStorage storage = skinStorage;
        if (storage == null || player == null) {
            return;
        }
        if (skin == null || skin.isEmpty()) {
            clearSkin(player);
            return;
        }
        UUID uuid = profileIdOf(player);
        // Persist BEFORE mutating the profile (mc1.12.2 invariant): a failed
        // enqueue leaves the applied profile untouched, so in-memory and
        // on-disk state stay consistent.
        storage.setSkin(uuid, skin);
        storage.saveSkinAsync(uuid, skin);
        mutateProfile(player, skin);
        cascade(player);
    }

    /**
     * Removes the stored skin and strips the applied {@code textures}
     * property, re-sending the profile only when stale textures existed.
     */
    public static void clearSkin(EntityPlayerMP player) {
        SkinStorage storage = skinStorage;
        if (storage == null || player == null) {
            return;
        }
        storage.removeSkin(profileIdOf(player));
        boolean hadAppliedTextures = !player.getGameProfile().getProperties().get("textures").isEmpty();
        player.getGameProfile().getProperties().removeAll("textures");
        if (hadAppliedTextures) {
            cascade(player);
        }
    }

    /** GameProfile mutation is what every client ultimately reads textures from. */
    private static void mutateProfile(EntityPlayerMP player, CustomSkinProperty skin) {
        GameProfile profile = player.getGameProfile();
        profile.getProperties().removeAll("textures");
        profile.getProperties().put("textures", skin.getOriginalProperty());
    }

    /**
     * The standard 1.8-1.12 skin-changer re-send: tab-list REMOVE+ADD to
     * ALL online players (the ADD packet serializes the GameProfile at
     * construction time, so this must run after the profile mutation), an
     * in-world respawn packet to every tracking observer, and an in-place
     * respawn of the target for its own view.
     */
    private static void cascade(EntityPlayerMP player) {
        MinecraftServer srv = server != null ? server : player.mcServer;
        if (srv == null || srv.getConfigurationManager() == null) {
            return;
        }
        S38PacketPlayerListItem removePacket =
            new S38PacketPlayerListItem(S38PacketPlayerListItem.Action.REMOVE_PLAYER, player);
        S38PacketPlayerListItem addPacket =
            new S38PacketPlayerListItem(S38PacketPlayerListItem.Action.ADD_PLAYER, player);
        srv.getConfigurationManager().sendPacketToAllPlayers(removePacket);
        srv.getConfigurationManager().sendPacketToAllPlayers(addPacket);
        if (player.worldObj instanceof WorldServer) {
            ((WorldServer) player.worldObj).getEntityTracker()
                .sendToAllTrackingEntity(player, new S0CPacketSpawnPlayer(player));
        }
        respawnSelf(player);
    }

    /** In-place respawn so the target's own view re-renders with the new skin. */
    private static void respawnSelf(EntityPlayerMP player) {
        try {
            World world = player.worldObj;
            player.playerNetServerHandler.sendPacket(new S07PacketRespawn(
                player.dimension, world.getDifficulty(), world.getWorldInfo().getTerrainType(),
                player.theItemInWorldManager.getGameType()));
            player.playerNetServerHandler.setPlayerLocation(
                player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch);
            player.sendPlayerAbilities();
            // Potion effects must be replayed after respawn (vanilla
            // transferPlayerToDimension parity, mirror of the 1.12.2 lane).
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.playerNetServerHandler.sendPacket(new S1DPacketEntityEffect(player.getEntityId(), effect));
            }
        } catch (Throwable t) {
            // Fail soft: a partial cascade must not abort the apply.
            EverlastingSkins.LOGGER.warn("Skin respawn cascade failed for {}", player.getName(), t);
        }
    }
}
