package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.metrics.Snapshot;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
