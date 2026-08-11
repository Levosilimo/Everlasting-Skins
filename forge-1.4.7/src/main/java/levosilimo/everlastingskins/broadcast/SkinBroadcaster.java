/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import cpw.mods.fml.common.network.PacketDispatcher;
import net.minecraft.src.Packet;

/**
 * 1.4.7 skin-broadcast channel (FML 4.7 custom-payload surface).
 *
 * <p>FML 4.7 has no netty {@code SimpleNetworkWrapper} (that is 1.8+) and no
 * {@code newChannel} — the 1.5.2 surface is {@code Packet250CustomPayload}
 * payloads, sent via {@link PacketDispatcher}. The channel name must be
 * ≤ 16 chars (NetworkRegistry.java throws beyond that);
 * {@code everlastingskins} is exactly 16 — at the ceiling, never lengthen it.
 *
 * <p>CHANNEL OWNERSHIP: since the joint client side landed (lib-5, PR #422)
 * the channel is registered by the {@code @NetworkMod} joint pattern on the
 * @Mod class — {@link ClientSkinHandler} on client processes,
 * {@link ServerPacketHandler} on server processes — NOT by a manual
 * {@code registerChannel} here (a universal no-op registration would shadow
 * the client-side dispatch). {@link #broadcastProfileChange} only sends.
 *
 * <p>Payloads carry the target player's username AND the flattened 64x32 PNG
 * bytes ({@link SkinMessage#encode(String, byte[])}); the client handler
 * decodes and injects the pixels. Payload cap: 32766 bytes
 * (Packet250CustomPayload 2-byte length, MC-16910) — a flattened 64x32 PNG is
 * ≈ 1-4 KB, far under the cap; oversized payloads are dropped defensively.
 */
public final class SkinBroadcaster {

    public static final String CHANNEL = "everlastingskins";

    /** Packet250CustomPayload 2-byte length cap (MC-16910). */
    private static final int MAX_PAYLOAD_BYTES = 32766;

    private SkinBroadcaster() {}

    /** Broadcasts a skin-change payload for {@code playerName} to all players. */
    public static void broadcastProfileChange(String playerName, byte[] pngBytes) {
        byte[] payload = SkinMessage.encode(playerName, pngBytes);
        if (payload.length > MAX_PAYLOAD_BYTES) {
            System.err.println("EverlastingSkins: skin payload for '" + playerName
                + "' exceeds " + MAX_PAYLOAD_BYTES + " bytes — dropped");
            return;
        }
        Packet packet = PacketDispatcher.getPacket(CHANNEL, payload);
        PacketDispatcher.sendPacketToAllPlayers(packet);
    }
}
