/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure result-document logic for the real-client E2E (shared master-plan
 * contract, scripts/e2e/). Kept free of any Minecraft/Forge import so the
 * lane's JUnit scaffold can test it without a client.
 *
 * <p>The in-jar driver writes {@code e2e-result.json} into the client's
 * gameDir with the client-known fields; the lane wrapper
 * ({@code test-infrastructure/run-e2e.sh}) merges the server-side facts
 * (server_booted) into the final document at
 * {@code ${RUNNER_TMP}/e2e-result.json} and maps the exit code.
 */
public final class E2EResult {

    /** Result file name in the client gameDir (driver-side artifact). */
    public static final String FILE_NAME = "e2e-result.json";

    private E2EResult() {}

    /**
     * Writes the client-side result document. Fields follow the master-plan
     * contract; the driver fills what it observes. Throws on IO failure so
     * the driver can surface it as a hard failure (never a silent pass).
     */
    public static void write(File target, boolean clientJoined, boolean commandExecuted,
                             boolean rendererState, boolean rendererVerified, long durationMs,
                             int exitCode, Map<String, String> artifacts) throws IOException {
        Map<String, Object> doc = new LinkedHashMap<String, Object>();
        doc.put("lane", "1.21.4");
        doc.put("server_booted", false); // client cannot know; script merges
        doc.put("client_joined", clientJoined);
        doc.put("command_executed", commandExecuted);
        doc.put("renderer_state", rendererState ? "sentinel" : "none");
        doc.put("renderer_verified", rendererVerified);
        doc.put("duration_ms", durationMs);
        doc.put("exit_code", exitCode);
        doc.put("artifacts", artifacts == null ? new LinkedHashMap<String, String>() : artifacts);
        Files.write(target.toPath(), toJson(doc).getBytes(StandardCharsets.UTF_8));
    }

    /** Minimal JSON emitter (kept dependency-free for parity with the pre-1.8 lanes). */
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
