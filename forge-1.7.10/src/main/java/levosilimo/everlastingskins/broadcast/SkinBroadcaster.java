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
        EnumMap<Side, FMLEmbeddedChannel> channels = NetworkRegistry.INSTANCE.newChannel(CHANNEL);
        serverChannel = channels.get(Side.SERVER);
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
    }
}
