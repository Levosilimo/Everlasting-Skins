/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
 *
 * <p>TTL: timestamps are taken with {@link System#nanoTime()}, which is
 * monotonic — it never jumps when the wall clock is adjusted (NTP, manual
 * changes) — and the millisecond TTL is converted to nanoseconds once at
 * construction.
 *
 * <p>Decoupled from the per-version mod config: settings are injected via
 * the constructor. The no-arg constructor applies the defaults the
 * per-version {@code Config} classes used (1 hour TTL, 1000 entries max);
 * consumers pass their own values when they differ.
 */
public class MojangProfileCache {

    private static final class CacheEntry {
        final CustomSkinProperty property;
        final long fetchedAtNanos;

        CacheEntry(CustomSkinProperty property, long fetchedAtNanos) {
            this.property = property;
            this.fetchedAtNanos = fetchedAtNanos;
        }
    }

    /** Default TTL (1 hour) — mirrors the per-version Config default. */
    private static final long DEFAULT_TTL_MS = TimeUnit.HOURS.toMillis(1);
    /** Default capacity — mirrors the per-version Config default. */
    private static final int DEFAULT_MAX_ENTRIES = 1000;

    private final LinkedHashMap<String, CacheEntry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final long ttlNanos;
    private final int maxEntries;

    public MojangProfileCache() {
        this(DEFAULT_TTL_MS, DEFAULT_MAX_ENTRIES);
    }

    public MojangProfileCache(long ttlMs, int maxEntries) {
        this.ttlNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, ttlMs));
        this.maxEntries = maxEntries;
    }

    /** Returns the cached skin for the username, or null when absent/expired. */
    public synchronized CustomSkinProperty get(String username) {
        if (username == null) return null;
        String key = username.toLowerCase(Locale.ROOT);
        CacheEntry entry = entries.get(key);
        if (entry == null) {
            SkinMetrics.INSTANCE.recordCacheMiss();
            return null;
        }
        if (System.nanoTime() - entry.fetchedAtNanos > ttlNanos) {
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
        String key = username.toLowerCase(Locale.ROOT);
        if (maxEntries <= 0) return;
        if (entries.containsKey(key)) {
            entries.put(key, new CacheEntry(property, System.nanoTime()));
            return;
        }
        evictExpired();
        while (entries.size() >= maxEntries) {
            evictOldest();
        }
        entries.put(key, new CacheEntry(property, System.nanoTime()));
    }

    public synchronized int size() {
        return entries.size();
    }

    /**
     * Snapshot of the cached usernames (lower-case keys), most recently used
     * first. The copy is unmodifiable, so callers cannot mutate cache state;
     * the size is inherently capped at the configured max entries.
     */
    public synchronized List<String> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(entries.keySet()));
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
        long now = System.nanoTime();
        Iterator<Map.Entry<String, CacheEntry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            CacheEntry entry = it.next().getValue();
            if (now - entry.fetchedAtNanos <= ttlNanos) {
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
