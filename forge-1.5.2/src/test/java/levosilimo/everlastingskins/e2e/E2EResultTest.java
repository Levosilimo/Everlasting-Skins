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
            artifacts.put("broadcast_injected", "true");
            E2EResult.write(out, true, true, true, true, 12345L, 0, artifacts);

            String json = new String(java.nio.file.Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"lane\":\"1.5.2\""));
            assertTrue(json.contains("\"server_booted\":false"));
            assertTrue(json.contains("\"client_joined\":true"));
            assertTrue(json.contains("\"command_executed\":true"));
            assertTrue(json.contains("\"renderer_state\":\"sentinel\""));
            assertTrue(json.contains("\"renderer_verified\":true"));
            assertTrue(json.contains("\"duration_ms\":12345"));
            assertTrue(json.contains("\"exit_code\":0"));
            assertTrue(json.contains("\"broadcast\":\"received\""));
            assertTrue(json.contains("\"broadcast_injected\":\"true\""));
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
    public void sentinelPixelContract() {
        BufferedImage sentinel = new BufferedImage(
            E2EResult.SENTINEL_WIDTH, E2EResult.SENTINEL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < E2EResult.SENTINEL_HEIGHT; y++) {
            for (int x = 0; x < E2EResult.SENTINEL_WIDTH; x++) {
                sentinel.setRGB(x, y,
                    (x < E2EResult.SENTINEL_BLOCK && y < E2EResult.SENTINEL_BLOCK)
                        ? E2EResult.RED : E2EResult.GREEN);
            }
        }
        assertTrue(E2EResult.isSentinelImage(sentinel));
        assertFalse(E2EResult.isSentinelImage(null));
        sentinel.setRGB(0, 0, 0xFF00FF00);
        assertFalse(E2EResult.isSentinelImage(sentinel));
        BufferedImage wrongSize = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        assertFalse(E2EResult.isSentinelImage(wrongSize));
    }

    @Test
    public void toJsonEscapesStrings() {
        Map<String, Object> doc = new LinkedHashMap<String, Object>();
        doc.put("lane", "1.5.2");
        doc.put("artifacts", new LinkedHashMap<String, String>());
        String json = E2EResult.toJson(doc);
        assertNotNull(json);
        assertTrue(json.contains("\"lane\":\"1.5.2\""));
        assertTrue(json.contains("\"artifacts\":{}"));
    }

    private static void deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteRecursively(c);
                }
            }
        }
        f.delete();
    }
}
