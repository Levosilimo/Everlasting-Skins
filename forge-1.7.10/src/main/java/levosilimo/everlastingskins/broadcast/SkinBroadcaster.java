/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import cpw.mods.fml.common.network.FMLEmbeddedChannel;
import cpw.mods.fml.common.network.FMLOutboundHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInboundHandlerAdapter;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import net.minecraft.entity.player.EntityPlayerMP;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;

/**
 * 1.7.10 skin-broadcast channel.
 *
 * <p>FML 1.7.10 removed the 1.6.4 {@code @NetworkMod} annotation (verified
 * absent from 10.13.4.1614 — only the internal {@code NetworkModHolder}
 * survives); the replacement surface is netty-based:
 * {@link NetworkRegistry#INSTANCE}.{@code newChannel(...)} returning
 * {@link FMLEmbeddedChannel}s. The channel name must not start with
 * {@code MC|}, {@code FML} or the NUL prefix (enforced by NetworkRegistry).
 *
     * <p>Payloads are {@link FMLProxyPacket}s carrying the target player's name;
     * the client-side handler would re-fetch the profile (server-side mod: the
     * channel is the notification surface, matching the sibling lanes' broadcast
     * contract).
     *
     * <p>The channel is created with a no-op netty handler: netty's
     * {@code EmbeddedChannel} constructor rejects an empty pipeline
     * ("handlers is empty.") — a real 1.7.10 server boot fails POSTINITIALIZATION
     * on the zero-handler {@code newChannel(String)} overload (found by the
     * slice-2 E2E spike; unit tests cannot see it because the lane's test
     * classpath never boots FML's network layer).
     */
public final class SkinBroadcaster {

    public static final String CHANNEL = "everlastingskins";

    private static volatile FMLEmbeddedChannel serverChannel;

    private SkinBroadcaster() {}

    /** Registers the channel; safe to call once per JVM (idempotent). */
    public static void init() {
        if (serverChannel != null) {
            return;
        }
        EnumMap<Side, FMLEmbeddedChannel> channels =
            NetworkRegistry.INSTANCE.newChannel(CHANNEL, NoOpHandler.INSTANCE);
        serverChannel = channels.get(Side.SERVER);
    }

    /**
     * Stateless, {@code @Sharable} no-op pipeline member: FML 1.7.10 adds the
     * same instance to every side's embedded channel, so a plain adapter
     * (non-sharable) is rejected on the second add ("not a @Sharable handler").
     */
    @Sharable
    public static final class NoOpHandler extends ChannelInboundHandlerAdapter {
        public static final NoOpHandler INSTANCE = new NoOpHandler();

        private NoOpHandler() {
        }
    }

    /** Broadcasts a skin-change notification for {@code target} to all players. */
    public static void broadcastProfileChange(EntityPlayerMP target) {
        FMLEmbeddedChannel channel = serverChannel;
        if (channel == null) {
            return;
        }
        byte[] payload = target.getCommandSenderName().getBytes(StandardCharsets.UTF_8);
        FMLProxyPacket packet = new FMLProxyPacket(Unpooled.wrappedBuffer(payload), CHANNEL);
        channel.attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.ALL);
        channel.writeAndFlush(packet);
        SkinMetrics.INSTANCE.recordBroadcast(payload.length);
    }
}
