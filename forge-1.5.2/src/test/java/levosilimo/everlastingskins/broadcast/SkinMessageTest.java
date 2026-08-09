/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * 1.5.2 broadcast payload tests (pure Java — no server, no netty).
 *
 * <p>1.5.2 is pre-UUID: the payload is username-keyed (no
 * {@code com.mojang.authlib.GameProfile} on this line) and rides inside a
 * {@code Packet250CustomPayload} data blob (FML 5.2 custom-payload surface —
 * no {@code IMessage} until 1.8).
 */
public class SkinMessageTest {

    @Test
    public void encodeDecodeRoundTripsUsernameOnly() {
        byte[] payload = SkinMessage.encode("Notch", null);
        SkinMessage message = SkinMessage.decode(payload);
        assertEquals("Notch", message.getPlayerName());
        assertNull(message.getTexturePng());
    }

    @Test
    public void encodeDecodeRoundTripsWithPng() {
        byte[] png = new byte[]{1, 2, 3, 4, 5};
        SkinMessage message = SkinMessage.decode(SkinMessage.encode("Steve", png));
        assertEquals("Steve", message.getPlayerName());
        assertArrayEquals(png, message.getTexturePng());
    }

    @Test
    public void channelNameFitsThe16CharCap() {
        // FML 5.2 NetworkRegistry throws beyond 16 chars; the lane's channel
        // name is exactly 16 — at the ceiling, never lengthen it.
        assertEquals(16, SkinBroadcaster.CHANNEL.length());
    }

    @Test(expected = IllegalArgumentException.class)
    public void malformedPayloadThrows() {
        SkinMessage.decode(new byte[]{0, 1, 2, 3});
    }

    @Test
    public void nameBytesAreUtf8Encoded() {
        byte[] payload = SkinMessage.encode("Notch", null);
        // [int length][name bytes][int 0]
        int nameLen = ((payload[0] & 0xFF) << 24) | ((payload[1] & 0xFF) << 16)
            | ((payload[2] & 0xFF) << 8) | (payload[3] & 0xFF);
        assertEquals(5, nameLen);
        assertEquals("Notch", new String(payload, 4, nameLen, StandardCharsets.UTF_8));
    }
}
