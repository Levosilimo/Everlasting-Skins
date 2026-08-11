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
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Server-to-client skin broadcast payload for 1.6.4 (FML 7.x
 * {@code Packet250CustomPayload}): carries the target player's USERNAME (no
 * UUID exists on this line — 1.6.4 is pre-UUID-migration) and the raw PNG
 * texture bytes when a companion client wants the payload inline.
 *
 * <p>Wire format (cape extension, per cape research — the 1.6.4 client
 * fetches {@code http://skins.minecraft.net/MinecraftCloaks/<name>.png} for
 * EVERY player with no allowlist, so byte injection is sufficient):
 * {@code [int nameLen][utf8 name][byte flags][int skinLen][skinPng][int capeLen][capePng]}
 * — flags bit 0 = hasSkin, bit 1 = hasCape. The old pre-cape format
 * {@code [int nameLen][name][int pngLen][png]} still decodes (see
 * {@link #decode(byte[])}).
 *
 * <p>1.6.4 has no netty {@code IMessage} (1.8+) and no
 * {@code SimpleNetworkWrapper}; the payload is a length-prefixed byte blob
 * inside {@code Packet250CustomPayload.data}. Encode/decode are pure Java
 * (testable without a server).
 */
public final class SkinMessage {

    /** Flag bit 0: the payload carries a skin PNG. */
    private static final int FLAG_HAS_SKIN = 1;
    /** Flag bit 1: the payload carries a cape PNG. */
    private static final int FLAG_HAS_CAPE = 2;

    private final String playerName;
    private final byte[] texturePng;
    private final byte[] capePng;

    public SkinMessage(String playerName, byte[] texturePng) {
        this(playerName, texturePng, null);
    }

    public SkinMessage(String playerName, byte[] texturePng, byte[] capePng) {
        this.playerName = playerName;
        this.texturePng = texturePng;
        this.capePng = capePng;
    }

    public String getPlayerName() {
        return playerName;
    }

    public byte[] getTexturePng() {
        return texturePng;
    }

    public byte[] getCapePng() {
        return capePng;
    }

    /**
     * Encodes a skin-only payload (pre-cape convenience, null PNG encodes as
     * a notification-only broadcast).
     */
    public static byte[] encode(String playerName, byte[] texturePng) {
        return encode(playerName, texturePng, null);
    }

    /**
     * Wire format: {@code [utf8 username][byte flags][int skin length][skin
     * png][int cape length][cape png]}. A null PNG encodes as length 0
     * (notification-only broadcast when both are null).
     */
    public static byte[] encode(String playerName, byte[] texturePng, byte[] capePng) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            byte[] name = playerName.getBytes(StandardCharsets.UTF_8);
            out.writeInt(name.length);
            out.write(name);
            int flags = (texturePng != null ? FLAG_HAS_SKIN : 0)
                | (capePng != null ? FLAG_HAS_CAPE : 0);
            out.writeByte(flags);
            if (texturePng == null) {
                out.writeInt(0);
            } else {
                out.writeInt(texturePng.length);
                out.write(texturePng);
            }
            if (capePng == null) {
                out.writeInt(0);
            } else {
                out.writeInt(capePng.length);
                out.write(capePng);
            }
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("SkinMessage encode failed", e);
        }
    }

    /**
     * Decodes both the extended (flags + cape) and the legacy pre-cape
     * ({@code [nameLen][name][pngLen][png]}) formats. A legacy payload always
     * yields a null cape.
     *
     * <p>Disambiguation: after the name, a legacy payload starts with the
     * PNG length int (high byte 0, next byte 0 for real PNGs — the payload
     * cap is 32766 bytes), while an extended payload starts with the flags
     * byte (0-3) followed by the skin-length int. The extended parse is
     * attempted first when the shape fits (first byte ≤ 3 and enough bytes
     * remain); it only wins when it consumes the payload EXACTLY. A legacy
     * payload with a real PNG always fails that attempt (its length int
     * over-reads into the PNG magic, exceeding the remaining bytes), so it
     * falls back cleanly to the legacy parse.
     *
     * <p>Every length prefix is bounds-checked against the remaining unread
     * payload BEFORE allocation ({@link #checkLength}), so a corrupted or
     * malicious prefix fails with {@link IllegalArgumentException} (which
     * the client handler catches) instead of an {@link OutOfMemoryError}
     * on the packet thread (lib-18 audit remediation).
     */
    public static SkinMessage decode(byte[] payload) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            int nameLen = in.readInt();
            checkLength(nameLen, payload.length - 4, "name");
            byte[] name = new byte[nameLen];
            in.readFully(name);
            String playerName = new String(name, StandardCharsets.UTF_8);
            int remaining = payload.length - 4 - nameLen;
            if (remaining >= 9 && (payload[4 + nameLen] & 0xFF) <= 3) {
                SkinMessage extended = tryDecodeExtended(in, playerName);
                if (extended != null) {
                    return extended;
                }
                // Not an extended payload — rewind and parse the legacy shape.
                in = new DataInputStream(new ByteArrayInputStream(payload));
                in.readInt();
                in.readFully(name);
            }
            int pngLen = in.readInt();
            checkLength(pngLen, payload.length - 8 - nameLen, "png");
            byte[] png = null;
            if (pngLen > 0) {
                png = new byte[pngLen];
                in.readFully(png);
            }
            return new SkinMessage(playerName, png);
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

    /**
     * Attempts the extended-format parse; returns null (and the caller falls
     * back to the legacy shape) unless the extended parse consumes the whole
     * payload.
     */
    private static SkinMessage tryDecodeExtended(DataInputStream in, String playerName) throws IOException {
        try {
            int flags = in.readUnsignedByte();
            int skinLen = in.readInt();
            checkLength(skinLen, in.available(), "skin");
            byte[] skin = null;
            if (skinLen > 0) {
                skin = new byte[skinLen];
                in.readFully(skin);
            }
            int capeLen = in.readInt();
            checkLength(capeLen, in.available(), "cape");
            byte[] cape = null;
            if (capeLen > 0) {
                cape = new byte[capeLen];
                in.readFully(cape);
            }
            if (in.available() != 0) {
                throw new EOFException("trailing bytes after extended payload");
            }
            return new SkinMessage(playerName, skin, cape);
        } catch (IOException | IllegalArgumentException e) {
            // A guard violation makes this a failed extended attempt (a
            // legacy payload's pngLen-derived skinLen always over-reads) —
            // fall back to the legacy shape exactly as an EOF would.
            return null;
        }
    }
}
