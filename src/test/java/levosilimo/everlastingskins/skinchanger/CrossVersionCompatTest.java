/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.google.gson.Gson;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-version schema compatibility: pins that a record written by the
 * other Minecraft version's SkinIO reads correctly under the local parser.
 * <p>
 * The 1.21 schema adds a {@code username} member and (Round 6 Tier 2 CRC)
 * an in-band {@code checksum} envelope. The 1.12.2 schema is
 * {@code source} + {@code originalProperty} without a marker. Gson ignores
 * unknown members on read, so a 1.21 record must parse under the
 * 1.12.2-shaped schema and a 1.12.2-shaped record must parse under the
 * 1.21 schema: {@code username} absent → null, marker absent → legacy load
 * with a warning, never a quarantine.
 */
class CrossVersionCompatTest {

    @TempDir
    Path tempDir;

    private SkinIO skinIO;

    @BeforeEach
    void setUp() {
        skinIO = new SkinIO(tempDir);
    }

    /* ================================================================== */
    /*  Fixtures                                                           */
    /* ================================================================== */

    private static final String USERNAME = "Notch";
    private static final String SOURCE = "MojangAPI";
    private static final String PROPERTY_NAME = "textures";
    private static final String PROPERTY_SIGNATURE = "sig-cross-version";
    private static final String PROPERTY_VALUE = Base64.getEncoder()
            .encodeToString("cross-version-value".getBytes(StandardCharsets.UTF_8));

    /** 1.12.2-only schema: {@code source} + {@code originalProperty}, no username, no checksum. */
    private static final String SKIN_1122_SHAPE = "{"
            + "\"source\":\"" + SOURCE + "\","
            + "\"originalProperty\":{"
            + "\"name\":\"" + PROPERTY_NAME + "\","
            + "\"value\":\"" + PROPERTY_VALUE + "\","
            + "\"signature\":\"" + PROPERTY_SIGNATURE + "\""
            + "}}";

    /** 1.21-only schema: adds {@code username} and a valid in-band checksum envelope. */
    private static final String SKIN_121_SHAPE = withChecksum("{"
            + "\"username\":\"" + USERNAME + "\","
            + "\"source\":\"" + SOURCE + "\","
            + "\"originalProperty\":{"
            + "\"name\":\"" + PROPERTY_NAME + "\","
            + "\"value\":\"" + PROPERTY_VALUE + "\","
            + "\"signature\":\"" + PROPERTY_SIGNATURE + "\""
            + "}}");

    /* ================================================================== */
    /*  Tests                                                              */
    /* ================================================================== */

    @Test
    @DisplayName("1.12.2-shaped record (no username, no checksum) reads with username null")
    void readSkinFile_accepts1122SchemaRecord() throws IOException {
        UUID uuid = UUID.randomUUID();
        Files.write(tempDir.resolve(uuid + ".json"), SKIN_1122_SHAPE.getBytes(StandardCharsets.UTF_8));

        CustomSkinProperty loaded = skinIO.loadSkin(uuid);

        assertNotNull(loaded, "a 1.12.2-shaped record must load");
        assertEquals(SOURCE, loaded.getSource());
        assertEquals(PROPERTY_NAME, loaded.getOriginalProperty().getName());
        assertEquals(PROPERTY_VALUE, loaded.getOriginalProperty().getValue());
        assertEquals(PROPERTY_SIGNATURE, loaded.getOriginalProperty().getSignature());
        assertNull(loaded.getUsername(), "an absent username member must deserialize to null");
        assertTrue(Files.exists(tempDir.resolve(uuid + ".json")),
                "a markerless record must not be quarantined for lacking a checksum");
    }

