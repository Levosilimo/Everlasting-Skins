package levosilimo.everlastingskins.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SkinMetricsTest {

    private final SkinMetrics metrics = new SkinMetrics();

    private static SkinMetrics freshMetrics() {
        return new SkinMetrics();
    }

    @Test
    @DisplayName("recordRefreshStarted increments the initiated counter")
    void recordRefreshStarted_incrementsCounter() {
        SkinMetrics m = freshMetrics();
        UUID uuid = UUID.randomUUID();

        m.recordRefreshStarted(uuid);
        m.recordRefreshStarted(uuid);

        assertEquals(2, m.snapshot().refreshesInitiated());
        assertEquals(0, m.snapshot().refreshesCompleted());
    }

    @Test
    @DisplayName("recordRefreshCompleted populates histograms and per-player state")
    void recordRefreshCompleted_populatesAllHistograms() {
        SkinMetrics m = freshMetrics();
        UUID uuid = UUID.randomUUID();

        m.recordRefreshCompleted(uuid, System.nanoTime(), 1_000_000L, 200_000L, 500_000L);

        SkinMetrics.Snapshot s = m.snapshot();
        assertEquals(1, s.refreshesCompleted());
        assertTrue(s.fetchPercentiles().get("p50") >= 1_000);
        assertTrue(s.saveEnqueuePercentiles().get("p50") >= 100);
        assertTrue(s.broadcastPercentiles().get("p50") >= 500);
        assertTrue(s.totalPercentiles().get("p50") >= 0);
        SkinMetrics.PlayerSnapshot ps = s.perPlayer().get(uuid);
        assertNotNull(ps);
        assertEquals(1, ps.refreshCount());
        assertTrue(ps.lastRefreshAtMs() > 0);
    }

    @Test
    @DisplayName("histogram percentiles land on the correct buckets")
    void histogramPercentiles_correctBuckets() {
        // Buckets: 1, 5, 10, 50, 100, 500us ... (see LATENCY_BUCKETS_US).
        // Record 10 samples of 100us each and 10 samples of 500us each.
        SkinMetrics m = freshMetrics();
        UUID uuid = UUID.randomUUID();
        long start = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            m.recordRefreshCompleted(uuid, start, 100_000L, 0, 0);
        }
        for (int i = 0; i < 10; i++) {
            m.recordRefreshCompleted(uuid, start, 500_000L, 0, 0);
        }

        Map<String, Long> p = m.snapshot().fetchPercentiles();
        // 20 samples: p50 crosses at the 10th sample (100us bucket = 100),
        // p95/p99 cross inside the 500us bucket (= 500).
        assertEquals(100L, p.get("p50"));
        assertEquals(500L, p.get("p95"));
        assertEquals(500L, p.get("p99"));
        assertEquals(500L, p.get("max"));
    }

    @Test
    @DisplayName("snapshot is readable under concurrent recording")
    void snapshot_isThreadSafe() {
        SkinMetrics m = freshMetrics();
        UUID uuid = UUID.randomUUID();
        Thread[] threads = new Thread[8];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 1_000; i++) {
                    m.recordRefreshStarted(uuid);
                    m.recordRefreshCompleted(uuid, System.nanoTime(), 50_000L, 0, 0);
                    m.snapshot();
                }
            });
            threads[t].start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while joining recorder threads");
            }
        }
        SkinMetrics.Snapshot s = m.snapshot();
        assertEquals(8_000, s.refreshesInitiated());
        assertEquals(8_000, s.refreshesCompleted());
    }

    @Test
    @DisplayName("skipped/debounced/rate-limited use separate counters")
    void recordRefreshSkippedDebouncedRateLimited_separateCounters() {
        SkinMetrics m = freshMetrics();
        UUID uuid = UUID.randomUUID();

        m.recordRefreshSkipped(uuid);
        m.recordRefreshSkipped(uuid);
        m.recordRefreshDebounced(uuid);
        m.recordRateLimited(uuid);

        SkinMetrics.Snapshot s = m.snapshot();
        assertEquals(2, s.refreshesSkipped());
        assertEquals(1, s.refreshesDebounced());
        assertEquals(1, s.refreshesRateLimited());
        assertEquals(0, s.refreshesCompleted());
        assertEquals(0, s.refreshesFailed());
    }

    @Test
    @DisplayName("reset zeroes all counters and histograms")
    void reset_zerosAllCountersAndHistograms() {
        SkinMetrics m = freshMetrics();
        UUID uuid = UUID.randomUUID();

        m.recordRefreshStarted(uuid);
        m.recordRefreshCompleted(uuid, System.nanoTime(), 1_000_000L, 200_000L, 500_000L);
        m.recordRefreshFailed(uuid);
        m.recordRefreshSkipped(uuid);
        m.recordRefreshDebounced(uuid);
        m.recordRateLimited(uuid);
        m.recordBroadcast(1024);
        m.recordSaveSubmitted();
        m.recordSaveCompleted();
        m.recordSaveEnqueueLatency(100_000L);
        m.recordSaveDiskLatency(TimeUnit.MILLISECONDS.toNanos(50));
        m.recordIoFailure();
        m.recordPlayerJoined();
        m.recordNetworkDelta(512, 128);

        m.reset();
        SkinMetrics.Snapshot s = m.snapshot();
        assertEquals(0, s.refreshesInitiated());
        assertEquals(0, s.refreshesCompleted());
        assertEquals(0, s.refreshesFailed());
        assertEquals(0, s.refreshesSkipped());
        assertEquals(0, s.refreshesDebounced());
        assertEquals(0, s.refreshesRateLimited());
        assertEquals(0, s.broadcastsSent());
        assertEquals(0, s.bytesWritten());
        assertEquals(0, s.savesSubmitted());
        assertEquals(0, s.savesCompleted());
        assertEquals(0, s.ioFailures());
        assertEquals(0, s.pendingAsyncWrites());
        assertEquals(0, s.onlinePlayers());
        assertEquals(0, s.netBytesWrittenOut());
        assertEquals(0, s.netBytesReadIn());
        assertEquals(0, s.fetchPercentiles().get("max"));
        assertEquals(0, s.saveEnqueuePercentiles().get("max"));
        assertEquals(0, s.saveDiskPercentiles().get("max"));
        assertEquals(0, s.broadcastPercentiles().get("max"));
        assertEquals(0, s.totalPercentiles().get("max"));
        assertTrue(s.perPlayer().isEmpty());
    }
}
