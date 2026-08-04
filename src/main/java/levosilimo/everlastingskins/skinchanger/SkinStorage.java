/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.JsonUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class SkinStorage {

    private static final ScheduledExecutorService SAVE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "EverlastingSkins-SkinIO");
        t.setDaemon(true);
        return t;
    });

    /**
     * Debounce window: burst saves for the same UUID collapse into one disk
     * write. Port of the PR #121 A2 drain-coalesce writer.
     */
    private static final long DEBOUNCE_MS = 50;

    /** Latest serialized payload per UUID awaiting the writer thread. */
    private final ConcurrentHashMap<UUID, byte[]> pendingWrites = new ConcurrentHashMap<>();
    /** Latch so only one drain is scheduled at a time. */
    private final AtomicBoolean drainScheduled = new AtomicBoolean(false);

    private final CustomSkinProperty DEFAULT_SKIN;
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
        CustomSkinProperty skin = skinIO.loadSkin(uuid);
        if (skin != null && skin.isEmpty()) {
            pendingWrites.remove(uuid);
            deleteSkinSerialized(uuid);
            return null;
        }
        return skin;
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

    public void saveSkin(UUID uuid) {
        CustomSkinProperty skinProperty = skinMap.get(uuid);
        if (skinProperty != null) {
            skinIO.saveSkin(uuid, skinProperty);
        }
    }

    /**
     * Coalescing async save: serializes the payload now, merges it into the
     * pending map (later payloads for the same UUID supersede earlier ones),
     * and schedules a single drain after a 50ms debounce window. Port of the
     * PR #121 A2 drain-coalesce writer; superseded payloads never reach disk
     * and count as savesCoalesced instead of realWrites.
     */
    public void saveSkinAsync(UUID uuid, CustomSkinProperty skin) {
        byte[] payload = JsonUtils.toJson(skin).getBytes(StandardCharsets.UTF_8);
        boolean superseded = pendingWrites.put(uuid, payload) != null;
        if (superseded) {
            SkinMetrics.INSTANCE.recordSaveCoalesced();
            SkinMetrics.INSTANCE.recordSaveCompleted();
        }
        SkinMetrics.INSTANCE.recordSaveSubmitted();
        scheduleDrain();
    }

    private void scheduleDrain() {
        if (drainScheduled.compareAndSet(false, true)) {
            SAVE_EXECUTOR.schedule(this::drainPending, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        }
    }

    /** Writes every pending payload once; superseded payloads were dropped at merge time. */
    private void drainPending() {
        try {
            for (UUID uuid : pendingWrites.keySet()) {
                byte[] payload = pendingWrites.remove(uuid);
                if (payload == null) continue;
                long start = System.nanoTime();
                skinIO.saveSkin(uuid, payload);
                SkinMetrics.INSTANCE.recordSaveDiskLatency(System.nanoTime() - start);
                SkinMetrics.INSTANCE.recordRealWrite();
                SkinMetrics.INSTANCE.recordSaveCompleted();
            }
        } finally {
            // Race fix from PR #121 (7cc66cf): reset the latch at the END of the
            // drain, and reschedule if new writes arrived while draining. Without
            // this the latch sticks true and later saves silently defer to flush.
            drainScheduled.set(false);
            if (!pendingWrites.isEmpty()) {
                scheduleDrain();
            }
        }
    }

    /**
     * Blocks until all queued writes have been drained. Called on server
     * shutdown so no payload is lost mid-session.
     */
    public void flushPending() {
        drainScheduled.set(false);
        try {
            SAVE_EXECUTOR.submit(this::drainPending).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            EverlastingSkins.logger.warn("Skin flush did not complete in time", e);
        }
    }

    public CustomSkinProperty removeSkin(UUID uuid) {
        pendingWrites.remove(uuid); // purge deferred drain; it must not resurrect a deleted skin
        skinMap.remove(uuid);
        deleteSkinSerialized(uuid);
        return null;
    }

    /**
     * Runs the file deletion on the single writer thread so it is serialized
     * against drain writes, and blocks until the delete has landed. An
     * in-flight drain that already pulled this payload completes its write
     * before the delete runs, so no stale write can land after the delete
     * (write-after-delete race).
     */
    private void deleteSkinSerialized(UUID uuid) {
        try {
            SAVE_EXECUTOR.submit(() -> skinIO.deleteSkin(uuid)).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            EverlastingSkins.logger.warn("Skin delete did not complete in time for {}", uuid, e);
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
        return !pendingWrites.isEmpty();
    }

    /**
     * Test-only: clears the static in-memory skin cache so integration tests
     * start isolated (player-name-derived UUIDs collide across tests).
     */
    public static void resetForTest() {
        skinMap.clear();
    }
}
