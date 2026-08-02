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
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SkinIO {

    private static final String FILE_EXTENSION = ".json";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String CORRUPT_PREFIX = ".corrupt-";

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
        // A deferred drain must not resurrect a deleted skin from a stale payload.
        pendingWrites.remove(uuid);
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
     */
    private void writeSkinFile(UUID uuid, byte[] payload) {
        Path target = savePath.resolve(uuid + FILE_EXTENSION);
        Path temp = savePath.resolve(uuid + FILE_EXTENSION + TEMP_SUFFIX);

        try {
            Files.createDirectories(savePath);
            Files.deleteIfExists(temp);

            Files.write(temp, payload, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }

            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            SkinMetrics.INSTANCE.recordIoFailure(e);
            EverlastingSkins.logger.error("Failed to save skin for player {}", uuid, e);
            if (Files.exists(temp)) {
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    try {
                        Files.deleteIfExists(temp);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
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
            return content;
        } catch (IOException e) {
            return null;
        }
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
