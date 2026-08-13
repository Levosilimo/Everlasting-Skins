/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import org.junit.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure result-document tests for the in-jar E2E driver (no Minecraft/FML on
 * the classpath).
 */
public class E2EResultTest {

    @Test
    public void writeProducesContractJson() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "e2e-result-test-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        try {
            File out = new File(dir, "e2e-result.json");
            Map<String, String> artifacts = new LinkedHashMap<String, String>();
            artifacts.put("broadcast", "received");
            E2EResult.write(out, true, true, true, true, 12345L, 0, artifacts);

            String json = new String(java.nio.file.Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"lane\":\"1.4.7\""));
            assertTrue(json.contains("\"server_booted\":false"));
            assertTrue(json.contains("\"client_joined\":true"));
            assertTrue(json.contains("\"command_executed\":true"));
            assertTrue(json.contains("\"renderer_state\":\"sentinel\""));
            assertTrue(json.contains("\"renderer_verified\":true"));
            assertTrue(json.contains("\"duration_ms\":12345"));
            assertTrue(json.contains("\"exit_code\":0"));
            assertTrue(json.contains("\"broadcast\":\"received\""));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void writeFailureState() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "e2e-result-fail-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        try {
            File out = new File(dir, "e2e-result.json");
            E2EResult.write(out, false, false, false, false, 999L, 2, null);
            String json = new String(java.nio.file.Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"client_joined\":false"));
            assertTrue(json.contains("\"renderer_state\":\"none\""));
            assertTrue(json.contains("\"exit_code\":2"));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void writeObserverProducesAdditiveContractJson() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "e2e-result-observer-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        try {
            File out = new File(dir, "e2e-result.json");
            Map<String, String> artifacts = new LinkedHashMap<String, String>();
            artifacts.put("driver", "E2EObserverDriver/1.4.7");
            artifacts.put("broadcast", "received");
            artifacts.put("wire_png_matches_sentinel", "true");
            E2EResult.writeObserver(out, true, "sentinel", true, 54321L, 0, artifacts);

            String json = new String(java.nio.file.Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"lane\":\"1.4.7\""));
            assertTrue(json.contains("\"server_booted\":false"));
            assertTrue(json.contains("\"observer_joined\":true"));
            assertTrue(json.contains("\"observer_renderer_state\":\"sentinel\""));
            assertTrue(json.contains("\"observer_renderer_verified\":true"));
            assertTrue(json.contains("\"observer_duration_ms\":54321"));
            assertTrue(json.contains("\"observer_exit_code\":0"));
            assertTrue(json.contains("\"wire_png_matches_sentinel\":\"true\""));
            // The observer doc must NOT carry actor fields (additive contract).
            assertFalse(json.contains("client_joined"));
            assertFalse(json.contains("command_executed"));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void writeObserverFailureState() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "e2e-result-observer-fail-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        try {
            File out = new File(dir, "e2e-result.json");
            E2EResult.writeObserver(out, false, "handler-injection-missing", false, 999L, 1, null);
            String json = new String(java.nio.file.Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"observer_joined\":false"));
            assertTrue(json.contains("\"observer_renderer_state\":\"handler-injection-missing\""));
            assertTrue(json.contains("\"observer_renderer_verified\":false"));
            assertTrue(json.contains("\"observer_exit_code\":1"));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void jsonEscapesQuotesAndBackslashes() {
        Map<String, Object> doc = new LinkedHashMap<String, Object>();
        doc.put("note", "a \"quoted\" \\ path");
        String json = E2EResult.toJson(doc);
        assertTrue(json.contains("\"note\":\"a \\\"quoted\\\" \\\\ path\""));
    }

    @Test
    public void sentinelImageContract() {
        assertTrue(E2EResult.isSentinelImage(sentinel()));
        assertFalse(E2EResult.isSentinelImage(null));
        assertFalse(E2EResult.isSentinelImage(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB)));
        BufferedImage wrong = sentinel();
        wrong.setRGB(63, 31, 0xFF0000FF); // corner not green
        assertFalse(E2EResult.isSentinelImage(wrong));
        BufferedImage wrongBlock = sentinel();
        wrongBlock.setRGB(0, 0, 0xFF00FF00); // block pixel not red
        assertFalse(E2EResult.isSentinelImage(wrongBlock));
    }

    @Test
    public void canonicalSentinelPngMeetsContract() throws Exception {
        // The exact bytes the e2e scripts copy into the server + client
        // game dirs (common/src/test/resources/e2e/sentinel-64x32.png).
        java.io.InputStream in = getClass().getResourceAsStream("/e2e/sentinel-64x32.png");
        assertNotNull("sentinel PNG must be on the test classpath", in);
        BufferedImage img;
        try {
            img = javax.imageio.ImageIO.read(in);
        } finally {
            in.close();
        }
        assertNotNull(img);
        assertEquals(64, img.getWidth());
        assertEquals(32, img.getHeight());
        assertTrue(E2EResult.isSentinelImage(img));
    }

    @Test
    public void pixelsEqualAcceptsIdenticalImages() {
        assertTrue(E2EResult.pixelsEqual(sentinel(), sentinel()));
        // A second decode of the same pixels must compare equal.
        BufferedImage copy = new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                copy.setRGB(x, y, (x < 8 && y < 8) ? E2EResult.RED : E2EResult.GREEN);
            }
        }
        assertTrue(E2EResult.pixelsEqual(sentinel(), copy));
    }

    @Test
    public void pixelsEqualRejectsAnyDrift() {
        BufferedImage drift = sentinel();
        drift.setRGB(63, 31, 0xFF0000FF);
        assertFalse(E2EResult.pixelsEqual(sentinel(), drift));
        assertFalse(E2EResult.pixelsEqual(sentinel(), null));
        assertFalse(E2EResult.pixelsEqual(null, sentinel()));
        assertTrue(E2EResult.pixelsEqual(null, null));
        assertFalse(E2EResult.pixelsEqual(sentinel(), new BufferedImage(64, 31, BufferedImage.TYPE_INT_ARGB)));
    }

    private static BufferedImage sentinel() {
        BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                int color = (x < 8 && y < 8) ? E2EResult.RED : E2EResult.GREEN;
                img.setRGB(x, y, color);
            }
        }
        return img;
    }

    private static void deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }
}
