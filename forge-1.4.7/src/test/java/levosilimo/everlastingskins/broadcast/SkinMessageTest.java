/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 1.4.7 broadcast payload tests (pure Java — no server, no netty).
 *
 * <p>1.4.7 is pre-UUID-migration: the payload is username-keyed (no
 * {@code com.mojang.authlib.GameProfile} on this line) and rides inside a
 * {@code Packet250CustomPayload} data blob (FML 4.7 custom-payload surface —
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
    public void encodeDecodeRoundTripsRealPngBytes() throws Exception {
        // A real PNG payload (the joint client broadcast now carries actual
        // texture bytes — lib-5, PR #422), well under the 32766-byte
        // Packet250CustomPayload cap (MC-16910).
        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(64, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", bos);
        byte[] png = bos.toByteArray();
        assertTrue(png.length < 32766);

        SkinMessage message = SkinMessage.decode(SkinMessage.encode("Steve", png));
        assertEquals("Steve", message.getPlayerName());
        assertArrayEquals(png, message.getTexturePng());
        assertNull(message.getCapePng());
    }

    @Test
    public void encodeDecodeRoundTripsSkinAndCape() {
        byte[] skin = new byte[]{1, 2, 3, 4, 5};
        byte[] cape = new byte[]{6, 7, 8};

        SkinMessage message = SkinMessage.decode(SkinMessage.encode("Alex", skin, cape));

        assertEquals("Alex", message.getPlayerName());
        assertArrayEquals(skin, message.getTexturePng());
        assertArrayEquals(cape, message.getCapePng());
    }

    @Test
    public void encodeDecodeRoundTripsCapeOnly() {
        byte[] cape = new byte[]{9, 9, 9};

        SkinMessage message = SkinMessage.decode(SkinMessage.encode("Alex", null, cape));

        assertEquals("Alex", message.getPlayerName());
        assertNull(message.getTexturePng());
        assertArrayEquals(cape, message.getCapePng());
    }

    @Test
    public void flagsBitsReflectFieldPresence() {
        // Wire layout: [int nameLen][utf8 name][byte flags][int skinLen]...
        byte[] skin = new byte[]{1};
        byte[] cape = new byte[]{2};
        int flagsOffset = 4 + "Alex".length();

        assertEquals(1, encodeFlagsByte("Alex", skin, null, flagsOffset)); // bit 0 = hasSkin
        assertEquals(2, encodeFlagsByte("Alex", null, cape, flagsOffset)); // bit 1 = hasCape
        assertEquals(3, encodeFlagsByte("Alex", skin, cape, flagsOffset));
        assertEquals(0, encodeFlagsByte("Alex", null, null, flagsOffset));
    }

    @Test
    public void legacyPayloadWithoutFlagsStillDecodes() throws Exception {
        // Pre-cape wire format: [int nameLen][utf8 name][int pngLen][png]
        // (the shape the joint client broadcast landed with, PR #426).
        byte[] png = new byte[]{10, 11, 12, 13};
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        byte[] name = "Legacy".getBytes(StandardCharsets.UTF_8);
        out.writeInt(name.length);
        out.write(name);
        out.writeInt(png.length);
        out.write(png);
        out.flush();

        SkinMessage message = SkinMessage.decode(bos.toByteArray());

        assertEquals("Legacy", message.getPlayerName());
        assertArrayEquals(png, message.getTexturePng());
        assertNull(message.getCapePng());
    }

    @Test
    public void legacyNotificationPayloadStillDecodes() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        byte[] name = "Legacy".getBytes(StandardCharsets.UTF_8);
        out.writeInt(name.length);
        out.write(name);
        out.writeInt(0);
        out.flush();

        SkinMessage message = SkinMessage.decode(bos.toByteArray());

        assertEquals("Legacy", message.getPlayerName());
        assertNull(message.getTexturePng());
        assertNull(message.getCapePng());
    }

    private static int encodeFlagsByte(String name, byte[] skin, byte[] cape, int flagsOffset) {
        byte[] payload = SkinMessage.encode(name, skin, cape);
        return payload[flagsOffset] & 0xFF;
    }

    @Test
    public void channelNameFitsThe16CharCap() {
        // FML 4.7 NetworkRegistry throws beyond 16 chars; the lane's channel
        // name is exactly 16 — at the ceiling, never lengthen it.
        assertEquals(16, SkinBroadcaster.CHANNEL.length());
    }

    @Test(expected = IllegalArgumentException.class)
    public void malformedPayloadThrows() {
        SkinMessage.decode(new byte[]{0, 1, 2, 3});
    }

    @Test(expected = IllegalArgumentException.class)
    public void malformedNameLengthRejectedBeforeAllocation() throws Exception {
        // nameLen = 2^31-1: the bounds guard must reject it (an
        // IllegalArgumentException the client handler catches), not
        // allocate ~2 GiB and OOM the packet thread (lib-18 audit).
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        new DataOutputStream(bos).writeInt(Integer.MAX_VALUE);
        SkinMessage.decode(bos.toByteArray());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeNameLengthRejectedBeforeAllocation() throws Exception {
        // nameLen = -1 (unsigned 0xFFFFFFFF): the len >= 0 half of the guard.
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        new DataOutputStream(bos).writeInt(-1);
        SkinMessage.decode(bos.toByteArray());
    }

    @Test(expected = IllegalArgumentException.class)
    public void malformedPngLengthRejectedBeforeAllocation() throws Exception {
        // pngLen = 2^31-1: the bounds guard must reject it before the
        // allocation (lib-18 audit).
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        byte[] name = "Legacy".getBytes(StandardCharsets.UTF_8);
        out.writeInt(name.length);
        out.write(name);
        out.writeInt(Integer.MAX_VALUE);
        out.flush();
        SkinMessage.decode(bos.toByteArray());
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
