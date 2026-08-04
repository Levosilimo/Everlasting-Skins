/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-record CRC (Tier 2): the in-band SHA-256 checksum envelope of
 * {@link SkinIO} closes the silent bit-rot gap — a flipped byte inside the
 * base64 value or signature still parses as valid JSON, and the
 * {@code isValidJson} check alone cannot see it. These tests pin the
 * write-side marker, the read-side verification and quarantine, backward
 * compatibility with markerless legacy records, the startup sweep, and the
 * per-load hash cost.
 */
class SkinIOCRCTest {

    @TempDir
    Path tempDir;

    private SkinIO skinIO;

    @BeforeEach
    void setUp() {
        skinIO = new SkinIO(tempDir);
    }

    @Test
    @DisplayName("saveSkin writes an in-band SHA-256 checksum member")
    void writeSkinFile_includesChecksum() throws IOException {
        UUID uuid = UUID.randomUUID();
        skinIO.saveSkin(uuid, skin("val"));

        Path target = tempDir.resolve(uuid + ".json");
        assertTrue(Files.exists(target), "target file must exist after save");
        JsonObject envelope = JsonParser.parseString(Files.readString(target, StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(envelope.has("checksum"), "persisted record must carry a checksum member");
        String checksum = envelope.get("checksum").getAsString();
        assertEquals(64, checksum.length(), "SHA-256 hex digest must be 64 chars");
        assertTrue(checksum.matches("[0-9a-f]{64}"), "checksum must be lowercase hex: " + checksum);
    }

    @Test
    @DisplayName("valid record with matching checksum loads successfully")
    void readSkinFile_verifiesChecksum_pass() {
        UUID uuid = UUID.randomUUID();
        CustomSkinProperty original = skin("value1");
        skinIO.saveSkin(uuid, original);

        CustomSkinProperty loaded = skinIO.loadSkin(uuid);
        assertNotNull(loaded, "a record whose checksum matches must load");
        assertEquals("value1", loaded.getOriginalProperty().value());
        assertEquals("sig1", loaded.getOriginalProperty().signature());
    }

    @Test
    @DisplayName("flipped byte in the base64 value → checksum mismatch, quarantined, null returned")
    void readSkinFile_verifiesChecksum_fail_quarantines() throws IOException {
        UUID uuid = UUID.randomUUID();
        skinIO.saveSkin(uuid, skin(base64("flip-me")));

        // Simulate bit rot: flip one base64 alphabet char in the value. The
        // result is still valid JSON AND still decodes as base64, so the old
        // isValidJson-only path would have silently loaded it — only the
        // in-band checksum can see this corruption.
        Path target = tempDir.resolve(uuid + ".json");
        JsonObject envelope = JsonParser.parseString(Files.readString(target, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject record = envelope.getAsJsonObject("originalProperty");
        String value = record.get("value").getAsString();
        char flipped = value.charAt(0) == 'Z' ? 'Y' : 'Z';
        record.addProperty("value", flipped + value.substring(1));
        Files.writeString(target, JsonUtils.toJson(envelope), StandardCharsets.UTF_8);

        CustomSkinProperty loaded = skinIO.loadSkin(uuid);
        assertNull(loaded, "a record with a flipped byte must not load");
        assertFalse(Files.exists(target), "corrupt record must be moved out of the storage path");
        assertCorruptFileExists(uuid);
    }

    @Test
    @DisplayName("record without checksum (legacy format) loads with a warning, not quarantined")
    void readSkinFile_noChecksum_backwardCompat() throws IOException {
        UUID uuid = UUID.randomUUID();
        // Legacy files were written as bare JsonUtils.toJson(skin) — no marker.
        Files.writeString(tempDir.resolve(uuid + ".json"), JsonUtils.toJson(skin("legacy")), StandardCharsets.UTF_8);

        CustomSkinProperty loaded = skinIO.loadSkin(uuid);
        assertNotNull(loaded, "a markerless legacy record must still load");
        assertEquals("legacy", loaded.getOriginalProperty().value());
        assertTrue(Files.exists(tempDir.resolve(uuid + ".json")),
                "a legacy record must never be quarantined for lacking a checksum");
    }

    @Test
    @DisplayName("startup sweep quarantines exactly the corrupt records")
    void validateAllFiles_sweepsCorrupt() throws IOException {
        List<UUID> valid = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID u = UUID.randomUUID();
            skinIO.saveSkin(u, skin("ok-" + i));
            valid.add(u);
        }
        UUID corrupt = UUID.randomUUID();
        skinIO.saveSkin(corrupt, skin(base64("rot")));
        Path corruptTarget = tempDir.resolve(corrupt + ".json");
        JsonObject envelope = JsonParser.parseString(Files.readString(corruptTarget, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject record = envelope.getAsJsonObject("originalProperty");
        String value = record.get("value").getAsString();
        char flipped = value.charAt(0) == 'Z' ? 'Y' : 'Z';
        record.addProperty("value", flipped + value.substring(1));
        Files.writeString(corruptTarget, JsonUtils.toJson(envelope), StandardCharsets.UTF_8);

        SkinIO.SweepResult result = skinIO.validateAllFiles();

        assertEquals(4, result.filesChecked, "sweep must check all four records");
        assertEquals(1, result.corruptFound, "sweep must find the one bit-rotten record");
        assertEquals(0, result.legacyFound, "all four records carry markers");
        assertFalse(Files.exists(corruptTarget), "corrupt record must be quarantined");
        assertCorruptFileExists(corrupt);
        for (UUID u : valid) {
            assertTrue(Files.exists(tempDir.resolve(u + ".json")), "valid record must survive the sweep");
        }
    }

    @Test
    @DisplayName("1000 load cycles with verification stay under 100ms")
    void crcPerformanceOverhead_acceptable() {
        int count = 1000;
        List<UUID> uuids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID u = UUID.randomUUID();
            skinIO.saveSkin(u, skin("perf-" + i));
            uuids.add(u);
        }

        // Timed region: the full read + verify + parse load cycle only; the
        // disk-write setup is intentionally outside the budget.
        long start = System.nanoTime();
        for (UUID u : uuids) {
            assertNotNull(skinIO.loadSkin(u), "every record must load during the perf probe");
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 100,
                count + " load+verify cycles took " + elapsedMs + "ms, expected < 100ms");
    }

    /* ================================================================== */
    /*  Helpers                                                            */
    /* ================================================================== */

    private static CustomSkinProperty skin(String value) {
        return new CustomSkinProperty(value, "sig1", "crc-test");
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private void assertCorruptFileExists(UUID uuid) {
        try (var files = Files.list(tempDir)) {
            boolean found = files.anyMatch(p -> p.getFileName().toString().startsWith(uuid + ".json.corrupt-"));
            assertTrue(found, "Expected a .corrupt-* quarantine file");
        } catch (IOException e) {
            fail("Failed to list temp dir for corrupt file check", e);
        }
    }
}
