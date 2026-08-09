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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;


/**
 * Production {@link SkinBroadcaster} for Minecraft 1.18.2. Builds the
 * REMOVE+ADD tab-list pair and delivers it via the live {@link PlayerList}
 * or directly to each filtered observer's connection. 1.18.2 predates
 * {@code ClientboundBundlePacket} (1.19.3), so {@code BROADCAST_USE_BUNDLE}
 * is a no-op here: the pair is always sent un-bundled. Also
 * drives the chunk-source remove/add pair that re-tracks the target on
 * observers' entity trackers when {@code REFRESH_VIA_ENTITY_TRACKER}
 * is enabled.
 *
 * <p>UPDATE_DISPLAY_NAME does not serialize the GameProfile, so
 * observers never received new textures; REMOVE+ADD_PLAYER forces
 * clients to re-learn the profile. Dimension-scoped only when
 * {@code DIMENSION_SCOPED_BROADCAST} is enabled.
 */
public final class VanillaSkinBroadcaster implements SkinBroadcaster {

    @Override
    public void broadcastProfileChange(GameProfile newProfile, ServerPlayer target) {
        broadcastInternal(target, null);
    }

    @Override
    public void broadcastProfileChange(GameProfile newProfile, ServerPlayer target, ServerPlayer[] observers) {
        broadcastInternal(target, observers);
    }

    private void broadcastInternal(ServerPlayer target, ServerPlayer[] explicitObservers) {
        PlayerList playerlist = target.getServer().getPlayerList();
        ServerLevel serverLevel = target.getLevel();
        ResourceKey<Level> dimension = serverLevel.dimension();
        Packet<ClientGamePacketListener> removePacket =
            new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER, target);
        Packet<ClientGamePacketListener> addPacket =
            new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, target);

        NetworkMetricsHandler netHandler = NetworkMetricsHandler.getOrAttach(target.connection.connection);
        long outBefore = netHandler != null ? netHandler.outboundBytes() : 0;
        long inBefore = netHandler != null ? netHandler.inboundBytes() : 0;
        long start = System.nanoTime();

        // 1.18.2 has no ClientboundBundlePacket (1.19.3+), so
        // BROADCAST_USE_BUNDLE is a no-op: REMOVE+ADD always go out as an
        // un-bundled pair.
        if (explicitObservers != null) {
            for (ServerPlayer observer : explicitObservers) {
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
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        // Per-viewer entity re-render via the tracker: untrack/track forces
        // remote clients to destroy+re-spawn the entity, so their cached
        // playerInfo re-fetches the new profile entry. Without this step
        // observers keep rendering the stale skin even after the tab-list
        // REMOVE+ADD (PR #121 dropped this step; lib-13 research: regression).
        ServerLevel level = player.getLevel();
        level.getChunkSource().removeEntity(player);
        level.getChunkSource().addEntity(player);
    }

    /** Serialized byte size of the packets (1.20.1: no StreamCodec). */
    private static int wireSize(Packet<?> packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.write(buf);
            return buf.readableBytes();
        } catch (Exception e) {
            EverlastingSkins.logger.debug("Failed to measure packet wire size", e);
            return 0;
        } finally {
            buf.release();
        }
    }
}
