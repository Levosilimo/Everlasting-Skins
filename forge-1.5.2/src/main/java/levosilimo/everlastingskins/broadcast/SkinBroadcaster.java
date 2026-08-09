/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.INetworkManager;
import net.minecraft.src.Packet;
import net.minecraft.src.Packet250CustomPayload;

/**
 * 1.5.2 skin-broadcast channel (FML 5.2 custom-payload surface).
 *
 * <p>FML 5.2 has no netty {@code SimpleNetworkWrapper} (that is 1.8+) and no
 * {@code newChannel} on {@link NetworkRegistry} — the 1.5.2 surface is
 * {@code NetworkRegistry.instance().registerChannel(IPacketHandler, String)}
 * with {@link Packet250CustomPayload} payloads, sent via
 * {@link PacketDispatcher}. The channel name must be ≤ 16 chars
 * (NetworkRegistry.java:99-105 throws beyond that); {@code everlastingskins}
 * is exactly 16 — at the ceiling, never lengthen it.
 *
 * <p>Payloads carry the target player's username
 * ({@link SkinMessage#encode(String, byte[])}); the client-side handler would
 * re-fetch the skin (server-side mod: the channel is the notification
 * surface, matching the sibling lanes' broadcast contract). The registered
 * {@link IPacketHandler} is a server-side no-op — 1.5.2 clients never send
 * on this channel.
 */
public final class SkinBroadcaster {

    public static final String CHANNEL = "everlastingskins";

    private static volatile boolean registered;

    private SkinBroadcaster() {}

    /** Registers the channel; safe to call once per JVM (idempotent). */
    public static void init() {
        if (registered) {
            return;
        }
        NetworkRegistry.instance().registerChannel(new ServerPacketHandler(), CHANNEL);
        registered = true;
    }

    /** Broadcasts a skin-change notification for {@code target} to all players. */
    public static void broadcastProfileChange(EntityPlayer target) {
        byte[] payload = SkinMessage.encode(target.getCommandSenderName(), null);
        Packet packet = PacketDispatcher.getPacket(CHANNEL, payload);
        PacketDispatcher.sendPacketToAllPlayers(packet);
    }

    /** Server-side no-op: the mod is serverSideOnly (FML 5.2). */
    private static final class ServerPacketHandler implements IPacketHandler {
        @Override
        public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player player) {
            // No client→server traffic on this channel.
        }
    }
}
