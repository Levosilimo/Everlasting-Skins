/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.client;

import levosilimo.everlastingskins.skinchanger.TextureDecoder;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ImageBufferDownload;
import net.minecraft.src.ResourceLocation;
import net.minecraft.src.ThreadDownloadImageData;
import net.minecraft.src.World;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 1.6.4 client-side skin applier tests (lib-5 joint client side): pure-Java
 * decode/flatten/findPlayer plus the TDI injection surface, with mocked
 * client classes (mockito + reflection setField, mirroring the lane's
 * existing test pattern — memory #1115: no live client, no GL).
 */
public class ClientSkinApplierTest {

    @Test
    public void decodeReturnsPngPixels() throws Exception {
        BufferedImage source = solid(4, 4, 0xFF112233);
        BufferedImage decoded = ClientSkinApplier.decode(png(source));
        assertNotNull(decoded);
        assertEquals(4, decoded.getWidth());
        assertEquals(0xFF112233, decoded.getRGB(2, 2));
    }

    @Test(expected = java.io.IOException.class)
    public void decodeRejectsGarbage() throws Exception {
        ClientSkinApplier.decode(new byte[]{1, 2, 3});
    }

    @Test
    public void flattenPassesLegacy64x32Through() {
        BufferedImage legacy = solid(64, 32, 0xFF445566);
        assertSame(legacy, ClientSkinApplier.flattenToLegacy(legacy));
    }

    @Test
    public void flattenCropsModern64x64ToTopHalf() {
        // 64x64 modern skin: the top half IS the legacy 64x32 layout; the
        // bottom-left regions (left arm / left leg) are dropped because the
        // pre-1.8 model mirrors the left limbs from the right-limb regions.
        BufferedImage modern = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                modern.setRGB(x, y, 0xFF000000);
            }
        }
        modern.setRGB(10, 10, 0xFFAA0000); // head region — kept
        modern.setRGB(5, 25, 0xFF00BB00);  // right leg region — kept
        modern.setRGB(45, 55, 0xFF0000CC); // left arm region — dropped

        BufferedImage legacy = ClientSkinApplier.flattenToLegacy(modern);

        assertEquals(64, legacy.getWidth());
        assertEquals(32, legacy.getHeight());
        assertEquals(0xFFAA0000, legacy.getRGB(10, 10));
        assertEquals(0xFF00BB00, legacy.getRGB(5, 25));
        // The dropped region must not leak into the canvas.
        assertEquals(0xFF000000, legacy.getRGB(45, 25));
    }

    @Test
    public void flattenDraws32x32UnscaledLikeVanilla() {
        BufferedImage small = solid(32, 32, 0xFF123456);
        small.setRGB(8, 8, 0xFFAABBCC);
        BufferedImage legacy = ClientSkinApplier.flattenToLegacy(small);
        assertEquals(64, legacy.getWidth());
        assertEquals(32, legacy.getHeight());
        assertEquals(0xFFAABBCC, legacy.getRGB(8, 8));
        assertEquals(0xFF123456, legacy.getRGB(20, 20));
    }

    @Test
    public void findPlayerMatchesByCommandSenderName() throws Exception {
        World world = mock(World.class);
        EntityPlayer notch = mock(EntityPlayer.class);
        when(notch.getCommandSenderName()).thenReturn("Notch");
        EntityPlayer steve = mock(EntityPlayer.class);
        when(steve.getCommandSenderName()).thenReturn("Steve");
        setField(world, "playerEntities", Arrays.asList(notch, steve));

        assertSame(notch, ClientSkinApplier.findPlayer(world, "Notch"));
        assertSame(steve, ClientSkinApplier.findPlayer(world, "Steve"));
    }

    @Test
    public void findPlayerReturnsNullForUnknownOrEmptyWorld() throws Exception {
        World world = mock(World.class);
        setField(world, "playerEntities", Collections.emptyList());
        assertNull(ClientSkinApplier.findPlayer(world, "Notch"));
        assertNull(ClientSkinApplier.findPlayer(null, "Notch"));
    }

    @Test
    public void applyInjectsImageIntoThreadDownloadImageData() throws Exception {
        // Real TDI from the deobf'd client jar (no GL involved in the setter).
        ThreadDownloadImageData tdi = new ThreadDownloadImageData(
            "https://example.invalid/skin.png",
            new ResourceLocation("minecraft", "skins/x"),
            new ImageBufferDownload());
        BufferedImage image = solid(64, 32, 0xFF010203);

        ClientSkinApplier.apply(tdi, image);

        // MCP 8.11: the public setter is getBufferedImage(BufferedImage); the
        // backing field is private on this line — read it back via reflection.
        Field field = ThreadDownloadImageData.class.getDeclaredField("bufferedImage");
        field.setAccessible(true);
        assertSame(image, field.get(tdi));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static BufferedImage solid(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    private static byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bos);
        return bos.toByteArray();
    }
}
