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
 *
 * <p>Eviction: the backing map is a {@link LinkedHashMap} in access order
 * (LRU). Reads and writes reorder entries; when the cache is at capacity the
 * entry at the head (least recently used) is removed in O(1) — no full-map
 * scan. Expired entries that reach the head are dropped lazily by
 * {@link #put(String, CustomSkinProperty)} before capacity eviction and by
 * {@link #get(String)} on access.
 *
 * <p>Thread-safety: every state mutation runs under the cache instance's
 * monitor, so a put, its capacity eviction and its expiry sweep form one
 * atomic section; there is no TOCTOU window between deciding an entry is
 * stale and removing it. A zero or negative {@code maxEntries} stores
 * nothing.
 */
public class MojangProfileCache {

    private static final class CacheEntry {
        final CustomSkinProperty property;
        final long fetchedAtMs;

        CacheEntry(CustomSkinProperty property, long fetchedAtMs) {
            this.property = property;
            this.fetchedAtMs = fetchedAtMs;
        }
    }

    private final LinkedHashMap<String, CacheEntry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final long ttlMs;
    private final int maxEntries;

    public MojangProfileCache() {
        this(Config.mojangProfileCacheTtlMs, Config.mojangProfileCacheMaxSize);
    }

    public MojangProfileCache(long ttlMs, int maxEntries) {
        this.ttlMs = ttlMs;
        this.maxEntries = maxEntries;
    }

    /** Returns the cached skin for the username, or null when absent/expired. */
    public synchronized CustomSkinProperty get(String username) {
        if (username == null) return null;
        String key = username.toLowerCase();
        CacheEntry entry = entries.get(key);
        if (entry == null) {
            SkinMetrics.INSTANCE.recordCacheMiss();
            return null;
        }
        if (System.currentTimeMillis() - entry.fetchedAtMs > ttlMs) {
            entries.remove(key, entry);
            SkinMetrics.INSTANCE.recordCacheMiss();
            return null;
        }
        SkinMetrics.INSTANCE.recordCacheHit();
        return entry.property;
    }

    /** Stores a fetched skin, evicting expired and least-recently-used entries when over capacity. */
    public synchronized void put(String username, CustomSkinProperty property) {
        if (username == null || property == null) return;
        String key = username.toLowerCase();
        if (maxEntries <= 0) return;
        if (entries.containsKey(key)) {
            entries.put(key, new CacheEntry(property, System.currentTimeMillis()));
            return;
        }
        evictExpired();
        while (entries.size() >= maxEntries) {
            evictOldest();
        }
        entries.put(key, new CacheEntry(property, System.currentTimeMillis()));
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }

    /**
     * Drops expired entries that have reached the head of the access-order
     * map. The walk stops at the first fresh entry, so it costs O(1) when
     * nothing is expired and amortized O(1) per dropped entry otherwise;
     * entries that expire while buried are cleaned on their next access.
     */
    private void evictExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, CacheEntry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            CacheEntry entry = it.next().getValue();
            if (now - entry.fetchedAtMs <= ttlMs) {
                break;
            }
            it.remove();
        }
    }

    /** Removes the least-recently-used entry (the map head) in O(1). */
    private void evictOldest() {
        Iterator<Map.Entry<String, CacheEntry>> it = entries.entrySet().iterator();
        it.next();
        it.remove();
    }
}
