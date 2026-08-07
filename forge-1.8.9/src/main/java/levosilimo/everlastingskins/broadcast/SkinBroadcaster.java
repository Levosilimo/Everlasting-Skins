/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import levosilimo.everlastingskins.skinchanger.TextureDecoder;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.io.IOException;
import java.util.UUID;

/**
 * 1.8.9 skin broadcast channel (pre-ModLauncher): a simple channel created
 * via {@link NetworkRegistry#newSimpleChannel(String)} — no PacketDistributor
 * on this version (that is 1.13+); server-to-client broadcast is
 * {@link SimpleNetworkWrapper#sendToAll}. The payload is validated through
 * {@link TextureDecoder} before send.
 */
public final class SkinBroadcaster {

    public static final String CHANNEL_NAME = "everlastingskins";

    private static SimpleNetworkWrapper channel;

    private SkinBroadcaster() {}

    /** Channel bootstrap — must run during INIT (FML 8.x). */
    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        channel.registerMessage(SkinMessage.Handler.class, SkinMessage.class, 0, Side.CLIENT);
    }

    /**
     * Broadcasts a skin texture to every connected client.
     *
     * @throws IOException if the payload is not a decodable image
     */
    public static void broadcastSkin(UUID playerId, byte[] texturePng) throws IOException {
        // Validate before send: never push undecodable bytes to clients.
        TextureDecoder.decode(texturePng);
        channel.sendToAll(new SkinMessage(playerId, texturePng));
    }
}
