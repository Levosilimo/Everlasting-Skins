/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.metrics.NetworkMetricsHandler;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketEntityEffect;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.network.play.server.SPacketServerDifficulty;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.world.WorldServer;

import java.io.IOException;

/**
 * Server-tick half of a skin change: mutates the GameProfile, forces every
 * viewer to re-read it, and enqueues the async disk flush. Runs on the main
 * thread via {@code addScheduledTask} so packet sends are thread-safe.
 */
final class SkinRefreshTask {

    private SkinRefreshTask() {
    }

    static void task(EntityPlayerMP target, CustomSkinProperty property, long fetchNanos) {
        if (target == null) return;

        long startNanos = System.nanoTime();
        SkinMetrics.INSTANCE.recordRefreshStarted(target.getUniqueID());

        if (property == null || property.isEmpty()) {
            SkinMetrics.INSTANCE.recordRefreshFailed(target.getUniqueID());
            return;
        }

        try {
            cascade(target, property, startNanos, fetchNanos);
            long durationNanos = System.nanoTime() - startNanos;
            SkinMetrics.INSTANCE.recordTaskDuration(durationNanos);
            if (durationNanos > 50_000_000L) {
                EverlastingSkins.logger.warn("SkinRefresh spike: {}ms for player {}",
                    durationNanos / 1_000_000, target.getName());
            }
        } catch (Throwable t) {
            // Fail soft: a partial cascade (profile mutated, observers stale) must
            // not abort the server tick or crash scheduled-task execution.
            EverlastingSkins.logger.error("Skin refresh failed for {}", target.getName(), t);
            SkinMetrics.INSTANCE.recordRefreshFailed(target.getUniqueID());
        }
    }

    private static void cascade(EntityPlayerMP target, CustomSkinProperty property, long startNanos,
            long fetchNanos) {
        MinecraftServer server = target.mcServer;
        PlayerList playerList = server.getPlayerList();
        WorldServer world = (WorldServer) target.world;

        // GameProfile mutation is what every client ultimately reads textures from.
        target.getGameProfile().getProperties().removeAll("textures");
        target.getGameProfile().getProperties().put("textures", property.getOriginalProperty());

        // Tab-list re-add to ALL online players (global, no dimension scoping).
        long broadcastStartNanos = System.nanoTime();
        NetworkMetricsHandler netHandler = NetworkMetricsHandler.getOrAttach(target.connection.netManager);
        long outBefore = netHandler != null ? netHandler.outboundBytes() : 0;
        long inBefore = netHandler != null ? netHandler.inboundBytes() : 0;
        SPacketPlayerListItem removePacket =
            new SPacketPlayerListItem(SPacketPlayerListItem.Action.REMOVE_PLAYER, target);
        SPacketPlayerListItem addPacket =
            new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, target);
        playerList.sendPacketToAllPlayers(removePacket);
        playerList.sendPacketToAllPlayers(addPacket);
        long broadcastNanos = System.nanoTime() - broadcastStartNanos;
        SkinMetrics.INSTANCE.recordBroadcastLatency(broadcastNanos);
        SkinMetrics.INSTANCE.recordBroadcast(wireSize(removePacket) + wireSize(addPacket));
        if (netHandler != null) {
            SkinMetrics.INSTANCE.recordNetworkDelta(
                netHandler.outboundBytes() - outBefore,
                netHandler.inboundBytes() - inBefore);
        }

        // Respawn in the same dimension so the target's own view refreshes without an inventory wipe.
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

        // Observer re-render via per-viewer EntityTracker untrack/re-track.
        if (Config.refreshViaEntityTracker) {
            EntityTracker tracker = world.getEntityTracker();
            tracker.untrack(self);
            tracker.track(self);
            tracker.updateVisibility(self);
        }

        // In-memory map already updated by the caller; disk flush is off-tick.
        long saveStartNanos = System.nanoTime();
        SkinRestorer.getSkinStorage().saveSkinAsync(self.getUniqueID(), property);
        long saveNanos = System.nanoTime() - saveStartNanos;

        SkinMetrics.INSTANCE.recordRefreshCompleted(
            target.getUniqueID(), startNanos, fetchNanos, saveNanos, broadcastNanos);
    }

    /** Serialized size of a tab-list packet, used as the broadcast byte count. */
    private static long wireSize(SPacketPlayerListItem packet) {
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        try {
            packet.writePacketData(buf);
            return buf.readableBytes();
        } catch (IOException e) {
            return 0;
        } finally {
            buf.release();
        }
    }
}