    @Test
    @DisplayName("markerless cross-version record loads and the sweep classifies it as legacy, not corrupt")
    void readSkinFile_acceptsMarkerlessLegacyRecord() throws IOException {
        // A pre-Round-6 1.21 file: 1.21 schema without the checksum envelope.
        JsonObject record = new JsonParser().parse(SKIN_121_SHAPE).getAsJsonObject();
        record.remove("checksum");
        UUID uuid = UUID.randomUUID();
        Files.write(tempDir.resolve(uuid + ".json"), JsonUtils.toJson(record).getBytes(StandardCharsets.UTF_8));

        CustomSkinProperty loaded = skinIO.loadSkin(uuid);
        assertNotNull(loaded, "a markerless legacy record must still load");
        assertEquals(SOURCE, loaded.getSource());

        SkinIO.SweepResult sweep = skinIO.validateAllFiles();
        assertEquals(1, sweep.filesChecked, "sweep must check the legacy record");
        assertEquals(0, sweep.corruptFound, "a missing checksum is legacy format, not corruption");
        assertEquals(1, sweep.legacyFound, "sweep must classify the markerless record as legacy");
        assertTrue(Files.exists(tempDir.resolve(uuid + ".json")), "legacy record must survive the sweep");
    }

    @Test
    @DisplayName("1.21-shaped record (username + checksum) reads with fields preserved and checksum verified")
    void readSkinFile_accepts121SchemaRecord() throws IOException {
        UUID uuid = UUID.randomUUID();
        String checksum = new JsonParser().parse(SKIN_121_SHAPE).getAsJsonObject().get("checksum").getAsString();
        assertTrue(checksum.matches("[0-9a-f]{64}"), "fixture checksum must be a 64-char hex SHA-256");
        Files.write(tempDir.resolve(uuid + ".json"), SKIN_121_SHAPE.getBytes(StandardCharsets.UTF_8));

        CustomSkinProperty loaded = skinIO.loadSkin(uuid);

        // A mismatched marker would be quarantined and loadSkin would return
        // null: a successful load proves the 1.21 writer's checksum verified.
        assertNotNull(loaded, "a 1.21-shaped record must load with its checksum verified");
        assertEquals(SOURCE, loaded.getSource());
        assertEquals(PROPERTY_NAME, loaded.getOriginalProperty().getName());
        assertEquals(PROPERTY_VALUE, loaded.getOriginalProperty().getValue());
        assertEquals(PROPERTY_SIGNATURE, loaded.getOriginalProperty().getSignature());
        assertEquals(USERNAME, loaded.getUsername(), "the username member from a 1.21 record must be preserved");
        assertTrue(Files.exists(tempDir.resolve(uuid + ".json")), "a verified record must not be quarantined");
    }

    @Test
    @DisplayName("1.12.2-written record parses under a 1.21-shaped schema (checksum envelope ignored)")
    void writeSkinFile_writesInteroperableFormat() throws IOException {
        UUID uuid = UUID.randomUUID();
        skinIO.saveSkin(uuid, new CustomSkinProperty(PROPERTY_NAME, PROPERTY_VALUE, PROPERTY_SIGNATURE, SOURCE, USERNAME));

        Path target = tempDir.resolve(uuid + ".json");
        String onDisk = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);

        // Simulate the 1.21 parser: a schema with username + source +
        // originalProperty but no checksum member (the marker is SkinIO's
        // envelope, not part of the model). Plain Gson ignores the unknown
        // checksum member, so the 1.12.2 record must map cleanly.
        Skin121Dto parsed = new Gson().fromJson(onDisk, Skin121Dto.class);
        assertEquals(SOURCE, parsed.source);
        assertEquals(USERNAME, parsed.username);
        assertNotNull(parsed.originalProperty, "originalProperty must deserialize");
        assertEquals(PROPERTY_NAME, parsed.originalProperty.name);
        assertEquals(PROPERTY_VALUE, parsed.originalProperty.value);
        assertEquals(PROPERTY_SIGNATURE, parsed.originalProperty.signature);
    }

    /* ================================================================== */
    /*  Helpers                                                            */
    /* ================================================================== */

    /** Appends a valid in-band SHA-256 marker, mirroring {@link SkinIO}'s write envelope. */
    private static String withChecksum(String record) {
        JsonObject obj = new JsonParser().parse(record).getAsJsonObject();
        obj.remove("checksum");
        String canonical = JsonUtils.toJson(obj);
        obj.addProperty("checksum", sha256Hex(canonical.getBytes(StandardCharsets.UTF_8)));
        return JsonUtils.toJson(obj);
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    /** 1.21-shaped schema: source + username + originalProperty, no checksum member. */
    private static final class Skin121Dto {
        String source;
        String username;
        PropertyDto originalProperty;
    }

    private static final class PropertyDto {
        String name;
        String value;
        String signature;
    }
}
