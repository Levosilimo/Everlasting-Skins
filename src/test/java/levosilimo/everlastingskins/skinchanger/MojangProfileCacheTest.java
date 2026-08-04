/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.permission.TestConfigSupport;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.BeforeAll;
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

    @BeforeAll
    static void loadConfig() {
        // ForgeConfigSpec values are only readable after the spec is loaded;
        // this class must not depend on other test classes having run first.
        TestConfigSupport.loadDefaults();
    }

    @Test
    @DisplayName("a cached entry is returned immediately")
    void getHit_returnsImmediately() {
        MojangProfileCache cache = new MojangProfileCache();
        cache.put(USERNAME, SKIN);

        CustomSkinProperty hit = cache.get(USERNAME);
        assertNotNull(hit);
        assertEquals("value", hit.getOriginalProperty().value());
    }

    @Test
    @DisplayName("an expired entry returns null")
    void getExpiredEntry_returnsNull() {
        MojangProfileCache cache = new MojangProfileCache(10, 100);
        cache.put(USERNAME, SKIN);

        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted");
        }
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

        SkinMetrics.Snapshot s = SkinMetrics.INSTANCE.snapshot();
        assertEquals(2, s.cacheHits());
        assertEquals(1, s.cacheMisses());
    }

    @Test
    @DisplayName("a cache disabled via config never serves or stores entries")
    void disabledCache_returnsNull() {
        boolean original = Config.MOJANG_CACHE_ENABLED.get();
        try {
            Config.MOJANG_CACHE_ENABLED.set(false);
            MojangProfileCache cache = new MojangProfileCache();
            cache.put(USERNAME, SKIN);

            assertFalse(cache.isEnabled());
            assertNull(cache.get(USERNAME));
            assertEquals(0, cache.size());
        } finally {
            Config.MOJANG_CACHE_ENABLED.set(original);
        }
    }

    @Test
    @DisplayName("a zero TTL expires every entry immediately")
    void zeroTtl_expiresImmediately() {
        MojangProfileCache cache = new MojangProfileCache(0, 100);
        cache.put(USERNAME, SKIN);

        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted");
        }
        assertNull(cache.get(USERNAME));
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("the configured max size is honored by the default constructor")
    void configuredMaxSize_isHonored() {
        int original = Config.MOJANG_CACHE_MAX_SIZE.get();
        try {
            Config.MOJANG_CACHE_MAX_SIZE.set(1);
            MojangProfileCache cache = new MojangProfileCache();
            cache.put("a", SKIN);
            cache.put("b", SKIN);

            assertEquals(1, cache.size());
        } finally {
            Config.MOJANG_CACHE_MAX_SIZE.set(original);
        }
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

    @Test
    @DisplayName("stress: 5000 puts into a 1000-entry cache never exceed the cap")
    void stress_neverExceedsCap() {
        MojangProfileCache cache = new MojangProfileCache(TimeUnit.HOURS.toMillis(1), 1000);
        for (int i = 0; i < 5000; i++) {
            cache.put("stress-user-" + i, SKIN);
            if (cache.size() > 1000) {
                fail("cache exceeded 1000 entries after put #" + i);
            }
        }
        assertEquals(1000, cache.size());
        // untouched entries age out in insertion order: newest survives, oldest is gone
        assertNotNull(cache.get("stress-user-4999"));
        assertNull(cache.get("stress-user-0"));
    }

    @Test
    @DisplayName("eviction stays O(1) per put: 2000 evictions from a full 200k cache")
    void evictionCost_isConstantTime() {
        int capacity = 200_000;
        int evictions = 2_000;
        MojangProfileCache cache = new MojangProfileCache(TimeUnit.HOURS.toMillis(1), capacity);
        for (int i = 0; i < capacity; i++) {
            cache.put("warm-" + i, SKIN);   // fill phase also JIT-warms the eviction path
        }

        long start = System.nanoTime();
        for (int i = 0; i < evictions; i++) {
            cache.put("evict-" + i, SKIN);
        }
        long largeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // The old O(n) stream-min scan over 200k entries per put would take
        // tens of seconds; O(1) head eviction takes a few ms. The thresholds
        // are deliberately loose so slow CI machines do not flake.
        assertTrue(largeMs < 5_000, "2000 puts into a full " + capacity + " cache took " + largeMs + "ms");

        MojangProfileCache small = new MojangProfileCache(TimeUnit.HOURS.toMillis(1), 100);
        for (int i = 0; i < 100; i++) {
            small.put("s-" + i, SKIN);
        }
        long smallStart = System.nanoTime();
        for (int i = 0; i < evictions; i++) {
            small.put("sev-" + i, SKIN);
        }
        long smallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - smallStart);
        assertTrue(largeMs <= smallMs * 50 + 500,
                "eviction cost grew with capacity: large " + largeMs + "ms vs small " + smallMs + "ms");
        assertEquals(capacity, cache.size());
    }
}
