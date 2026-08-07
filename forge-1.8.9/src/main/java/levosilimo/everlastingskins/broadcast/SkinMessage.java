/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

/**
 * Server-to-client skin broadcast payload for 1.8.9 (pre-ModLauncher
 * SimpleNetworkWrapper): carries the target player UUID and the raw PNG
 * texture bytes. Client side is a no-op handler — this mod is
 * serverSideOnly; the payload is delivered for companion client mods.
 */
public class SkinMessage implements IMessage {

    private UUID playerId;
    private byte[] texturePng;

    /** Required by SimpleNetworkWrapper's reflective instantiation. */
    public SkinMessage() {}

    public SkinMessage(UUID playerId, byte[] texturePng) {
        this.playerId = playerId;
        this.texturePng = texturePng;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public byte[] getTexturePng() {
        return texturePng;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        long most = buf.readLong();
        long least = buf.readLong();
        playerId = new UUID(most, least);
        int length = buf.readInt();
        texturePng = new byte[length];
        buf.readBytes(texturePng);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(playerId.getMostSignificantBits());
        buf.writeLong(playerId.getLeastSignificantBits());
        buf.writeInt(texturePng.length);
        buf.writeBytes(texturePng);
    }

    /** Client-side no-op: the mod is serverSideOnly (1.8.9 FML). */
    public static class Handler implements IMessageHandler<SkinMessage, IMessage> {
        @Override
        public IMessage onMessage(SkinMessage message, MessageContext ctx) {
            return null;
        }
    }
}
