/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.client;

import levosilimo.everlastingskins.skinchanger.TextureDecoder;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ThreadDownloadImageData;
import net.minecraft.src.World;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Pure-Java client-side skin application for the 1.5.2 lane (lib-5 Option B:
 * the joint jar carries a client handler that makes skins ACTUALLY RENDER on
 * pre-1.8 clients). Decode / flatten / apply are separated from the network
 * and renderer bindings so they are unit-testable without a client.
 *
 * <p>Flatten contract: the 1.5.2 renderer uses the legacy 64x32 model whose
 * left limbs MIRROR the right-limb regions (ModelBiped samples both arms from
 * textureOffset(40,16) and both legs from (0,16) — spike-verified against the
 * vanilla client jar). The correct 64x64 → 64x32 conversion is therefore the
 * vanilla crop (1.5.2 {@code ImageBufferDownload.parseUserSkin} draws the
 * source unscaled at (0,0)): the modern top half IS the legacy layout, and the
 * modern bottom-left regions (left arm / left leg) are dropped.
 */
public final class ClientSkinApplier {

    private ClientSkinApplier() {}

    /**
     * Decodes PNG bytes into an ARGB {@link BufferedImage}.
     *
     * @throws IOException if the bytes are not a decodable image
     */
    public static BufferedImage decode(byte[] png) throws IOException {
        return TextureDecoder.decode(png);
    }

    /**
     * Converts any skin to the legacy 64x32 model. 64x32 (and smaller-height
     * 64-wide) inputs pass through unchanged; a 64x64 modern skin is cropped
     * to its top half (vanilla 1.5.2 parity); any other size is drawn
     * unscaled at (0,0) exactly like the vanilla download pipeline.
     */
    public static BufferedImage flattenToLegacy(BufferedImage source) {
        if (source.getWidth() == 64 && source.getHeight() <= 32) {
            return source;
        }
        BufferedImage legacy = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics g = legacy.getGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return legacy;
    }

    /**
     * Finds a spawned player by {@code getCommandSenderName()} in the client
     * world ({@code World.playerEntities}), or null when not spawned yet.
     */
    public static EntityPlayer findPlayer(World world, String playerName) {
        if (world == null || world.playerEntities == null) {
            return null;
        }
        for (Object o : world.playerEntities) {
            if (o instanceof EntityPlayer) {
                EntityPlayer candidate = (EntityPlayer) o;
                if (candidate.getCommandSenderName().equals(playerName)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Injects the flattened image into the player's skin
     * {@link ThreadDownloadImageData}. 1.5.2 (MCP 7.51) exposes the image as
     * the PUBLIC field {@code image}; resetting {@code textureSetupComplete}
     * makes {@code RenderEngine.getTextureForDownloadableImage} re-upload the
     * new pixels into the existing GL texture on the next render pass.
     */
    public static void apply(ThreadDownloadImageData target, BufferedImage image) {
        target.image = image;
        target.textureSetupComplete = false;
    }
}
