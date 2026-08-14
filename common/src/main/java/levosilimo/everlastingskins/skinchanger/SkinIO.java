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
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.JsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import java.util.stream.Stream;

public class SkinIO {

    private static final Logger LOGGER = LogManager.getLogger(SkinIO.class);

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
     *
     * <p>Static by design: the JVM-wide drain-coalesce contract keeps one
     * writer thread shared across all {@link SkinIO} instances, so a drain
     * from instance A and a drain from instance B are serialized FIFO on the
     * same thread. All per-instance state (the pending payload map, the
     * debounce latch, the completion futures) lives on the instance, so one
     * instance's drain can never cancel or skip another instance's scheduled
     * drain. Callers must still treat the shared writer as single-threaded:
     * a blocked drain on one instance stalls the writer for every instance.
     */
    private static ScheduledExecutorService writer;

    private static final long DRAIN_DEBOUNCE_MS = 50;

    /**
     * Bounded write-retry budget per payload. A transient failure (e.g. a
     * busy temp file) re-queues the payload for another attempt instead of
     * dropping it; only after this many consecutive failures is the payload
     * abandoned (and surfaced to sync callers / completed futures).
     */
    private static final int MAX_WRITE_ATTEMPTS = 3;

    /**
     * Per-instance debounce latch. Static would leak state across instances:
     * instance A's drain resetting the shared latch would skip instance B's
     * scheduled drain, stranding B's payload until the next save or flush.
     */
    private final AtomicBoolean drainScheduled = new AtomicBoolean();

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

    /**
     * Futures returned by {@link #saveSkinAsync(UUID, CustomSkinProperty)},
     * keyed by UUID. Coalescing means one future per UUID: it completes only
     * when the payload is durably written (or the write is superseded by a
     * delete), so awaiters never observe a "saved" stage before the data is
     * on disk.
     */
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> pendingFutures = new ConcurrentHashMap<>();

