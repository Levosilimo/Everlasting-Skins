/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkinStorage {

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
        SkinMetrics.INSTANCE.recordReadStart();
        long start = System.nanoTime();
        try {
            CustomSkinProperty skin = skinIO.loadSkin(uuid);
            if (skin != null && skin.isEmpty()) {
                skinIO.deleteSkin(uuid);
                return null;
            }
            return skin;
        } finally {
            SkinMetrics.INSTANCE.recordReadComplete(System.nanoTime() - start);
        }
    }

    // Retrieve via SkinRestorer.getSkinStorage(); this instance is the backing store.
    public CustomSkinProperty getSkin(UUID uuid) {
        return skinMap.computeIfAbsent(uuid, k -> loadSkin(k));
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

    /** Synchronous save; used where durability is required immediately. */
    public void saveSkin(UUID uuid) {
        CustomSkinProperty skinProperty = skinMap.get(uuid);
        if (skinProperty != null) {
            skinIO.saveSkin(uuid, skinProperty);
        }
    }

    /** Coalescing async save; used on the hot refresh path. */
    public void saveSkinAsync(UUID uuid) {
        CustomSkinProperty skinProperty = skinMap.get(uuid);
        if (skinProperty != null) {
            skinIO.saveSkinAsync(uuid, skinProperty);
        }
    }

    public CustomSkinProperty removeSkin(UUID uuid) {
        // Delete the file first (blocking, serialized through the SkinIO writer
        // thread) and only then drop the map entry: getSkin() reloads from disk
        // on a map miss, so removing the entry before the file is gone would let
        // a concurrent read resurrect the cleared skin into the map.
        skinIO.deleteSkin(uuid);
        skinMap.remove(uuid);
        return null;
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
}
