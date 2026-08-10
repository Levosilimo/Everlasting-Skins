/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.sun.net.httpserver.HttpServer;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * 1.4.7 server-side skin texture fetch tests (lib-5 joint broadcast): the
 * textures property is fetched over HTTP, decoded and flattened to the legacy
 * 64x32 model. Deterministic fixtures only (memory #1115) — an in-process
 * HttpServer serves a generated PNG on localhost; the lane-local fetch's
 * {@code everlastingskins.allowHttp} escape hatch mirrors the :common
 * client's controlled-testing policy.
 */
public class SkinTextureFetcherTest {

    private HttpServer server;
    private String baseUrl;

    @Before
    public void setUp() throws Exception {
        System.setProperty("everlastingskins.allowHttp", "true");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void tearDown() throws Exception {
        System.clearProperty("everlastingskins.allowHttp");
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void fetchesAndFlattensModern64x64Skin() throws Exception {
        BufferedImage modern = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                modern.setRGB(x, y, 0xFF445566);
            }
        }
        modern.setRGB(10, 10, 0xFFAA0000);
        serve("/skin64.png", png(modern));

        byte[] result = SkinTextureFetcher.fetchLegacyPng(property("textures", baseUrl + "/skin64.png"));

        assertNotNull(result);
        BufferedImage flattened = ImageIO.read(new java.io.ByteArrayInputStream(result));
        assertEquals(64, flattened.getWidth());
        assertEquals(32, flattened.getHeight());
        assertEquals(0xFFAA0000, flattened.getRGB(10, 10));
    }

    @Test
    public void passesLegacy64x32Through() throws Exception {
        serve("/skin32.png", png(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB)));

        byte[] result = SkinTextureFetcher.fetchLegacyPng(property("textures", baseUrl + "/skin32.png"));

        assertNotNull(result);
        BufferedImage flattened = ImageIO.read(new java.io.ByteArrayInputStream(result));
        assertEquals(64, flattened.getWidth());
        assertEquals(32, flattened.getHeight());
    }

    @Test
    public void httpStatusErrorYieldsNull() throws Exception {
        serve("/missing.png", new byte[]{1, 2, 3});
        server.createContext("/missing404", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        byte[] result = SkinTextureFetcher.fetchLegacyPng(property("textures", baseUrl + "/missing404"));

        assertNull(result);
    }

    @Test
    public void httpsOnlyWithoutEscapeHatch() throws Exception {
        System.clearProperty("everlastingskins.allowHttp");
        byte[] result = SkinTextureFetcher.fetchLegacyPng(property("textures", baseUrl + "/skin64.png"));
        assertNull(result);
    }

    @Test
    public void undecodableBodyYieldsNull() throws Exception {
        serve("/garbage.png", new byte[]{0, 1, 2, 3, 4, 5});
        byte[] result = SkinTextureFetcher.fetchLegacyPng(property("textures", baseUrl + "/garbage.png"));
        assertNull(result);
    }

    @Test
    public void fetchesAndCropsModern64x64Cape() throws Exception {
        // Modern capes are 64x64 canvases whose art lives in the top 64x32
        // rows; the pre-1.8 renderCloak UVs need the 64x32 top region.
        BufferedImage modern = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                modern.setRGB(x, y, 0xFF223344);
            }
        }
        modern.setRGB(20, 20, 0xFFBB0000); // top region — kept
        serve("/cape64.png", png(modern));

        byte[] result = SkinTextureFetcher.fetchLegacyCapePng(capeProperty(baseUrl + "/cape64.png"));

        assertNotNull(result);
        BufferedImage cropped = ImageIO.read(new java.io.ByteArrayInputStream(result));
        assertEquals(64, cropped.getWidth());
        assertEquals(32, cropped.getHeight());
        assertEquals(0xFFBB0000, cropped.getRGB(20, 20));
    }

    @Test
    public void capeLessPropertyYieldsNull() throws Exception {
        // A skin property without a CAPE entry must not produce cape bytes
        // (the common case — most skins have no cape).
        assertNull(SkinTextureFetcher.fetchLegacyCapePng(property("textures", baseUrl + "/skin64.png")));
    }

    private void serve(String path, byte[] body) {
        server.createContext(path, exchange -> {
            byte[] payload = body;
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        });
    }

    /** Textures property whose base64 value is the JSON {"textures":{"SKIN":{"url":...}}}. */
    private static CustomSkinProperty property(String name, String url) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        String value = java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new CustomSkinProperty(name, value, null, "test");
    }

    /** Textures property with a CAPE entry (skin + cape URLs). */
    private static CustomSkinProperty capeProperty(String capeUrl) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/s\"},"
            + "\"CAPE\":{\"url\":\"" + capeUrl + "\"}}}";
        String value = java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new CustomSkinProperty("textures", value, null, "test");
    }

    private static byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bos);
        return bos.toByteArray();
    }
}
