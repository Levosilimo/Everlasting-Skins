package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Small TTL + cap cache for Mojang profile lookups. Keyed by lower-case
 * username; a hit avoids the (slow, rate-limited) Mojang HTTP chain entirely.
 * Zero external dependencies.
 */
public class MojangProfileCache {

    private record CacheEntry(CustomSkinProperty property, long fetchedAtMs) {
    }

    private final ConcurrentHashMap<String, CacheEntry> entries = new ConcurrentHashMap<>();
    private final long ttlMs;
    private final int maxEntries;

    public MojangProfileCache() {
        this(TimeUnit.HOURS.toMillis(1), 1000);
    }

    public MojangProfileCache(long ttlMs, int maxEntries) {
        this.ttlMs = ttlMs;
        this.maxEntries = maxEntries;
    }

    /** Returns the cached skin for the username, or null when absent/expired. */
    public CustomSkinProperty get(String username) {
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

    /** Stores a fetched skin, evicting the oldest entry when over capacity. */
    public void put(String username, CustomSkinProperty property) {
        if (username == null || property == null) return;
        String key = username.toLowerCase();
        if (entries.containsKey(key)) {
            entries.put(key, new CacheEntry(property, System.currentTimeMillis()));
            return;
        }
        while (entries.size() >= maxEntries && !entries.isEmpty()) {
            entries.entrySet().stream()
                    .min((a, b) -> Long.compare(a.getValue().fetchedAtMs(), b.getValue().fetchedAtMs()))
                    .ifPresent(oldest -> entries.remove(oldest.getKey(), oldest.getValue()));
        }
        entries.put(key, new CacheEntry(property, System.currentTimeMillis()));
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
