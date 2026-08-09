/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * 1.6.4 lane-local texture decoder tests (PNG payload validation for the
 * broadcast pipeline).
 *
 * <p>Deterministic fixtures only (memory #1115): the PNG is generated
 * in-memory with ImageIO — no live HTTP, no filesystem.
 */
public class TextureDecoderTest {

    @Test
    public void decodesValidPng() throws IOException {
        BufferedImage decoded = TextureDecoder.decode(png(4, 4));
        assertNotNull(decoded);
        assertEquals(4, decoded.getWidth());
        assertEquals(4, decoded.getHeight());
    }

    @Test
    public void decodesSkinAspectPng() throws IOException {
        // Classic 64x32 skin texture aspect.
        BufferedImage decoded = TextureDecoder.decode(png(64, 32));
        assertEquals(64, decoded.getWidth());
        assertEquals(32, decoded.getHeight());
    }

    @Test(expected = IOException.class)
    public void garbageBytesThrow() throws IOException {
        TextureDecoder.decode(new byte[]{0, 1, 2, 3, 4, 5});
    }

    @Test(expected = IOException.class)
    public void emptyBytesThrow() throws IOException {
        TextureDecoder.decode(new byte[0]);
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bos);
        return bos.toByteArray();
    }
}
