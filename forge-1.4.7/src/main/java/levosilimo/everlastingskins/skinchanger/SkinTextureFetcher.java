/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.client.ClientSkinApplier;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.PropertyUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Server-side skin texture fetch for the joint client broadcast (lib-5,
 * PR #422): the server stores only the textures property (base64 JSON with a
 * URL), so the PNG bytes must be fetched over HTTP before they can ride in
 * the {@code SkinMessage} payload.
 *
 * <p>The :common {@code HttpClient} is JSON/String-body-only and frozen
 * (shared by every lane), so this lane-local fetch is byte-returning and
 * HTTPS-only, mirroring the :common client's policy including its
 * {@code everlastingskins.allowHttp} test escape hatch. The 64x64 modern skin
 * is flattened to the pre-1.7.8 64x32 model via
 * {@link ClientSkinApplier#flattenToLegacy} so every client payload is
 * already in the era renderer's native layout.
 *
 * <p>Fail-soft: any fetch/decode/encode failure returns null (the broadcast
 * degrades to the legacy notification-only payload rather than crashing the
 * command/login path).
 *
 * <p>Cape path ({@link #fetchLegacyCapePng}): the textures JSON carries the
 * cape under {@code CAPE.url} (exposed via
 * {@link PropertyUtils#getCapeTextureUrl}); modern capes are 64x64 canvases
 * whose art lives in the top 64x32 rows, so the fetched cape is cropped to
 * the top 64x32 region before broadcast (pre-1.8 renderCloak UVs). Cape-less
 * properties yield null (no cape field on the wire).
 */
public final class SkinTextureFetcher {

    private static final String USER_AGENT = "EverlastingSkins/1.4.7";
    private static final int TIMEOUT_MS = 10000;

    private SkinTextureFetcher() {}

    /**
     * Fetches the skin PNG from the property's texture URL and returns it
     * flattened to the legacy 64x32 model, re-encoded as PNG. Null when the
     * fetch/decode fails or the property has no skin URL.
     */
    public static byte[] fetchLegacyPng(CustomSkinProperty skin) {
        try {
            byte[] png = fetchBytes(PropertyUtils.getSkinTextureUrl(skin));
            if (png == null) {
                return null;
            }
            BufferedImage image = ClientSkinApplier.decode(png);
            BufferedImage legacy = ClientSkinApplier.flattenToLegacy(image);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(legacy, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            System.err.println("EverlastingSkins: skin texture fetch failed: " + e);
            return null;
        }
    }

    /**
     * Fetches the cape PNG from the property's {@code CAPE.url} and returns it
     * cropped to the legacy 64x32 model (top 64x32 rows of the modern 64x64
     * canvas), re-encoded as PNG. Null when the property has no cape or the
     * fetch/decode fails.
     */
    public static byte[] fetchLegacyCapePng(CustomSkinProperty skin) {
        try {
            String capeUrl = PropertyUtils.getCapeTextureUrl(skin);
            if (capeUrl == null) {
                return null;
            }
            byte[] png = fetchBytes(capeUrl);
            if (png == null) {
                return null;
            }
            BufferedImage image = ClientSkinApplier.decode(png);
            BufferedImage legacy = ClientSkinApplier.cropCapeToLegacy(image);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(legacy, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            System.err.println("EverlastingSkins: cape texture fetch failed: " + e);
            return null;
        }
    }

    private static byte[] fetchBytes(String urlString) throws IOException {
        URL url = new URL(urlString);
        // textures.minecraft.net is https; mirror the :common HttpClient's
        // https-only policy with the same controlled-testing escape hatch.
        if (!"https".equals(url.getProtocol()) && !Boolean.getBoolean("everlastingskins.allowHttp")) {
            throw new IOException("Only HTTPS is supported: " + urlString);
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " for " + urlString);
            }
            InputStream in = connection.getInputStream();
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                return bos.toByteArray();
            } finally {
                in.close();
            }
        } finally {
            connection.disconnect();
        }
    }
}
