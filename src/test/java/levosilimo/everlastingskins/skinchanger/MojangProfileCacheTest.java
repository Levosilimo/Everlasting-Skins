/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.metrics.Snapshot;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MojangProfileCacheTest {

    private static final String USERNAME = "Notch";
    private static final CustomSkinProperty SKIN = new CustomSkinProperty("textures", "value", "sig", "MojangAPI");

    @Test
    @DisplayName("a cached entry is returned immediately")
    void getHit_returnsImmediately() {
        MojangProfileCache cache = new MojangProfileCache();
        cache.put(USERNAME, SKIN);

        CustomSkinProperty hit = cache.get(USERNAME);
        assertNotNull(hit);
        assertEquals("value", hit.getOriginalProperty().getValue());
    }

    @Test
    @DisplayName("an expired entry returns null")
    void getExpiredEntry_returnsNull() throws InterruptedException {
        MojangProfileCache cache = new MojangProfileCache(10, 100);
        cache.put(USERNAME, SKIN);

        Thread.sleep(20);

        assertNull(cache.get(USERNAME));
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("put then get round-trips a skin")
    void putAndGet_roundTrip() {
        MojangProfileCache cache = new MojangProfileCache(TimeUnit.HOURS.toMillis(1), 100);
        cache.put("steve", SKIN);

        assertNotNull(cache.get("steve"));
        assertNotNull(cache.get("STEVE"));  // case-insensitive key
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("cache respects its capacity, evicting the oldest entry")
    void capIsRespected() {
        MojangProfileCache cache = new MojangProfileCache(TimeUnit.HOURS.toMillis(1), 2);
        cache.put("a", SKIN);
        cache.put("b", SKIN);
        cache.put("c", SKIN);

        assertEquals(2, cache.size());
        assertNull(cache.get("a"));  // oldest evicted
        assertNotNull(cache.get("b"));
        assertNotNull(cache.get("c"));
    }

    @Test
    @DisplayName("hits and misses increment the metric counters")
    void hitIncrementsCounter() {
        SkinMetrics.INSTANCE.reset();
        MojangProfileCache cache = new MojangProfileCache();
        cache.put(USERNAME, SKIN);

        cache.get(USERNAME);   // hit
        cache.get(USERNAME);   // hit
        cache.get("missing");  // miss

        Snapshot s = SkinMetrics.INSTANCE.snapshot();
        assertEquals(2, s.cacheHits());
        assertEquals(1, s.cacheMisses());
    }

    @Test
    @DisplayName("the cache never exceeds its capacity on any put")
    void capNeverExceeded() {
        MojangProfileCache cache = new MojangProfileCache(TimeUnit.HOURS.toMillis(1), 25);
        for (int i = 0; i < 500; i++) {
            cache.put("user" + i, SKIN);
            assertTrue(cache.size() <= 25, "size exceeded cap after put #" + i);
        }
        assertEquals(25, cache.size());
    }

    @Test
    @DisplayName("eviction is monotone LRU: a hit promotes the entry and demotes the next victim")
    void lruEviction_isMonotone() {
        MojangProfileCache cache = new MojangProfileCache(TimeUnit.HOURS.toMillis(1), 3);
        cache.put("a", SKIN);
        cache.put("b", SKIN);
        cache.put("c", SKIN);

        assertNotNull(cache.get("a"));   // touch: a becomes most recently used

        cache.put("d", SKIN);            // capacity 3 -> evicts b (now LRU)
        cache.put("e", SKIN);            // evicts c (a and d are more recently used)
        cache.put("f", SKIN);            // evicts a (d and e are more recently used)

        assertNull(cache.get("b"));
        assertNull(cache.get("c"));
        assertNull(cache.get("a"));
        assertNotNull(cache.get("d"));
        assertNotNull(cache.get("e"));
        assertNotNull(cache.get("f"));
        assertEquals(3, cache.size());
    }

    @Test
    @DisplayName("a put refreshes an existing key, keeping it alive and moving it to the MRU end")
    void putExistingKey_refreshesEntry() {
        MojangProfileCache cache = new MojangProfileCache(TimeUnit.HOURS.toMillis(1), 2);
        cache.put("a", SKIN);
        cache.put("b", SKIN);
        cache.put("a", SKIN);            // refresh a
        cache.put("c", SKIN);            // evicts b, not a
        assertNotNull(cache.get("a"));
        assertNull(cache.get("b"));
        assertNotNull(cache.get("c"));
    }

    @Test
    @DisplayName("expired entries are evicted by the put sweep, not counted against capacity")
    void ttlExpiry_evictsEntriesOnPut() throws InterruptedException {
        MojangProfileCache cache = new MojangProfileCache(20, 100);
        cache.put("a", SKIN);
        Thread.sleep(40);
        cache.put("b", SKIN);            // sweep drops expired a
        assertEquals(1, cache.size());
        assertNull(cache.get("a"));
        assertNotNull(cache.get("b"));
    }
}
