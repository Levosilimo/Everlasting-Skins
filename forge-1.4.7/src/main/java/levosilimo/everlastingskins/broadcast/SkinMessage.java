/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Server-to-client skin broadcast payload for 1.4.7 (FML 4.7
 * {@code Packet250CustomPayload}): carries the target player's USERNAME (no
 * UUID exists on this line — 1.4.7 is pre-UUID-migration) and the raw PNG
 * texture bytes when a companion client wants the payload inline.
 *
 * <p>1.4.7 has no netty {@code IMessage} (1.8+) and no
 * {@code SimpleNetworkWrapper}; the payload is a length-prefixed byte blob
 * inside {@code Packet250CustomPayload.data}. Encode/decode are pure Java
 * (testable without a server).
 */
public final class SkinMessage {

    private final String playerName;
    private final byte[] texturePng;

    public SkinMessage(String playerName, byte[] texturePng) {
        this.playerName = playerName;
        this.texturePng = texturePng;
    }

    public String getPlayerName() {
        return playerName;
    }

    public byte[] getTexturePng() {
        return texturePng;
    }

    /**
     * Wire format: [utf8 username][int png length][png bytes]. A null PNG
     * encodes as length 0 (notification-only broadcast).
     *
     * <p>Every length prefix is bounds-checked against the remaining unread
     * payload BEFORE allocation ({@link #checkLength}), so a corrupted or
     * malicious prefix fails with {@link IllegalArgumentException} instead
     * of an {@link OutOfMemoryError} on the packet thread (lib-18 audit
     * remediation).
     */
    public static byte[] encode(String playerName, byte[] texturePng) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            byte[] name = playerName.getBytes(StandardCharsets.UTF_8);
            out.writeInt(name.length);
            out.write(name);
            if (texturePng == null) {
                out.writeInt(0);
            } else {
                out.writeInt(texturePng.length);
                out.write(texturePng);
            }
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("SkinMessage encode failed", e);
        }
    }

    public static SkinMessage decode(byte[] payload) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            int nameLen = in.readInt();
            checkLength(nameLen, payload.length - 4, "name");
            byte[] name = new byte[nameLen];
            in.readFully(name);
            int pngLen = in.readInt();
            checkLength(pngLen, payload.length - 8 - nameLen, "png");
            byte[] png = null;
            if (pngLen > 0) {
                png = new byte[pngLen];
                in.readFully(png);
            }
            return new SkinMessage(new String(name, StandardCharsets.UTF_8), png);
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed SkinMessage payload", e);
        }
    }

    /**
     * Bounds guard for a length-prefixed field: rejects any length prefix
     * that is negative or exceeds the remaining unread payload BEFORE the
     * array allocation. The client handler catches
     * {@link IllegalArgumentException}; an {@link OutOfMemoryError} would
     * escape {@code catch (Exception)} and kill the network thread.
     */
    private static void checkLength(int len, int remaining, String field) {
        if (len < 0 || len > remaining) {
            throw new IllegalArgumentException("Malformed SkinMessage payload: " + field
                + " length " + len + " exceeds remaining " + remaining + " bytes");
        }
    }
}
