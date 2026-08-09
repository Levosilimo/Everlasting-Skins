/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Lane-local skin texture decoder (not in :common — ImageIO/AWT is a
 * binding-layer concern; :common stays pure data transport). Decodes PNG
 * skin bytes into an ARGB {@link BufferedImage} so the broadcast pipeline
 * can validate a payload before pushing it to clients.
 */
public final class TextureDecoder {

    private TextureDecoder() {}

    /**
     * Decodes texture bytes into a {@link BufferedImage}.
     *
     * @throws IOException if the bytes are not a decodable image
     */
    public static BufferedImage decode(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IOException("Not a decodable image");
        }
        return image;
    }
}
