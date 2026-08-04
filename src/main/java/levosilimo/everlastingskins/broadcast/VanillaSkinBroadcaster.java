/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.metrics.NetworkMetricsHandler;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.server.management.PlayerList;
import net.minecraft.world.WorldServer;

import java.io.IOException;

/**
 * Production {@link SkinBroadcaster} for Minecraft 1.12.2. Builds the
 * REMOVE+ADD tab-list pair and delivers it via the live
 * {@link PlayerList#sendPacketToAllPlayers} (1.12.2 has no
 * ClientboundBundlePacket; bundle-mode is documented as 1.21-only).
 * Drives the entity-tracker untrack/retrack when
 * {@code refreshViaEntityTracker} is enabled.
 *
 * <p>The ADD packet serializes the GameProfile at construction time, so
 * this must run after the GameProfile mutation in
 * {@link SkinRefreshTask}.
 */
public final class VanillaSkinBroadcaster implements SkinBroadcaster {

    @Override
    public void broadcastProfileChange(GameProfile newProfile, EntityPlayerMP target) {
        broadcastInternal(target, null);
    }

    @Override
    public void broadcastProfileChange(GameProfile newProfile, EntityPlayerMP target, EntityPlayerMP[] observers) {
        broadcastInternal(target, observers);
    }

    private void broadcastInternal(EntityPlayerMP target, EntityPlayerMP[] explicitObservers) {
        PlayerList playerList = target.mcServer.getPlayerList();
        long broadcastStartNanos = System.nanoTime();
        NetworkMetricsHandler netHandler = NetworkMetricsHandler.getOrAttach(target.connection.netManager);
        long outBefore = netHandler != null ? netHandler.outboundBytes() : 0;
        long inBefore = netHandler != null ? netHandler.inboundBytes() : 0;
        SPacketPlayerListItem removePacket =
            new SPacketPlayerListItem(SPacketPlayerListItem.Action.REMOVE_PLAYER, target);
        SPacketPlayerListItem addPacket =
            new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, target);
        if (explicitObservers != null) {
            for (EntityPlayerMP observer : explicitObservers) {
                observer.connection.sendPacket(removePacket);
                observer.connection.sendPacket(addPacket);
            }
        } else {
            playerList.sendPacketToAllPlayers(removePacket);
            playerList.sendPacketToAllPlayers(addPacket);
        }
        long broadcastNanos = System.nanoTime() - broadcastStartNanos;
        SkinMetrics.INSTANCE.recordBroadcastLatency(broadcastNanos);
        SkinMetrics.INSTANCE.recordBroadcast(wireSize(removePacket) + wireSize(addPacket));
        if (netHandler != null) {
            SkinMetrics.INSTANCE.recordNetworkDelta(
                netHandler.outboundBytes() - outBefore,
                netHandler.inboundBytes() - inBefore);
        }
    }

    @Override
    public void trackerUntrackRetrack(Entity entity) {
        if (!Config.refreshViaEntityTracker) {
            return;
        }
        if (!(entity instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) entity;
        WorldServer world = (WorldServer) player.world;
        EntityTracker tracker = world.getEntityTracker();
        tracker.untrack(player);
        tracker.track(player);
        tracker.updateVisibility(player);
    }

    /** Serialized size of a tab-list packet, used as the broadcast byte count. */
    private static long wireSize(Packet<?> packet) {
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        try {
            ((SPacketPlayerListItem) packet).writePacketData(buf);
            return buf.readableBytes();
        } catch (IOException e) {
            EverlastingSkins.logger.debug("Failed to measure packet wire size", e);
            return 0;
        } finally {
            buf.release();
        }
    }
}
