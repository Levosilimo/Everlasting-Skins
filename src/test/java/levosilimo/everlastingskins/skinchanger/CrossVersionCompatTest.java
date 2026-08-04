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
        Files.writeString(tempDir.resolve(uuid + ".json"), SKIN_1122_SHAPE, StandardCharsets.UTF_8);

        CustomSkinProperty loaded = skinIO.loadSkin(uuid);

        assertNotNull(loaded, "a 1.12.2-shaped record must load");
        assertEquals(SOURCE, loaded.getSource());
        assertEquals(PROPERTY_NAME, loaded.getOriginalProperty().name());
        assertEquals(PROPERTY_VALUE, loaded.getOriginalProperty().value());
        assertEquals(PROPERTY_SIGNATURE, loaded.getOriginalProperty().signature());
        assertNull(loaded.getUsername(), "an absent username member must deserialize to null");
        assertTrue(Files.exists(tempDir.resolve(uuid + ".json")),
                "a markerless record must not be quarantined for lacking a checksum");
    }

    @Test
    @DisplayName("markerless cross-version record loads and the sweep classifies it as legacy, not corrupt")
    void readSkinFile_acceptsMarkerlessLegacyRecord() throws IOException {
        UUID uuid = UUID.randomUUID();
        Files.writeString(tempDir.resolve(uuid + ".json"), SKIN_1122_SHAPE, StandardCharsets.UTF_8);

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
    @DisplayName("1.21-written record parses under a 1.12.2-shaped schema (unknown members ignored)")
    void writeSkinFile_writesInteroperableFormat() throws IOException {
        UUID uuid = UUID.randomUUID();
        skinIO.saveSkin(uuid, new CustomSkinProperty(PROPERTY_NAME, PROPERTY_VALUE, PROPERTY_SIGNATURE, SOURCE, USERNAME));

        Path target = tempDir.resolve(uuid + ".json");
        String onDisk = Files.readString(target, StandardCharsets.UTF_8);
        JsonObject envelope = JsonParser.parseString(onDisk).getAsJsonObject();
        assertEquals(USERNAME, envelope.get("username").getAsString(), "1.21 format must persist username");
        assertTrue(envelope.has("checksum"), "1.21 format must carry the in-band checksum envelope");

        // Simulate the 1.12.2 parser: a schema without username or checksum
        // members. Plain Gson ignores unknown members, so the 1.21 record
        // must map cleanly onto the 1.12.2 shape.
        Skin1122Dto parsed = new Gson().fromJson(onDisk, Skin1122Dto.class);
        assertEquals(SOURCE, parsed.source);
        assertNotNull(parsed.originalProperty, "originalProperty must deserialize");
        assertEquals(PROPERTY_NAME, parsed.originalProperty.name);
        assertEquals(PROPERTY_VALUE, parsed.originalProperty.value);
        assertEquals(PROPERTY_SIGNATURE, parsed.originalProperty.signature);

        CustomSkinProperty loaded = skinIO.loadSkin(uuid);
        assertNotNull(loaded, "the written record must load back with its checksum verified");
        assertEquals(USERNAME, loaded.getUsername());
    }

    /* ================================================================== */
    /*  Helpers                                                            */
    /* ================================================================== */

    /** Appends a valid in-band SHA-256 marker, mirroring {@link SkinIO}'s write envelope. */
    private static String withChecksum(String record) {
        JsonObject obj = JsonParser.parseString(record).getAsJsonObject();
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

    /** 1.12.2-shaped schema: source + originalProperty only (no username, no checksum). */
    private static final class Skin1122Dto {
        String source;
        PropertyDto originalProperty;
    }

    private static final class PropertyDto {
        String name;
        String value;
        String signature;
    }
}
