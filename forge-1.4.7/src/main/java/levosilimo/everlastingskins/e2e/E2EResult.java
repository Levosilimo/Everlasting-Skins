/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure result-document logic for the real-client E2E (shared master-plan
 * contract, scripts/e2e/). Kept free of any Minecraft/FML import so the
 * lane's JUnit scaffold can test it without a client.
 *
 * <p>The in-jar driver writes {@code e2e-result.json} into the client's
 * gameDir with the client-known fields; {@code scripts/e2e/e2e-common.sh}
 * merges the server-side facts (server_booted, artifacts) into the final
 * document at {@code ${RUNNER_TMP}/e2e-result.json} and maps the exit code.
 */
public final class E2EResult {

    /** Result file name in the client gameDir (driver-side artifact). */
    public static final String FILE_NAME = "e2e-result.json";

    /**
     * Sentinel PNG contract (64x32, shared from :common test resources as
     * {@code e2e/sentinel-64x32.png}): top-left 8x8 block pure red, all
     * remaining pixels pure green. The driver verifies the decoded image
     * before injecting it into the skin renderer.
     */
    public static final int SENTINEL_WIDTH = 64;
    public static final int SENTINEL_HEIGHT = 32;
    public static final int SENTINEL_BLOCK = 8;
    public static final int RED = 0xFFFF0000;
    public static final int GREEN = 0xFF00FF00;

    private E2EResult() {}

    /**
     * Verifies a decoded image against the sentinel pixel contract. The
     * image is a valid sentinel iff every pixel inside the top-left
     * {@link #SENTINEL_BLOCK} square is pure red and every other pixel is
     * pure green.
     */
    public static boolean isSentinelImage(BufferedImage image) {
        if (image == null || image.getWidth() != SENTINEL_WIDTH || image.getHeight() != SENTINEL_HEIGHT) {
            return false;
        }
        for (int y = 0; y < SENTINEL_HEIGHT; y++) {
            for (int x = 0; x < SENTINEL_WIDTH; x++) {
                int expected = (x < SENTINEL_BLOCK && y < SENTINEL_BLOCK) ? RED : GREEN;
                if ((image.getRGB(x, y) & 0xFFFFFF) != (expected & 0xFFFFFF)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Per-pixel ARGB equality between two decoded images (audit lib-20
     * content assert): dimensions must match and every pixel must be
     * identical. Deterministic and cheap (64x32 = 2048 comparisons).
     */
    public static boolean pixelsEqual(BufferedImage a, BufferedImage b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return false;
        }
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Writes the client-side result document. Fields follow the master-plan
     * contract; the driver fills what it observes. Throws on IO failure so
     * the driver can surface it as a hard failure (never a silent pass).
     */
    public static void write(File target, boolean clientJoined, boolean commandExecuted,
                             boolean rendererState, boolean rendererVerified, long durationMs,
                             int exitCode, Map<String, String> artifacts) throws IOException {
        write(target, clientJoined, commandExecuted, rendererState ? "sentinel" : "none",
            rendererVerified, durationMs, exitCode, artifacts);
    }

    /**
     * String renderer_state variant (audit lib-20): the driver reports the
     * precise failure mode, e.g. {@code sentinel-mismatch} when the injected
     * image's pixels differ from the sentinel file, while the boolean
     * overload keeps the legacy {@code sentinel}|{@code none} mapping for
     * existing callers. {@code sentinel} on pass is the only value the
     * orchestrator's assert gate accepts for a green run.
     */
    public static void write(File target, boolean clientJoined, boolean commandExecuted,
                             String rendererState, boolean rendererVerified, long durationMs,
                             int exitCode, Map<String, String> artifacts) throws IOException {
        Map<String, Object> doc = new LinkedHashMap<String, Object>();
        doc.put("lane", "1.4.7");
        doc.put("server_booted", false); // client cannot know; script merges
        doc.put("client_joined", clientJoined);
        doc.put("command_executed", commandExecuted);
        doc.put("renderer_state", rendererState == null ? "none" : rendererState);
        doc.put("renderer_verified", rendererVerified);
        doc.put("duration_ms", durationMs);
        doc.put("exit_code", exitCode);
        doc.put("artifacts", artifacts == null ? new LinkedHashMap<String, String>() : artifacts);
        Files.write(target.toPath(), toJson(doc).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes the OBSERVER client's result document (second-observer client,
     * lib-23 gap (d): the observer runs no commands and proves the broadcast
     * fan-out by asserting the REAL {@code ClientSkinHandler}'s wire
     * injection into the target player's TDI). Fields are additive and
     * {@code observer_}-prefixed so {@code scripts/e2e/e2e-common.sh} can
     * merge them into the actor's document without touching any actor
     * field (backward-compatible result contract).
     */
    public static void writeObserver(File target, boolean observerJoined, String rendererState,
                                     boolean rendererVerified, long durationMs, int exitCode,
                                     Map<String, String> artifacts) throws IOException {
        Map<String, Object> doc = new LinkedHashMap<String, Object>();
        doc.put("lane", "1.4.7");
        doc.put("server_booted", false); // client cannot know; script merges
        doc.put("observer_joined", observerJoined);
        doc.put("observer_renderer_state", rendererState == null ? "none" : rendererState);
        doc.put("observer_renderer_verified", rendererVerified);
        doc.put("observer_duration_ms", durationMs);
        doc.put("observer_exit_code", exitCode);
        doc.put("observer_artifacts", artifacts == null ? new LinkedHashMap<String, String>() : artifacts);
        Files.write(target.toPath(), toJson(doc).getBytes(StandardCharsets.UTF_8));
    }

    /** Minimal JSON emitter (no gson dependency on the legacy classpath is guaranteed at boot). */
    public static String toJson(Map<String, Object> doc) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : doc.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof Boolean || v instanceof Number) {
                sb.append(v);
            } else if (v instanceof Map) {
                sb.append(toJson((Map<String, Object>) v));
            } else {
                sb.append('"').append(String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            }
        }
        return sb.append('}').toString();
    }
}