    /**
     * UUIDs whose payloads were dropped after {@link #MAX_WRITE_ATTEMPTS}
     * failed write attempts. Lets sync callers (saveSkin, flushPending) fail
     * closed instead of believing a lost payload was persisted.
     */
    private final Set<UUID> failedWrites = ConcurrentHashMap.newKeySet();

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
     * Deletes the skin file, serialized through the single writer thread.
     * The deferred payload is dropped first so a drain that has not started
     * yet skips this UUID, and an in-flight drain that already pulled the
     * payload completes its write BEFORE the delete runs (single writer
     * thread) — a cleared skin can never be resurrected by a stale write
     * landing after the delete (write-after-delete race). Blocks until the
     * delete has landed. Failures propagate so the caller can keep the
     * in-memory state in lockstep with the disk instead of dropping the map
     * entry while the file survives (later reload resurrects the skin).
     */
    public void deleteSkin(UUID uuid) throws IOException {
        pendingWrites.remove(uuid);
        // The deferred payload is superseded by the delete: no write will
        // land for this UUID, so settle any futures awaiting it rather than
        // leaving them pending forever.
        completePending(uuid);
        try {
            writer().submit(() -> {
                deleteSkinFile(uuid);
                return null;
            }).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Fail closed, like every other delete outcome: the caller must
            // keep the map entry because the file may still exist. Swallowing
            // the interrupt would drop the entry while the file survives and
            // a later reload resurrects the cleared skin.
            throw new SkinStorage.DeleteFailedException("Skin delete of " + uuid + " interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IOException("Skin delete of " + uuid + " failed", cause);
        } catch (TimeoutException e) {
            throw new SkinStorage.DeleteFailedException("Skin delete of " + uuid + " timed out after 5s", e);
        }
    }

    private void deleteSkinFile(UUID uuid) throws IOException {
        Path target = savePath.resolve(uuid + FILE_EXTENSION);
        if (Files.deleteIfExists(target)) {
            LOGGER.info("Deleted skin file for {}", uuid);
        }
    }

    /**
     * Synchronous save for back-compat: merges the payload and blocks until
     * the writer thread has drained it, so all writes are serialized through
     * the single writer thread (no temp-file races). Failures are surfaced as
     * {@link SkinStorage.SaveFailedException}, never swallowed: a sync caller
     * must not believe a payload was persisted when it was dropped.
     */
    public void saveSkin(UUID uuid, CustomSkinProperty skin) {
        merge(uuid, skin);
        drainScheduled.set(false);
        try {
            writer().submit(this::drainPending).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkinStorage.SaveFailedException("Skin save of " + uuid + " interrupted", e);
        } catch (TimeoutException e) {
            throw new SkinStorage.SaveFailedException("Skin save of " + uuid + " timed out after 5s", e);
        } catch (ExecutionException e) {
            throw new SkinStorage.SaveFailedException("Skin save of " + uuid + " failed", e.getCause());
        }
        if (failedWrites.remove(uuid)) {
            // The drain dropped the payload after MAX_WRITE_ATTEMPTS.
            throw new SkinStorage.SaveFailedException(
                    "Skin save of " + uuid + " failed after " + MAX_WRITE_ATTEMPTS + " attempts");
        }
    }

    /**
     * Coalescing async save: merges the payload into the pending map and
     * schedules a single drain after a 50ms debounce window, so burst updates
     * for the same UUID collapse into one disk write. The returned future
     * completes only when the payload has been durably written to disk (or
     * the pending write is superseded by a delete); on a write that exhausts
     * the retry budget it completes exceptionally.
     */
    public CompletableFuture<Void> saveSkinAsync(UUID uuid, CustomSkinProperty skin) {
        CompletableFuture<Void> future = pendingFutures.computeIfAbsent(uuid, k -> new CompletableFuture<>());
        merge(uuid, skin);
        scheduleDrain();
        return future;
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
            // Intentional fire-and-forget debounce: drainPending logs its own
            // failures, so the timer future carries no observable signal.
            @SuppressWarnings("FutureReturnValueIgnored")
            ScheduledFuture<?> unused = writer().schedule(this::drainPending, DRAIN_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOGGER.warn("SkinIO drain schedule failed", e);
        }
    }

    /**
     * Writes every pending payload once; superseded payloads were dropped at
     * merge time. A failed payload is retried (bounded by
     * {@link #MAX_WRITE_ATTEMPTS}) instead of being removed before its write
     * lands: removing first would drop the record permanently on a transient
     * failure.
     */
    private void drainPending() {
        try {
            for (UUID uuid : pendingWrites.keySet()) {
                drainOne(uuid);
            }
        } catch (Exception e) {
            // Safety net for unexpected runtime failures: payloads stay in the
            // map and the finally block re-schedules the drain, so nothing is
            // lost. Per-UUID write failures are handled inside drainOne.
            LOGGER.error("SkinIO drain failed", e);
        } finally {
            // Reset the latch so future saveSkinAsync calls schedule again. If new
            // writes arrived during this drain, schedule the next drain immediately
            // (covers the race between reset and the next saveSkinAsync).
            drainScheduled.set(false);
            if (!pendingWrites.isEmpty()) {
                scheduleDrain();
            }
        }
    }

    /**
     * Writes one UUID's latest payload, retrying transient failures up to
     * {@link #MAX_WRITE_ATTEMPTS} times. The payload is removed from the
     * pending map only after a successful write (a newer payload that
     * superseded it during the write is left queued and written next), and on
     * final failure the payload is dropped but recorded in
     * {@link #failedWrites} so sync callers fail closed; the async future, if
     * any, completes exceptionally.
     */
    private void drainOne(UUID uuid) {
        int attempts = 0;
        while (true) {
            byte[] payload = pendingWrites.get(uuid);
            if (payload == null) {
                return; // deleted or already drained
            }
            try {
                long start = System.nanoTime();
                saveSkin(uuid, payload);
                SkinMetrics.INSTANCE.recordSaveDiskLatency(System.nanoTime() - start);
                SkinMetrics.INSTANCE.recordRealWrite();
                SkinMetrics.INSTANCE.recordSaveCompleted();
                // remove(uuid, payload) keeps a newer superseding payload queued
                // for the next drain; only complete the future when no newer
                // payload is waiting, or an awaiter would see "durable" before
                // the superseding write lands.
                pendingWrites.remove(uuid, payload);
                failedWrites.remove(uuid);
                if (pendingWrites.get(uuid) == null) {
                    completePending(uuid);
                }
                return;
            } catch (IOException e) {
                attempts++;
                if (attempts >= MAX_WRITE_ATTEMPTS) {
                    pendingWrites.remove(uuid, payload);
                    failedWrites.add(uuid);
                    failPending(uuid, e);
                    LOGGER.error("SkinIO write of {} failed after {} attempts; payload dropped",
                            uuid, attempts, e);
                    return;
                }
                LOGGER.warn("SkinIO write of {} failed (attempt {}/{}); retrying",
                        uuid, attempts, MAX_WRITE_ATTEMPTS, e);
            }
        }
    }

    /** Completes (normally) the async future for a UUID whose payload is no longer pending. */
    private void completePending(UUID uuid) {
        CompletableFuture<Void> future = pendingFutures.remove(uuid);
        if (future != null) {
            future.complete(null);
        }
    }

    /** Completes (exceptionally) the async future for a UUID whose payload was dropped. */
    private void failPending(UUID uuid, Throwable cause) {
        CompletableFuture<Void> future = pendingFutures.remove(uuid);
        if (future != null) {
            future.completeExceptionally(cause);
        }
    }

    /**
     * Blocks until all queued writes have been drained. Called synchronously
     * on logout and shutdown. Persistence failures are surfaced as
     * {@link SkinStorage.SaveFailedException}: a flush that lost a payload
     * must not look like a clean drain.
     */
    public void flushPending() {
        drainScheduled.set(false);
        try {
            writer().submit(this::drainPending).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkinStorage.SaveFailedException("SkinIO flush interrupted", e);
        } catch (TimeoutException e) {
            throw new SkinStorage.SaveFailedException("SkinIO flush timed out after 5s", e);
        } catch (ExecutionException e) {
            throw new SkinStorage.SaveFailedException("SkinIO flush failed", e.getCause());
        }
        if (!failedWrites.isEmpty()) {
            throw new SkinStorage.SaveFailedException(
                    "SkinIO flush: " + failedWrites.size() + " payload(s) failed to persist");
        }
    }

    /**
     * True while payloads are still queued for the drain-coalesce writer.
     * Lets tests (and shutdown paths) wait for disk quiescence instead of
     * racing the 50ms debounce window.
     */
    public boolean hasPendingWrites() {
        return !pendingWrites.isEmpty();
    }

    /**
     * Shuts down the writer thread, awaiting in-flight writes. The writer is
     * recreated on demand by the next save (see {@link #writer()}). Callers
     * should drain first via {@link #flushPending()} so no payload (and no
     * awaiting {@link #saveSkinAsync(UUID, CustomSkinProperty)} future) is
     * stranded by the shutdown.
     */
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
     * Atomic write of a pre-serialized payload. This is the drain's write
     * hook: package-private so tests can substitute a blocking or failing
     * writer and hold a drain in flight deterministically. Failures are
     * recorded in the I/O-failure metrics and rethrown so the drain can
     * retry the payload instead of silently dropping it.
     */
    void saveSkin(UUID uuid, byte[] payload) throws IOException {
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
            LOGGER.error("Failed to save skin for player {}", uuid, e);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Best-effort cleanup; a leftover temp file is harmless.
            }
            throw e;
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
            LOGGER.warn("Atomic rename unsupported for {}, downgrading to non-atomic move", uuid, e);
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
            LOGGER.warn("Failed to fsync skin directory {}", savePath, e);
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
        SkinMetrics.INSTANCE.recordReadStart();
        long start = System.nanoTime();
        String content;
        try {
            content = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SkinMetrics.INSTANCE.recordReadFailure();
            return null;
        }
        SkinMetrics.INSTANCE.recordReadComplete(System.nanoTime() - start);
        if (!isValidJson(content)) {
            quarantineFile(uuid);
            return null;
        }
        int status = checksumStatus(content);
        if (status == CHECKSUM_MISMATCH) {
            LOGGER.warn("Bit rot detected in skin record {}: checksum mismatch, quarantining", target);
            quarantineFile(uuid);
            return null;
        }
        if (status == CHECKSUM_ABSENT) {
            LOGGER.warn(
                    "Skin record {} has no checksum (legacy format); loaded without integrity verification", target);
        }
        return content;
    }

