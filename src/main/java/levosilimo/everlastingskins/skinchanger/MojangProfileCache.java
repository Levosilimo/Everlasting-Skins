/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small TTL + cap cache for Mojang profile lookups. Keyed by lower-case
 * username; a hit avoids the (slow, rate-limited) Mojang HTTP chain entirely.
 * Zero external dependencies.
 */
public class MojangProfileCache {

    private record CacheEntry(CustomSkinProperty property, long fetchedAtMs) {
    }

    private final LinkedHashMap<String, CacheEntry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final long ttlMs;
    private final int maxEntries;

    public MojangProfileCache() {
        this(Config.MOJANG_CACHE_TTL_MS.get(), Config.MOJANG_CACHE_MAX_SIZE.get());
    }

    public MojangProfileCache(long ttlMs, int maxEntries) {
        this.ttlMs = ttlMs;
        this.maxEntries = maxEntries;
    }

    /** True when the cache is active per config; a disabled cache never stores or serves entries. */
    public boolean isEnabled() {
        return Config.MOJANG_CACHE_ENABLED.get();
    }

    /** Returns the cached skin for the username, or null when absent/expired. */
    public synchronized CustomSkinProperty get(String username) {
        if (!isEnabled()) return null;
        if (username == null) return null;
        String key = username.toLowerCase();
        CacheEntry entry = entries.get(key);
        if (entry == null) {
            SkinMetrics.INSTANCE.recordCacheMiss();
            return null;
        }
        if (System.currentTimeMillis() - entry.fetchedAtMs() > ttlMs) {
            entries.remove(key, entry);
            SkinMetrics.INSTANCE.recordCacheMiss();
            return null;
        }
        SkinMetrics.INSTANCE.recordCacheHit();
        return entry.property();
    }

    /** Stores a fetched skin, evicting the least-recently-used entry when over capacity. */
    public synchronized void put(String username, CustomSkinProperty property) {
        if (!isEnabled()) return;
        if (username == null || property == null) return;
        String key = username.toLowerCase();
        if (entries.containsKey(key)) {
            entries.put(key, new CacheEntry(property, System.currentTimeMillis()));
            return;
        }
        // The map is in access order, so the head is the least-recently-used
        // entry and eviction is O(1) instead of an O(n) scan for the oldest.
        while (entries.size() >= maxEntries && !entries.isEmpty()) {
            Iterator<Map.Entry<String, CacheEntry>> it = entries.entrySet().iterator();
            it.next();
            it.remove();
        }
        entries.put(key, new CacheEntry(property, System.currentTimeMillis()));
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }
}
