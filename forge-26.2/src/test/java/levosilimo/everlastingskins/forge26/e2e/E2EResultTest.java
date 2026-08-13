/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge26.e2e;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure result-document tests for the in-jar E2E driver (no Minecraft/Forge
 * on the classpath) — 26.x port.
 */
class E2EResultTest {

    @Test
    void writeProducesContractJson() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "e2e-result-test-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        try {
            File out = new File(dir, E2EResult.FILE_NAME);
            Map<String, String> artifacts = new LinkedHashMap<String, String>();
            artifacts.put("driver", "E2EDriver/26.2");
            artifacts.put("property", "eyJ0ZXh0dXJlcyI6e319");
            E2EResult.write(out, true, true, true, true, 12345L, 0, artifacts);

            String json = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"lane\":\"26.2\""));
            assertTrue(json.contains("\"client_joined\":true"));
            assertTrue(json.contains("\"command_executed\":true"));
            assertTrue(json.contains("\"renderer_state\":\"sentinel\""));
            assertTrue(json.contains("\"renderer_verified\":true"));
            assertTrue(json.contains("\"duration_ms\":12345"));
            assertTrue(json.contains("\"exit_code\":0"));
            assertTrue(json.contains("\"server_booted\":false")); // script merges
            assertTrue(json.contains("\"driver\":\"E2EDriver/26.2\""));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    void failureDocCarriesTheContractFields() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "e2e-result-test-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        try {
            File out = new File(dir, E2EResult.FILE_NAME);
            E2EResult.write(out, true, true, false, false, 90000L, 1, null);
            String json = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"renderer_state\":\"none\""));
            assertTrue(json.contains("\"renderer_verified\":false"));
            assertTrue(json.contains("\"exit_code\":1"));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    void toJsonEscapesQuotesAndBackslashes() {
        Map<String, Object> doc = new LinkedHashMap<String, Object>();
        doc.put("artifacts", new LinkedHashMap<String, String>() {{
            put("property", "a\"b\\c");
        }});
        String json = E2EResult.toJson(doc);
        assertTrue(json.contains("\"property\":\"a\\\"b\\\\c\""));
    }

    @Test
    void markerSurvivesTheDocument() {
        Map<String, Object> doc = new LinkedHashMap<String, Object>();
        doc.put("artifacts", new LinkedHashMap<String, String>() {{
            put("property", E2E.MARKER);
        }});
        String json = E2EResult.toJson(doc);
        assertTrue(json.contains(E2E.MARKER));
        assertFalse(json.contains("\n"));
        assertNotNull(json);
    }

    private static void deleteRecursively(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteRecursively(child);
                } else {
                    child.delete();
                }
            }
        }
        dir.delete();
    }
}
