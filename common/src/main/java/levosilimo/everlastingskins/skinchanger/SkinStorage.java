/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory skin cache in front of {@link SkinIO}. All persistence — the
 * synchronous write path, the coalescing async drain, and the serialized
 * delete — lives in {@link SkinIO} (single home for the async layer); this
 * facade only keeps the map and delegates.
 */
public class SkinStorage {

    private static final Logger LOGGER = LogManager.getLogger(SkinStorage.class);

    private final CustomSkinProperty DEFAULT_SKIN;

    /**
     * Static by design: the in-memory skin cache is process-wide (one cache
     * per JVM, not per {@link SkinStorage} instance). Callers must treat
     * {@link SkinStorage} as a single-instance facade over the shared cache:
     * construct one per server and route all access through it, or the
     * per-instance {@link SkinIO} delegate and this shared map drift apart.
     */
    private static final ConcurrentHashMap<UUID, CustomSkinProperty> skinMap = new ConcurrentHashMap<>();
    private final SkinIO skinIO;

    public SkinStorage(SkinIO skinIO) {
        this.skinIO = skinIO;
        this.DEFAULT_SKIN = loadDefaultSkin();
    }

    private static CustomSkinProperty loadDefaultSkin() {
        Properties props = new Properties();
        try (InputStream is = SkinStorage.class.getResourceAsStream("/everlastingskins/default-skin.properties")) {
            if (is == null) {
                throw new RuntimeException("Default skin resource not found: /everlastingskins/default-skin.properties");
            }
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load default skin resource", e);
        }
        String value = props.getProperty("skin.value");
        String signature = props.getProperty("skin.signature");
        if (value == null || signature == null) {
            throw new RuntimeException("Default skin resource missing required properties");
        }
        CustomSkinProperty.setDefaultSkinValue(value);
        return new CustomSkinProperty("textures", value, signature, null);
    }

    public CustomSkinProperty loadSkin(UUID uuid) {
        SkinMetrics.INSTANCE.recordReadStart();
        long start = System.nanoTime();
        try {
            CustomSkinProperty skin = skinIO.loadSkin(uuid);
            if (skin != null && skin.isEmpty()) {
                deleteSkinSerialized(uuid);
                return null;
            }
            return skin;
        } finally {
            SkinMetrics.INSTANCE.recordReadComplete(System.nanoTime() - start);
        }
    }

    // Access via SkinRestorer.getSkinStorage().
    public CustomSkinProperty getSkin(UUID uuid) {
        CustomSkinProperty skinProperty = skinMap.get(uuid);
        if (skinProperty != null) return skinProperty;

        skinProperty = loadSkin(uuid);
        if (skinProperty != null) {
            skinMap.put(uuid, skinProperty);
        }
        return skinProperty;
    }
    @Nullable
    public String getSource(UUID uuid) {
        CustomSkinProperty skin = skinMap.get(uuid);
        if (skin != null) {
            return skin.isEmpty() ? null : skin.getSource();
        }
        CustomSkinProperty loaded = loadSkin(uuid);
        if (loaded == null || loaded.isEmpty()) return null;
        return loaded.getSource();
    }

    @Nullable
    public String getUsername(UUID uuid) {
        CustomSkinProperty skin = skinMap.get(uuid);
        if (skin != null) {
            return skin.isEmpty() ? null : skin.getUsername();
        }
        CustomSkinProperty loaded = loadSkin(uuid);
        if (loaded == null || loaded.isEmpty()) return null;
        return loaded.getUsername();
    }

    /**
     * Strict last-write-wins save: the in-memory value is written through the
     * SkinIO writer thread and this call blocks until the write has landed.
     * SkinIO merges the payload into the pending map (superseding any queued
     * async payload for the same UUID) and submits a drain on the FIFO writer
     * thread, so the sync save is ordered after any in-flight drain write for
     * the same UUID — a stale async payload can never land after the sync
     * save (sync/async inversion).
     *
     * @throws SaveFailedException when the write could not be confirmed to
     *         have landed (timeout, interrupt, or failure after the bounded
     *         retry budget) — the caller must not assume persistence
     */
    public void saveSkin(UUID uuid) {
        CustomSkinProperty skinProperty = skinMap.get(uuid);
        if (skinProperty == null) return;
        skinIO.saveSkin(uuid, skinProperty);
    }

