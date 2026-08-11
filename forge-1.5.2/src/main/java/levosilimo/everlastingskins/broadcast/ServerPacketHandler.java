/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.Player;
import net.minecraft.src.INetworkManager;
import net.minecraft.src.Packet250CustomPayload;

/**
 * 1.5.2 server-side receiver for the {@code everlastingskins} channel,
 * referenced by the {@code @NetworkMod} joint pattern
 * (serverPacketHandlerSpec). FML dispatches server-side custom payloads to
 * the server handler (registered with Side.SERVER) and client-side payloads
 * to {@link ClientSkinHandler} — both handlers live in the same jar.
 */
public final class ServerPacketHandler implements IPacketHandler {

    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player player) {
        // No client→server traffic on this channel.
    }
}
