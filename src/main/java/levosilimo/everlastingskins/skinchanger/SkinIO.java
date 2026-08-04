/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.JsonUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

public class SkinIO {

    private static final String FILE_EXTENSION = ".json";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String CORRUPT_PREFIX = ".corrupt-";

    /**
     * In-band per-record integrity marker (Tier 2): hex SHA-256 of the
     * canonical record bytes, stored as a JSON member of the record itself.
     * Record and marker therefore move together in one atomic rename, so a
     * crash can never leave a torn record/checksum pair on disk (a sidecar
     * file has an unavoidable two-file window). This mirrors the in-band
     * checksum placement of ARIES (per-log-page checksums) and RocksDB
     * (per-block trailers) and the committed-state framing of Pillai et al.
     * (OSDI 2014): a reader sees either the previous committed record or the
     * new one, never a record whose integrity cannot be checked.
     */
    private static final String CHECKSUM_FIELD = "checksum";

    private final Path savePath;

    public SkinIO(Path savePath) {
        this.savePath = savePath;
    }

    @Nullable
    public String getSourceFromFileStorage(UUID uuid) {
        String skinJson = readSkinFile(uuid);
        if (skinJson == null) return null;
        try {
            JsonObject obj = new JsonParser().parse(skinJson).getAsJsonObject();
            if (obj.has("source") && !obj.get("source").isJsonNull()) {
                return obj.get("source").getAsString();
            }
            return null;
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }

    @Nullable
    public CustomSkinProperty loadSkin(UUID uuid) {
        String skinJson = readSkinFile(uuid);
        if (skinJson == null) return null;
        try {
            CustomSkinProperty skin = JsonUtils.fromJson(skinJson, CustomSkinProperty.class);
            if (skin == null || !skin.isValid()) {
                return null;
            }
            return skin;
        } catch (JsonParseException e) {
            return null;
        }
    }

    /**
     * Deletes the skin file. Failures propagate so the caller can keep the
     * in-memory state in lockstep with the disk instead of dropping the map
     * entry while the file survives (later reload resurrects the skin).
     */
    public void deleteSkin(UUID uuid) throws IOException {
        Path target = savePath.resolve(uuid + FILE_EXTENSION);
        if (Files.deleteIfExists(target)) {
            EverlastingSkins.logger.info("Deleted skin file for {}", uuid);
        }
    }

    public void saveSkin(UUID uuid, CustomSkinProperty skin) {
        saveSkin(uuid, JsonUtils.toJson(skin).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Atomic write of a pre-serialized payload from the SkinStorage async drain.
     */
    void saveSkin(UUID uuid, byte[] payload) {
        Path target = savePath.resolve(uuid + FILE_EXTENSION);
        Path temp = savePath.resolve(uuid + FILE_EXTENSION + TEMP_SUFFIX);

        try {
            Files.createDirectories(savePath);
            Files.deleteIfExists(temp);

            Files.write(temp, withChecksum(payload));

            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }

            moveIntoPlace(uuid, temp, target);
        } catch (IOException e) {
            SkinMetrics.INSTANCE.recordIoFailure(e);
            EverlastingSkins.logger.error("Failed to save skin for player {}", uuid, e);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Wraps a raw record payload in the checksum envelope: strips any stale
     * {@value #CHECKSUM_FIELD} member, hashes the canonical record bytes and
     * appends the hex SHA-256 digest as the {@value #CHECKSUM_FIELD} member.
     * The canonical record is the payload re-serialized through the same Gson
     * used by {@link JsonUtils} (deterministic field order and pretty
     * printing), so the write path and the verify path derive identical
     * bytes: a single flipped byte anywhere in the canonical record changes
     * the digest. This is the tier-2 barrier that closes the silent bit-rot
     * gap — a flipped byte inside the base64 value or signature still parses
     * as valid JSON, and the {@code isValidJson} check alone cannot see it
     * (cf. ARIES page checksums and RocksDB block trailers, which store the
     * integrity marker in-band with the data it protects).
     * <p>
     * Backwards compatibility: a payload that is not a JSON object (e.g.
     * the raw byte payloads written by the durability tests, or any
     * hand-crafted legacy record) is written verbatim without a marker;
     * such files load with a warning, never a quarantine.
     */
    private static byte[] withChecksum(byte[] payload) {
        try {
            JsonObject obj = new JsonParser().parse(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
            obj.remove(CHECKSUM_FIELD);
            String canonical = JsonUtils.toJson(obj);
            obj.addProperty(CHECKSUM_FIELD, sha256Hex(canonical.getBytes(StandardCharsets.UTF_8)));
            return JsonUtils.toJson(obj).getBytes(StandardCharsets.UTF_8);
        } catch (JsonParseException | IllegalStateException e) {
            // Not a JSON object: write verbatim (legacy format, no marker).
            return payload;
        }
    }

    /** Hex SHA-256 of the given bytes. Built-in {@link MessageDigest}: no new dependency. */
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

    /**
     * Renames the fsync'd temp file over the target. Filesystems without
     * atomic renames throw from ATOMIC_MOVE; we then downgrade to a plain
     * move, but surface the loss of crash-consistency instead of swallowing
     * it. Either way the parent directory is fsync'd afterwards: file fsync
     * does not flush the rename's directory entry, so without this the write
     * can vanish on power loss (Pillai OSDI'14).
     */
    private void moveIntoPlace(UUID uuid, Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            SkinMetrics.INSTANCE.recordIoFailure(e);
            EverlastingSkins.logger.warn("Atomic rename unsupported for {}, downgrading to non-atomic move", uuid, e);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        fsyncDirectory();
    }

    /**
     * fsyncs the save directory. Java 8 cannot open directories for READ on
     * every platform (Windows throws AccessDeniedException), so a failure is
     * logged but does not fail the save: the file content is already durable,
     * only the rename entry may not survive a crash.
     */
    private void fsyncDirectory() {
        try (FileChannel dir = openDirectoryChannel(savePath)) {
            dir.force(true);
        } catch (UnsupportedOperationException ignored) {
            // Some filesystems (e.g. FAT) don't support dir fsync; tolerate silently.
        } catch (IOException e) {
            EverlastingSkins.logger.warn("Failed to fsync skin directory {}", savePath, e);
        }
    }

    /**
     * Opens the save directory read-only so it can be fsynced. Package-private
     * so tests can substitute the channel and assert the durability barrier
     * runs after the rename (Mockito cannot intercept static
     * {@code FileChannel.open} on the Java 8 line).
     */
    FileChannel openDirectoryChannel(Path dir) throws IOException {
        return FileChannel.open(dir, StandardOpenOption.READ);
    }

    @Nullable
    private String readSkinFile(UUID uuid) {
        Path target = savePath.resolve(uuid + FILE_EXTENSION);
        if (!Files.exists(target)) return null;
        try {
            byte[] bytes = Files.readAllBytes(target);
            String content = new String(bytes, StandardCharsets.UTF_8);
            if (!isValidJson(content)) {
                quarantineFile(uuid);
                return null;
            }
            return content;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean isValidJson(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        try {
            JsonElement element = new JsonParser().parse(content);
            return element.isJsonObject() || element.isJsonArray();
        } catch (JsonParseException e) {
            return false;
        }
    }

    private void quarantineFile(UUID uuid) {
        Path target = savePath.resolve(uuid + FILE_EXTENSION);
        if (!Files.exists(target)) return;
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        Path quarantine = savePath.resolve(uuid + FILE_EXTENSION + CORRUPT_PREFIX + timestamp);
        try {
            Files.move(target, quarantine, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }
}
