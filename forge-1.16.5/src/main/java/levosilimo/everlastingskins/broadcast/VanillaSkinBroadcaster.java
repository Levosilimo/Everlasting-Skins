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
import net.minecraft.client.network.play.IClientPlayNetHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPlayerListItemPacket;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

/**
 * Production {@link SkinBroadcaster} for Minecraft 1.16.5. Builds the
 * unified REMOVE+ADD tab-list pair (1.16.5 has a single
 * {@link ClientboundPlayerInfoPacket} covering both actions; the separate
 * REMOVE packet and the bundle packet are 1.19.3+/1.19.4+ additions) and
 * delivers it via the live {@link PlayerList}. Also drives the
 * chunk-source remove/add pair that re-tracks the target on observers'
 * entity trackers when {@code REFRESH_VIA_ENTITY_TRACKER} is enabled.
 *
 * <p>UPDATE_DISPLAY_NAME does not serialize the GameProfile, so observers
 * never received new textures; REMOVE+ADD_PLAYER forces clients to
 * re-learn the profile. Dimension-scoped only when
 * {@code DIMENSION_SCOPED_BROADCAST} is enabled. {@code
 * BROADCAST_USE_BUNDLE} (ClientboundBundlePacket) does not exist on
 * 1.16.5 and is inert here.
 */
public final class VanillaSkinBroadcaster implements SkinBroadcaster {

    @Override
    public void broadcastProfileChange(GameProfile newProfile, ServerPlayerEntity target) {
        broadcastInternal(target, null);
    }

    @Override
    public void broadcastProfileChange(GameProfile newProfile, ServerPlayerEntity target, ServerPlayerEntity[] observers) {
        broadcastInternal(target, observers);
    }

    private void broadcastInternal(ServerPlayerEntity target, ServerPlayerEntity[] explicitObservers) {
        PlayerList playerlist = target.getServer().getPlayerList();
        ServerWorld serverLevel = target.getLevel();
        RegistryKey<World> dimension = serverLevel.dimension();
        IPacket<IClientPlayNetHandler> removePacket =
            new SPlayerListItemPacket(SPlayerListItemPacket.Action.REMOVE_PLAYER, target);
        IPacket<IClientPlayNetHandler> addPacket =
            new SPlayerListItemPacket(SPlayerListItemPacket.Action.ADD_PLAYER, target);

        NetworkMetricsHandler netHandler = NetworkMetricsHandler.getOrAttach(target.connection.getConnection());
        long outBefore = netHandler != null ? netHandler.outboundBytes() : 0;
        long inBefore = netHandler != null ? netHandler.inboundBytes() : 0;
        long start = System.nanoTime();

        if (explicitObservers != null) {
            for (ServerPlayerEntity observer : explicitObservers) {
                if (Config.DIMENSION_SCOPED_BROADCAST.get()
                        && !observer.getLevel().dimension().equals(dimension)) {
                    continue;
                }
                observer.connection.send(removePacket);
                observer.connection.send(addPacket);
            }
        } else if (Config.DIMENSION_SCOPED_BROADCAST.get()) {
            playerlist.broadcastAll(removePacket, dimension);
            playerlist.broadcastAll(addPacket, dimension);
        } else {
            playerlist.broadcastAll(removePacket);
            playerlist.broadcastAll(addPacket);
        }

        long broadcastNanos = System.nanoTime() - start;
        SkinMetrics.INSTANCE.recordBroadcastLatency(broadcastNanos);
        SkinMetrics.INSTANCE.recordSpikeBroadcast(broadcastNanos);
        SkinMetrics.INSTANCE.recordBroadcast(wireSize(removePacket) + wireSize(addPacket));
        if (netHandler != null) {
            SkinMetrics.INSTANCE.recordNetworkDelta(
                netHandler.outboundBytes() - outBefore,
                netHandler.inboundBytes() - inBefore);
        }
    }

    @Override
    public void trackerUntrackRetrack(Entity entity) {
        if (!Config.REFRESH_VIA_ENTITY_TRACKER.get()) {
            return;
        }
        if (!(entity instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) entity;
        // Per-viewer entity re-render via the tracker: untrack/track forces
        // remote clients to destroy+re-spawn the entity, so their cached
        // playerInfo re-fetches the new profile entry. Without this step
        // observers keep rendering the stale skin even after the tab-list
        // REMOVE+ADD (PR #121 dropped this step; lib-13 research: regression).
        ServerWorld level = player.getLevel();
        level.getChunkSource().removeEntity(player);
        level.getChunkSource().addEntity(player);
    }

    /** Serialized byte size of the packets, measured by writing them out. */
    private static int wireSize(IPacket<?> packet) {
        try {
            PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
            packet.write(buf);
            int size = buf.readableBytes();
            buf.release();
            return size;
        } catch (Exception e) {
            EverlastingSkins.logger.debug("Failed to measure packet wire size", e);
            return 0;
        }
    }
}
