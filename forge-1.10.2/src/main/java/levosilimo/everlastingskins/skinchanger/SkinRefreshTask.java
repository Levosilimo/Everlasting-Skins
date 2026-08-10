/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.broadcast.SkinProfileBroadcaster;
import levosilimo.everlastingskins.broadcast.VanillaProfileBroadcaster;
import levosilimo.everlastingskins.forge102.EverlastingSkins;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketEntityEffect;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.network.play.server.SPacketServerDifficulty;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.world.WorldServer;

/**
 * Server-tick half of a skin change (1.10.2 era-adapted mirror of the
 * mc1.12.2 class of the same name): enqueues the async disk flush, mutates
 * the GameProfile, and forces every viewer to re-read it. Runs on the main
 * thread via {@code addScheduledTask} so packet sends are thread-safe.
 *
 * <p>The flush is enqueued before the mutation so a failed enqueue leaves
 * the applied profile untouched (applied and on-disk state stay
 * consistent). The 1.10.2 entity surface uses the pre-1.11 field names
 * ({@code worldObj}, {@code mcServer}) — the refresh packets
 * (SPacketRespawn / SPacketServerDifficulty / SPacketEntityEffect) are
 * already the modern MCP names on this version.
 */
final class SkinRefreshTask {

    /**
     * Seam for the per-viewer packet fan-out. Production uses
     * {@link VanillaProfileBroadcaster}; tests inject a recording fake to
     * assert call shape without re-implementing the REMOVE+ADD+cascade
     * pipeline.
     */
    private static volatile SkinProfileBroadcaster broadcaster = new VanillaProfileBroadcaster();

    /** Test seam: replace the broadcaster. */
    static void setBroadcaster(SkinProfileBroadcaster newBroadcaster) {
        broadcaster = newBroadcaster;
    }

    private SkinRefreshTask() {
    }

    static void task(EntityPlayerMP target, CustomSkinProperty property, long fetchNanos) {
        if (target == null) return;

        long startNanos = System.nanoTime();
        SkinMetrics.INSTANCE.recordRefreshStarted(target.getUniqueID());

        if (property == null || property.isEmpty()) {
            // /skin clear with no Mojang profile: storage is already null, so
            // drop the applied textures property; stale applied textures are
            // re-broadcast + cascaded, a textureless profile is a silent no-op.
            try {
                clearCascade(target);
            } catch (Throwable t) {
                EverlastingSkins.LOGGER.error("Skin clear failed for {}", target.getName(), t);
                SkinMetrics.INSTANCE.recordRefreshFailed(target.getUniqueID());
            }
            return;
        }

        try {
            cascade(target, property, startNanos, fetchNanos);
            long durationNanos = System.nanoTime() - startNanos;
            SkinMetrics.INSTANCE.recordTaskDuration(durationNanos);
            if (durationNanos > 50_000_000L) {
                EverlastingSkins.LOGGER.warn("SkinRefresh spike: {}ms for player {}",
                    durationNanos / 1_000_000, target.getName());
            }
        } catch (Throwable t) {
            // Fail soft: a partial cascade (profile mutated, observers stale) must
            // not abort the server tick or crash scheduled-task execution.
            EverlastingSkins.LOGGER.error("Skin refresh failed for {}", target.getName(), t);
            SkinMetrics.INSTANCE.recordRefreshFailed(target.getUniqueID());
        }
    }

    private static void cascade(EntityPlayerMP target, CustomSkinProperty property, long startNanos,
            long fetchNanos) {
        // Atomicity invariant: the disk flush is enqueued BEFORE the
        // GameProfile is mutated. saveSkinAsync serializes the property
        // synchronously, so when the enqueue throws, the applied profile is
        // left untouched and in-memory and on-disk state still agree.
        long saveStartNanos = System.nanoTime();
        SkinRestorer.getSkinStorage().saveSkinAsync(target.getUniqueID(), property);
        long saveNanos = System.nanoTime() - saveStartNanos;

        // GameProfile mutation is what every client ultimately reads textures from.
        mutateProfile(target, property);

        // Tab-list re-add to ALL online players (global, no dimension scoping).
        long broadcastNanos = broadcastProfileChange(target);

        // Respawn in the same dimension so the target's own view refreshes
        // without an inventory wipe.
        respawnSelf(target);

        SkinMetrics.INSTANCE.recordRefreshCompleted(
            target.getUniqueID(), startNanos, fetchNanos, saveNanos, broadcastNanos);
    }

    private static void mutateProfile(EntityPlayerMP target, CustomSkinProperty property) {
        target.getGameProfile().getProperties().removeAll("textures");
        target.getGameProfile().getProperties().put("textures", property.getOriginalProperty());
    }

    /**
     * Clear half of the cascade: drop the applied textures property and, only
     * when stale textures actually existed, re-broadcast REMOVE+ADD so
     * observers re-learn the profile and run the respawn cascade so the
     * target's own view reverts to the default skin. A textureless profile has
     * nothing to revert or re-learn: the clear is a silent no-op.
     */
    private static void clearCascade(EntityPlayerMP target) {
        boolean hadAppliedTextures = !target.getGameProfile().getProperties().get("textures").isEmpty();
        target.getGameProfile().getProperties().removeAll("textures");
        if (!hadAppliedTextures) {
            return;
        }
        broadcastProfileChange(target);
        respawnSelf(target);
    }

    /** Tab-list REMOVE+ADD to ALL online players; returns the measured broadcast nanos. */
    private static long broadcastProfileChange(EntityPlayerMP target) {
        long broadcastStartNanos = System.nanoTime();
        broadcaster.broadcastProfileChange(target);
        return System.nanoTime() - broadcastStartNanos;
    }

    /** Respawn cascade for the target's own view (same-dimension, no inventory wipe). */
    private static void respawnSelf(EntityPlayerMP target) {
        MinecraftServer server = target.mcServer;
        PlayerList playerList = server.getPlayerList();
        WorldServer world = (WorldServer) target.world;

        EntityPlayerMP self = target;
        self.connection.sendPacket(new SPacketRespawn(
            self.dimension, self.world.getDifficulty(),
            self.world.getWorldInfo().getTerrainType(),
            self.interactionManager.getGameType()));
        self.connection.setPlayerLocation(
            self.posX, self.posY, self.posZ, self.rotationYaw, self.rotationPitch);
        self.connection.sendPacket(new SPacketServerDifficulty(
            self.world.getDifficulty(),
            self.world.getWorldInfo().isDifficultyLocked()));
        playerList.updatePermissionLevel(self);
        self.sendPlayerAbilities();

        // Potion effects must be replayed after respawn (vanilla transferPlayerToDimension parity).
        for (PotionEffect effect : self.getActivePotionEffects()) {
            self.connection.sendPacket(new SPacketEntityEffect(self.getEntityId(), effect));
        }

        // Time/weather resend, matching join behavior.
        playerList.updateTimeAndWeatherForPlayer(self, world);
    }
}