    /**
     * Coalescing async save: delegates to the SkinIO drain-coalesce writer.
     * The payload is serialized now and merged into the pending map (later
     * payloads for the same UUID supersede earlier ones); a single drain runs
     * after a 50ms debounce window. The returned stage completes only once
     * the payload has been durably written to disk (or the pending write is
     * superseded by a delete); callers that await it can rely on the data
     * being on disk when it completes.
     */
    public CompletableFuture<Void> saveSkinAsync(UUID uuid, CustomSkinProperty skin) {
        return skinIO.saveSkinAsync(uuid, skin);
    }

    /**
     * Blocks until all queued writes have been drained. Called on server
     * shutdown so no payload is lost mid-session.
     *
     * @throws SaveFailedException when a queued payload failed to persist
     */
    public void flushPending() {
        skinIO.flushPending();
    }

    /**
     * Raised when a serialized skin delete cannot be confirmed to have landed
     * (timeout, writer failure, or interrupted wait). The in-memory map entry
     * and the on-disk file must move together, so callers propagate this
     * instead of silently dropping the entry while the file survives.
     */
    public static class DeleteFailedException extends RuntimeException {

        DeleteFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Raised when a synchronous persistence call ({@link #saveSkin(UUID)} or
     * {@link #flushPending()}) cannot confirm the payload landed: the write
     * failed after the bounded retry budget, timed out, or was interrupted.
     * Mirrors {@link DeleteFailedException}: sync callers must not believe a
     * payload was persisted when it was dropped.
     */
    public static class SaveFailedException extends RuntimeException {

        SaveFailedException(String message) {
            super(message);
        }

        SaveFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public CustomSkinProperty removeSkin(UUID uuid) {
        // Delete the file first (serialized through the SkinIO writer thread,
        // which also purges any deferred payload) and only then drop the map
        // entry: getSkin() reloads from disk on a map miss, so removing the
        // entry before the file is gone would let a concurrent read resurrect
        // the cleared skin into the map. A failed delete propagates instead
        // of dropping the entry: the file and the map must move together.
        deleteSkinSerialized(uuid);
        skinMap.remove(uuid);
        return null;
    }

    /**
     * Runs the file deletion through {@link SkinIO#deleteSkin(UUID)}, which
     * serializes it on the single writer thread so it is ordered against
     * drain writes and blocks until the delete has landed. Failure is raised
     * as {@link DeleteFailedException}, never swallowed: the caller must not
     * drop the in-memory entry while the file may still exist, or a later
     * getSkin() reload resurrects the cleared skin.
     */
    private void deleteSkinSerialized(UUID uuid) {
        try {
            skinIO.deleteSkin(uuid);
        } catch (IOException e) {
            LOGGER.warn("Failed to delete skin file for {}", uuid, e);
            throw new DeleteFailedException("Skin delete of " + uuid + " failed", e);
        } catch (DeleteFailedException e) {
            // Already fail-closed (interrupt/timeout in SkinIO); rethrow as-is
            // so the caller sees the original reason, not a double wrap.
            throw e;
        } catch (RuntimeException e) {
            LOGGER.warn("Skin delete of {} failed", uuid, e);
            throw new DeleteFailedException("Skin delete of " + uuid + " failed", e);
        }
    }

    public CustomSkinProperty setSkin(UUID uuid, @Nullable CustomSkinProperty skin) {
        if (skin == null || skin.isEmpty()) return removeSkin(uuid);
        skinMap.put(uuid, skin);
        return skin;
    }

    public boolean hasDefaultSkin(UUID uuid) {
        CustomSkinProperty skin = skinMap.get(uuid);
        if (skin == null) {
            skin = loadSkin(uuid);
            if (skin == null) return true;
            skinMap.put(uuid, skin);
        }
        return DEFAULT_SKIN.equals(skin) || skin.isEmpty();
    }

    /**
     * Test-only: true while payloads are still queued for the drain-coalesce
     * writer. Lets tests wait for disk quiescence instead of racing the 50ms
     * debounce window.
     */
    public boolean hasPendingWrites() {
        return skinIO.hasPendingWrites();
    }

    /**
     * Test-only: clears the static in-memory skin cache so integration tests
     * start isolated (player-name-derived UUIDs collide across tests).
     */
    public static void resetForTest() {
        skinMap.clear();
    }
}
