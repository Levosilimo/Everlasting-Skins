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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /** {@link #checksumStatus(String)} result: canonical record matches its marker. */
    private static final int CHECKSUM_VALID = 0;
    /** {@link #checksumStatus(String)} result: record is malformed or its marker no longer matches. */
    private static final int CHECKSUM_MISMATCH = 1;
    /** {@link #checksumStatus(String)} result: record carries no marker (legacy format). */
    private static final int CHECKSUM_ABSENT = 2;

    /**
     * Single-thread writer so per-UUID writes are serialized; latest payload
     * per UUID wins (coalescing). Daemon thread so it never blocks shutdown.
     * Lazily (re)created: a shutdown (e.g. ServerStoppingEvent in tests or a
     * reload) must not permanently kill the writer for subsequent saves.
     */
    private static ScheduledExecutorService writer;

    private static final long DRAIN_DEBOUNCE_MS = 50;
    private static final AtomicBoolean drainScheduled = new AtomicBoolean();

    private static synchronized ScheduledExecutorService writer() {
        if (writer == null || writer.isShutdown()) {
            writer = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "EverlastingSkins-IO");
                t.setDaemon(true);
                return t;
            });
        }
        return writer;
    }

    /** Latest serialized payload per UUID awaiting the writer thread. */
    private final ConcurrentHashMap<UUID, byte[]> pendingWrites = new ConcurrentHashMap<>();

    private final Path savePath;

    public SkinIO(Path savePath) {
        this.savePath = savePath;
    }

    @Nullable
    public String getSourceFromFileStorage(UUID uuid) {
        String skinJson = readSkinFile(uuid);
        if (skinJson == null) return null;
        try {
            JsonObject obj = JsonParser.parseString(skinJson).getAsJsonObject();
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

        public void deleteSkin(UUID uuid) {
        // Drop any deferred payload first so a drain that has not started yet
        // skips this UUID, then perform the file deletion ON the writer thread.
        // An in-flight drain that already pulled this payload completes its
        // write before our delete runs (single writer thread), so a cleared
        // skin can never be resurrected by a stale write landing after the
        // delete (write-after-delete race).
        pendingWrites.remove(uuid);
        try {
            writer().submit(() -> deleteSkinFile(uuid)).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            EverlastingSkins.logger.warn("SkinIO delete did not complete in time for {}", uuid, e);
        }
    }

    private void deleteSkinFile(UUID uuid) {
        Path target = savePath.resolve(uuid + FILE_EXTENSION);
        try {
            if (Files.deleteIfExists(target)) {
                EverlastingSkins.logger.info("Deleted skin file for {}", uuid);
            }
        } catch (IOException e) {
            EverlastingSkins.logger.warn("Failed to delete skin file for {}", uuid, e);
        }
    }

    /**
     * Synchronous save for back-compat: merges the payload and blocks until
     * the writer thread has drained it, so all writes are serialized through
     * the single writer thread (no temp-file races).
     */
    public void saveSkin(UUID uuid, CustomSkinProperty skin) {
        merge(uuid, skin);
        drainScheduled.set(false);
        try {
            writer().submit(this::drainPending).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            EverlastingSkins.logger.warn("SkinIO save did not complete in time", e);
        }
    }

    /**
     * Coalescing async save: merges the payload into the pending map and
     * schedules a single drain after a 50ms debounce window, so burst updates
     * for the same UUID collapse into one disk write. The returned future
     * completes once the payload has been merged (the write itself happens on
     * the writer thread).
     */
    public CompletableFuture<Void> saveSkinAsync(UUID uuid, CustomSkinProperty skin) {
        merge(uuid, skin);
        scheduleDrain();
        return CompletableFuture.completedFuture(null);
    }

    private void merge(UUID uuid, CustomSkinProperty skin) {
        byte[] payload = JsonUtils.toJson(skin).getBytes(StandardCharsets.UTF_8);
        boolean superseded = pendingWrites.put(uuid, payload) != null;
        if (superseded) {
            SkinMetrics.INSTANCE.recordSaveCoalesced();
            SkinMetrics.INSTANCE.recordSaveCompleted();
        }
        SkinMetrics.INSTANCE.recordSaveSubmitted();
    }

    private void scheduleDrain() {
        if (drainScheduled.getAndSet(true)) return;
        try {
            writer().schedule(this::drainPending, DRAIN_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            EverlastingSkins.logger.warn("SkinIO drain schedule failed", e);
        }
    }

    /** Writes every pending payload once; superseded payloads were dropped at merge time. */
    private void drainPending() {
        for (UUID uuid : pendingWrites.keySet()) {
            byte[] payload = pendingWrites.remove(uuid);
            if (payload == null) continue;
            long start = System.nanoTime();
            writeSkinFile(uuid, payload);
            SkinMetrics.INSTANCE.recordSaveDiskLatency(System.nanoTime() - start);
            SkinMetrics.INSTANCE.recordRealWrite();
            SkinMetrics.INSTANCE.recordSaveCompleted();
        }
        // Reset the latch so future saveSkinAsync calls schedule again. If new
        // writes arrived during this drain, schedule the next drain immediately
        // (covers the race between reset and the next saveSkinAsync).
        drainScheduled.set(false);
        if (!pendingWrites.isEmpty()) {
            scheduleDrain();
        }
    }

    /**
     * Blocks until all queued writes have been drained. Called synchronously
     * on logout and shutdown.
     */
    public void flushPending() {
        drainScheduled.set(false);
        try {
            writer().submit(this::drainPending).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            EverlastingSkins.logger.warn("SkinIO flush did not complete in time", e);
        }
    }

    /** Shuts down the writer thread, awaiting in-flight writes. The writer is
     *  recreated on demand by the next save (see {@link #writer()}). */
    public static void shutdown() {
        if (writer == null) return;
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        writer = null;
    }

    /**
     * Atomic write: temp file + force + ATOMIC_MOVE, with a fallback move on
     * failure. Preserved from the original synchronous implementation.
     * After the rename the parent directory is fsynced so the directory
     * entry itself survives a power loss (Pillai OSDI'14 safe file flush).
     */
    private void writeSkinFile(UUID uuid, byte[] payload) {
        Path target = savePath.resolve(uuid + FILE_EXTENSION);
        Path temp = savePath.resolve(uuid + FILE_EXTENSION + TEMP_SUFFIX);

        try {
            Files.createDirectories(savePath);
            Files.deleteIfExists(temp);

            Files.write(temp, withChecksum(payload), StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }

            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            fsyncDirectory();
        } catch (IOException e) {
            SkinMetrics.INSTANCE.recordIoFailure(e);
            EverlastingSkins.logger.error("Failed to save skin for player {}", uuid, e);
            if (Files.exists(temp)) {
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                    fsyncDirectory();
                } catch (IOException ex) {
                    try {
                        Files.deleteIfExists(temp);
                    } catch (IOException ignored) {
                    }
                }
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
            JsonObject obj = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
            obj.remove(CHECKSUM_FIELD);
            String canonical = JsonUtils.toJson(obj);
            obj.addProperty(CHECKSUM_FIELD, sha256Hex(canonical.getBytes(StandardCharsets.UTF_8)));
            return JsonUtils.toJson(obj).getBytes(StandardCharsets.UTF_8);
        } catch (JsonParseException | IllegalStateException e) {
            // Not a JSON object: write verbatim (legacy format, no marker).
            return payload;
        }
    }

    /**
     * Per-thread SHA-256 digest reused across calls instead of allocating a
     * fresh {@link MessageDigest} per hash. Reusing the digest avoids GC
     * pressure and per-call JCE provider lookup; safe because each call
     * resets the digest via {@link MessageDigest#reset()} before update.
     * The provider lookup failure is surfaced on first use, identically to
     * the per-call allocation it replaces.
     */
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    });

    /** Hex SHA-256 of the given bytes. Built-in {@link MessageDigest}: no new dependency. */
    private static String sha256Hex(byte[] data) {
        MessageDigest digest = SHA_256.get();
        digest.reset();
        byte[] hash = digest.digest(data);
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    /**
     * Durability barrier: fsync the parent directory after the rename.
     * File fsync alone does not flush the rename's directory entry, so
     * without this a power loss can lose the write even though the content
     * was already durable. Best-effort: the content is already fsynced, so
     * a directory fsync failure is logged, never propagated.
     */
    private void fsyncDirectory() {
        try (FileChannel dirChannel = openDirectoryChannel(savePath)) {
            dirChannel.force(true);
        } catch (UnsupportedOperationException ignored) {
            // Some filesystems (e.g. FAT) don't support dir fsync; tolerate silently.
        } catch (IOException e) {
            EverlastingSkins.logger.warn("Failed to fsync skin directory {}", savePath, e);
        }
    }

    /**
     * Opens the save directory read-only so it can be fsynced. Package-private
     * so tests can substitute the channel and assert the durability barrier
     * runs after the rename.
     */
    FileChannel openDirectoryChannel(Path dir) throws IOException {
        return FileChannel.open(dir, StandardOpenOption.READ);
    }

    @Nullable
    private String readSkinFile(UUID uuid) {
        Path target = savePath.resolve(uuid + FILE_EXTENSION);
        if (!Files.exists(target)) return null;
        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);
            if (!isValidJson(content)) {
                quarantineFile(uuid);
                return null;
            }
            int status = checksumStatus(content);
            if (status == CHECKSUM_MISMATCH) {
                EverlastingSkins.logger.warn("Bit rot detected in skin record {}: checksum mismatch, quarantining", target);
                quarantineFile(uuid);
                return null;
            }
            if (status == CHECKSUM_ABSENT) {
                EverlastingSkins.logger.warn(
                        "Skin record {} has no checksum (legacy format); loaded without integrity verification", target);
            }
            return content;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Recomputes the in-band SHA-256 of a parsed record and compares it to
     * the stored marker. The canonical form (record minus the marker member,
     * re-serialized through the same Gson) is byte-identical to the bytes
     * that were hashed on write, so a single flipped byte in the value,
     * signature or any other member fails the comparison — the silent
     * failure mode that {@code isValidJson} cannot see (bit rot that still
     * parses as valid JSON). Returns {@link #CHECKSUM_VALID} on a match,
     * {@link #CHECKSUM_MISMATCH} on malformed JSON or a digest mismatch and
     * {@link #CHECKSUM_ABSENT} for legacy records without a marker.
     */
    private static int checksumStatus(String content) {
        JsonElement element;
        try {
            element = JsonParser.parseString(content);
        } catch (JsonParseException e) {
            return CHECKSUM_MISMATCH;
        }
        if (!element.isJsonObject()) {
            // Array-shaped records predate the marker; nothing to verify.
            return CHECKSUM_ABSENT;
        }
        JsonObject obj = element.getAsJsonObject();
        if (!obj.has(CHECKSUM_FIELD) || obj.get(CHECKSUM_FIELD).isJsonNull()) {
            return CHECKSUM_ABSENT;
        }
        String expected = obj.get(CHECKSUM_FIELD).getAsString();
        obj.remove(CHECKSUM_FIELD);
        String canonical = JsonUtils.toJson(obj);
        boolean matches = sha256Hex(canonical.getBytes(StandardCharsets.UTF_8)).equals(expected);
        return matches ? CHECKSUM_VALID : CHECKSUM_MISMATCH;
    }

    private static boolean isValidJson(String content) {
        if (content == null || content.isBlank()) return false;
        try {
            JsonElement element = JsonParser.parseString(content);
            return element.isJsonObject() || element.isJsonArray();
        } catch (JsonParseException e) {
            return false;
        }
    }

    private void quarantineFile(UUID uuid) {
        quarantineFile(savePath.resolve(uuid + FILE_EXTENSION));
    }

    /**
     * Renames a corrupt record to {@code <name>.corrupt-<ts>} so it is never
     * re-read. Called for malformed JSON and checksum mismatches alike; a
     * mismatched record that still parses as JSON is quarantined identically.
     */
    private void quarantineFile(Path target) {
        if (!Files.exists(target)) return;
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        Path quarantine = target.resolveSibling(target.getFileName() + CORRUPT_PREFIX + timestamp);
        try {
            Files.move(target, quarantine, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    /**
     * Startup integrity sweep: scans every {@code *.json} record in the save
     * directory, recomputes the in-band SHA-256 and quarantines any record
     * whose marker no longer matches (bit rot detected before any player
     * logs in, so a silently corrupt skin can never be applied). Legacy
     * records without a marker pass through, as do {@code .tmp} and
     * {@code .corrupt-*} files. Logs a summary line with the counts; the
     * result is returned so tests (and the summary) can assert the sweep
     * outcome. One hash per record: negligible at startup scale.
     */
    public SweepResult validateAllFiles() {
        if (!Files.isDirectory(savePath)) {
            EverlastingSkins.logger.info("SkinIO sweep: no skin directory at {}, nothing to validate", savePath);
            return new SweepResult(0, 0, 0);
        }
        int checked = 0;
        int corrupt = 0;
        int legacy = 0;
        try (Stream<Path> files = Files.list(savePath)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                if (!name.endsWith(FILE_EXTENSION)) continue;
                checked++;
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    int status = checksumStatus(content);
                    if (status == CHECKSUM_MISMATCH) {
                        EverlastingSkins.logger.warn("Bit rot detected in {} during startup sweep; quarantining", file);
                        quarantineFile(file);
                        corrupt++;
                    } else if (status == CHECKSUM_ABSENT) {
                        legacy++;
                    }
                } catch (IOException e) {
                    EverlastingSkins.logger.warn("SkinIO sweep could not read {}; leaving it in place", file, e);
                }
            }
        } catch (IOException e) {
            EverlastingSkins.logger.warn("SkinIO sweep failed to list {}", savePath, e);
        }
        EverlastingSkins.logger.info("SkinIO sweep: {} files checked, {} corrupt quarantined, {} legacy without checksum",
                checked, corrupt, legacy);
        return new SweepResult(checked, corrupt, legacy);
    }

    /** Outcome of {@link #validateAllFiles()}. */
    public static final class SweepResult {
        public final int filesChecked;
        public final int corruptFound;
        public final int legacyFound;

        SweepResult(int filesChecked, int corruptFound, int legacyFound) {
            this.filesChecked = filesChecked;
            this.corruptFound = corruptFound;
            this.legacyFound = legacyFound;
        }
    }
}