    /**
     * Recomputed the in-band SHA-256 of a parsed record and compares it to
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
            element = new JsonParser().parse(content);
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
        if (content == null || content.trim().isEmpty()) return false;
        try {
            JsonElement element = new JsonParser().parse(content);
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
            // Best-effort quarantine; the corrupt record stays in place and
            // is retried next sweep.
        }
    }

    /**
     * Startup integrity sweep: scans every {@code *.json} record in the save
     * directory, recomputes the in-band SHA-256 and quarantines any record
     * whose marker no longer matches (bit rot detected before any player
     * logs in, so a silently corrupt skin can never be applied). Legacy
     * records without a marker pass through, as do {@code .tmp} and
     * {@code .corrupt-*} files. Logs a per-file warning for each markerless
     * legacy record — the per-file log enables operators to identify which
     * files lack the checksum (e.g. created by an older mod version or
     * manually edited) — plus a summary line with the counts; the result is
     * returned so tests (and the summary) can assert the sweep outcome. One
     * hash per record: negligible at startup scale.
     */
    public SweepResult validateAllFiles() {
        if (!Files.isDirectory(savePath)) {
            LOGGER.info("SkinIO sweep: no skin directory at {}, nothing to validate", savePath);
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
                    String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    int status = checksumStatus(content);
                    if (status == CHECKSUM_MISMATCH) {
                        LOGGER.warn("Bit rot detected in {} during startup sweep; quarantining", file);
                        quarantineFile(file);
                        corrupt++;
                    } else if (status == CHECKSUM_ABSENT) {
                        // Per-file log mirrors the read path's warning so
                        // operators can identify which files lack the
                        // checksum (e.g. created by an older mod version or
                        // manually edited), not just a count in the summary.
                        LOGGER.warn(
                                "Skin record {} has no checksum (legacy format); passed through without integrity verification", file);
                        legacy++;
                    }
                } catch (IOException e) {
                    LOGGER.warn("SkinIO sweep could not read {}; leaving it in place", file, e);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("SkinIO sweep failed to list {}", savePath, e);
        }
        LOGGER.info("SkinIO sweep: {} files checked, {} corrupt quarantined, {} legacy without checksum",
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
