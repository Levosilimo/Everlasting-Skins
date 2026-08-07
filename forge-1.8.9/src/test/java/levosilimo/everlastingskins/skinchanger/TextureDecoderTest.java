/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure-JUnit tests for the lane-local {@link TextureDecoder}
 * (memory #1115: fully deterministic local image bytes — no HTTP).
 */
class TextureDecoderTest {

    @Test
    void decodes64x32SkinTexture() throws IOException {
        byte[] png = encodePng(64, 32);
        BufferedImage decoded = TextureDecoder.decode(png);
        assertNotNull(decoded);
        assertEquals(64, decoded.getWidth());
        assertEquals(32, decoded.getHeight());
    }

    @Test
    void rejectsNonImageBytes() {
        byte[] garbage = "definitely not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> TextureDecoder.decode(garbage));
    }

    @Test
    void preservesTransparentPixels() throws IOException {
        BufferedImage source = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0x00000000); // fully transparent
        source.setRGB(1, 0, 0xFF336699); // opaque
        BufferedImage decoded = TextureDecoder.decode(encodePng(source));
        assertEquals(0x00000000, decoded.getRGB(0, 0));
        assertEquals(0xFF336699, decoded.getRGB(1, 0));
    }

    private static byte[] encodePng(int width, int height) throws IOException {
        return encodePng(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB));
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
