package levosilimo.everlastingskins.metrics;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * In-process metrics for the EverlastingSkins /skin refresh flow.
 * Zero external dependencies, lock-free writes from any thread.
 *
 * Latency tracking uses fixed buckets (not HdrHistogram):
 * 1us, 5us, 10us, 50us, 100us, 500us, 1ms, 5ms, 10ms, 50ms, 100ms,
 * 250ms, 500ms, 1s, 5s, 10s, plus an overflow bucket for everything above.
 * p50/p95/p99 are computed by walking the cumulative distribution.
 */
public final class SkinMetrics {

    private static final long[] LATENCY_BUCKETS_US = {
            1, 5, 10, 50, 100, 500, 1_000, 5_000, 10_000, 50_000,
            100_000, 250_000, 500_000, 1_000_000, 5_000_000, 10_000_000
    };

    public static final SkinMetrics INSTANCE = new SkinMetrics();

    private final LongAdder refreshesInitiated = new LongAdder();
    private final LongAdder refreshesCompleted = new LongAdder();
    private final LongAdder refreshesFailed = new LongAdder();
    private final LongAdder refreshesDebounced = new LongAdder();
    private final LongAdder refreshesSkipped = new LongAdder();
    private final LongAdder refreshesRateLimited = new LongAdder();
    private final LongAdder broadcastsSent = new LongAdder();
    private final LongAdder bytesWritten = new LongAdder();
    private final LongAdder savesSubmitted = new LongAdder();
    private final LongAdder savesCompleted = new LongAdder();
    private final LongAdder ioFailures = new LongAdder();
    private final LongAdder netBytesWrittenOut = new LongAdder();
    private final LongAdder netBytesReadIn = new LongAdder();

    private final LongAdder[] fetchLatencyBuckets = new LongAdder[LATENCY_BUCKETS_US.length + 1];
    private final LongAdder[] saveEnqueueLatencyBuckets = new LongAdder[LATENCY_BUCKETS_US.length + 1];
    private final LongAdder[] saveDiskLatencyBuckets = new LongAdder[LATENCY_BUCKETS_US.length + 1];
    private final LongAdder[] broadcastLatencyBuckets = new LongAdder[LATENCY_BUCKETS_US.length + 1];
    private final LongAdder[] totalLatencyBuckets = new LongAdder[LATENCY_BUCKETS_US.length + 1];

    private final AtomicInteger pendingAsyncWrites = new AtomicInteger();
    private final LongAdder onlinePlayers = new LongAdder();
    private final AtomicLong startedAtMs = new AtomicLong(System.currentTimeMillis());

    private final ConcurrentHashMap<UUID, PlayerMetrics> perPlayer = new ConcurrentHashMap<>();

    /** Package-private for tests; production uses {@link #INSTANCE}. */
    SkinMetrics() {
        for (int i = 0; i <= LATENCY_BUCKETS_US.length; i++) {
            fetchLatencyBuckets[i] = new LongAdder();
            saveEnqueueLatencyBuckets[i] = new LongAdder();
            saveDiskLatencyBuckets[i] = new LongAdder();
            broadcastLatencyBuckets[i] = new LongAdder();
            totalLatencyBuckets[i] = new LongAdder();
        }
    }

    public void recordRefreshStarted(UUID player) {
        refreshesInitiated.increment();
    }

    /**
     * Records a completed refresh. fetch/save/broadcast latencies are recorded
     * into their own histograms when non-zero (the save and broadcast phases
     * run asynchronously from the command completion, so callers pass what
     * they measured); total latency is always recorded from startNanos.
     */
    public void recordRefreshCompleted(UUID player, long startNanos, long fetchNanos, long saveNanos, long broadcastNanos) {
        refreshesCompleted.increment();
        PlayerMetrics pm = perPlayer.computeIfAbsent(player, k -> new PlayerMetrics());
        pm.refreshCount.increment();
        pm.lastRefreshAtMs.set(System.currentTimeMillis());
        recordLatency(fetchLatencyBuckets, fetchNanos);
        recordLatency(saveEnqueueLatencyBuckets, saveNanos);
        recordLatency(broadcastLatencyBuckets, broadcastNanos);
        recordLatency(totalLatencyBuckets, System.nanoTime() - startNanos);
    }

    public void recordRefreshFailed(UUID player) {
        refreshesFailed.increment();
    }

    public void recordRefreshDebounced(UUID player) {
        refreshesDebounced.increment();
    }

    public void recordRefreshSkipped(UUID player) {
        refreshesSkipped.increment();
    }

    public void recordRateLimited(UUID player) {
        refreshesRateLimited.increment();
    }

    public void recordBroadcast(long bytes) {
        broadcastsSent.increment();
        bytesWritten.add(bytes);
    }

    /** Time between submit and the writer thread picking the write up. */
    public void recordSaveEnqueueLatency(long nanos) {
        recordLatency(saveEnqueueLatencyBuckets, nanos);
    }

    /** Actual disk write (write + fsync + atomic rename), measured on the writer thread. */
    public void recordSaveDiskLatency(long nanos) {
        recordLatency(saveDiskLatencyBuckets, nanos);
    }

    public void recordBroadcastLatency(long nanos) {
        recordLatency(broadcastLatencyBuckets, nanos);
    }

    /** Consumes the per-connection byte deltas measured around a refresh. */
    public void recordNetworkDelta(long outDelta, long inDelta) {
        if (outDelta > 0) netBytesWrittenOut.add(outDelta);
        if (inDelta > 0) netBytesReadIn.add(inDelta);
    }

    /** Records the full task() duration into the total latency histogram. */
    public void recordTotalLatency(long nanos) {
        recordLatency(totalLatencyBuckets, nanos);
    }

    public void recordSaveSubmitted() {
        savesSubmitted.increment();
        pendingAsyncWrites.incrementAndGet();
    }

    public void recordSaveCompleted() {
        savesCompleted.increment();
        pendingAsyncWrites.decrementAndGet();
    }

    public void recordIoFailure() {
        ioFailures.increment();
    }

    public void recordPlayerJoined() {
        onlinePlayers.increment();
    }

    public void recordPlayerLeft() {
        onlinePlayers.decrement();
    }

    /** Zeroes every counter and clears per-player state. */
    public void reset() {
        refreshesInitiated.reset();
        refreshesCompleted.reset();
        refreshesFailed.reset();
        refreshesDebounced.reset();
        refreshesSkipped.reset();
        refreshesRateLimited.reset();
        broadcastsSent.reset();
        bytesWritten.reset();
        savesSubmitted.reset();
        savesCompleted.reset();
        ioFailures.reset();
        netBytesWrittenOut.reset();
        netBytesReadIn.reset();
        for (int i = 0; i <= LATENCY_BUCKETS_US.length; i++) {
            fetchLatencyBuckets[i].reset();
            saveEnqueueLatencyBuckets[i].reset();
            saveDiskLatencyBuckets[i].reset();
            broadcastLatencyBuckets[i].reset();
            totalLatencyBuckets[i].reset();
        }
        pendingAsyncWrites.set(0);
        onlinePlayers.reset();
        perPlayer.clear();
        startedAtMs.set(System.currentTimeMillis());
    }

    private static void recordLatency(LongAdder[] buckets, long nanos) {
        if (nanos <= 0) return;
        long us = Math.max(1, nanos / 1000);
        int idx = Arrays.binarySearch(LATENCY_BUCKETS_US, us);
        if (idx < 0) idx = -idx - 1;
        buckets[idx].increment();
    }

    /**
     * Computes p50/p95/p99 (in microseconds) and the highest non-empty bucket
     * bound by walking the cumulative distribution. Values land on the bucket
     * bound they fall into; samples beyond the last bound report as
     * {@code maxBound} (the 10s+ overflow bucket).
     */
    static Map<String, Long> histogramPercentiles(LongAdder[] buckets, long[] bucketBoundsUs) {
        Map<String, Long> result = new LinkedHashMap<>();
        long total = 0;
        for (LongAdder bucket : buckets) {
            total += bucket.sum();
        }
        if (total == 0) {
            result.put("p50", 0L);
            result.put("p95", 0L);
            result.put("p99", 0L);
            result.put("max", 0L);
            return result;
        }
        long cumulative = 0;
        long maxBound = 0;
        long p50 = 0;
        long p95 = 0;
        long p99 = 0;
        long p50Target = Math.round(total * 0.50);
        long p95Target = Math.round(total * 0.95);
        long p99Target = Math.round(total * 0.99);
        for (int i = 0; i < buckets.length; i++) {
            long count = buckets[i].sum();
            if (count == 0) continue;
            cumulative += count;
            long bound = i < bucketBoundsUs.length ? bucketBoundsUs[i] : bucketBoundsUs[bucketBoundsUs.length - 1];
            maxBound = bound;
            if (p50 == 0 && cumulative >= p50Target) p50 = bound;
            if (p95 == 0 && cumulative >= p95Target) p95 = bound;
            if (p99 == 0 && cumulative >= p99Target) p99 = bound;
        }
        result.put("p50", p50);
        result.put("p95", p95);
        result.put("p99", p99);
        result.put("max", maxBound);
        return result;
    }

    public Snapshot snapshot() {
        return new Snapshot(
                refreshesInitiated.sum(), refreshesCompleted.sum(), refreshesFailed.sum(),
                refreshesDebounced.sum(), refreshesSkipped.sum(), refreshesRateLimited.sum(),
                broadcastsSent.sum(), bytesWritten.sum(), savesSubmitted.sum(), savesCompleted.sum(),
                ioFailures.sum(), pendingAsyncWrites.get(), onlinePlayers.sum(),
                System.currentTimeMillis() - startedAtMs.get(),
                netBytesWrittenOut.sum(), netBytesReadIn.sum(),
                histogramPercentiles(fetchLatencyBuckets, LATENCY_BUCKETS_US),
                histogramPercentiles(saveEnqueueLatencyBuckets, LATENCY_BUCKETS_US),
                histogramPercentiles(saveDiskLatencyBuckets, LATENCY_BUCKETS_US),
                histogramPercentiles(broadcastLatencyBuckets, LATENCY_BUCKETS_US),
                histogramPercentiles(totalLatencyBuckets, LATENCY_BUCKETS_US),
                snapshotPerPlayer());
    }

    /** Per-player refresh counts, most active first. */
    public List<Map.Entry<UUID, PlayerSnapshot>> topPlayers(int limit) {
        return snapshotPerPlayer().entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<UUID, PlayerSnapshot> e) -> e.getValue().refreshCount()).reversed())
                .limit(limit)
                .toList();
    }

    private Map<UUID, PlayerSnapshot> snapshotPerPlayer() {
        Map<UUID, PlayerSnapshot> map = new LinkedHashMap<>();
        perPlayer.forEach((uuid, pm) -> map.put(uuid, new PlayerSnapshot(pm.refreshCount.sum(), pm.lastRefreshAtMs.get())));
        return map;
    }

    /** Formats the snapshot as the human-readable /skin metrics output. */
    public String formatHuman(Snapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("EverlastingSkins Metrics (uptime ").append(formatUptime(s.uptimeMs())).append(")\n");
        sb.append("  refreshes: ").append(s.refreshesInitiated()).append(" initiated, ")
                .append(s.refreshesCompleted()).append(" completed, ")
                .append(s.refreshesFailed()).append(" failed\n");
        sb.append("  skipped: ").append(s.refreshesSkipped()).append(" (identical), debounced: ")
                .append(s.refreshesDebounced()).append(", rate-limited: ").append(s.refreshesRateLimited()).append('\n');
        sb.append("  broadcasts: ").append(s.broadcastsSent()).append(" (").append(formatBytes(s.bytesWritten())).append(" written)\n");
        sb.append("  saves: ").append(s.savesSubmitted()).append(" submitted, ").append(s.savesCompleted())
                .append(" completed, ").append(s.ioFailures()).append(" failed, ")
                .append(s.pendingAsyncWrites()).append(" pending\n");
        sb.append("  network: ").append(formatBytes(s.netBytesWrittenOut())).append(" out, ")
                .append(formatBytes(s.netBytesReadIn())).append(" in (per-connection)\n");
        sb.append("  online players: ").append(s.onlinePlayers()).append('\n');
        sb.append("  latencies (ms):\n");
        sb.append("    fetch:     ").append(formatPercentiles(s.fetchPercentiles())).append('\n');
        sb.append("    save enq:  ").append(formatPercentiles(s.saveEnqueuePercentiles())).append('\n');
        sb.append("    save disk: ").append(formatPercentiles(s.saveDiskPercentiles())).append('\n');
        sb.append("    broadcast: ").append(formatPercentiles(s.broadcastPercentiles())).append('\n');
        sb.append("    total:     ").append(formatPercentiles(s.totalPercentiles()));
        return sb.toString();
    }

    /** Formats the snapshot as a single JSON object. */
    public String formatJson(Snapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"uptimeMs\":").append(s.uptimeMs());
        sb.append(",\"refreshes\":{\"initiated\":").append(s.refreshesInitiated())
                .append(",\"completed\":").append(s.refreshesCompleted())
                .append(",\"failed\":").append(s.refreshesFailed())
                .append(",\"skipped\":").append(s.refreshesSkipped())
                .append(",\"debounced\":").append(s.refreshesDebounced())
                .append(",\"rateLimited\":").append(s.refreshesRateLimited()).append('}');
        sb.append(",\"broadcasts\":{\"sent\":").append(s.broadcastsSent())
                .append(",\"bytesWritten\":").append(s.bytesWritten()).append('}');
        sb.append(",\"saves\":{\"submitted\":").append(s.savesSubmitted())
                .append(",\"completed\":").append(s.savesCompleted())
                .append(",\"ioFailures\":").append(s.ioFailures())
                .append(",\"pending\":").append(s.pendingAsyncWrites()).append('}');
        sb.append(",\"networkBytes\":{\"out\":").append(s.netBytesWrittenOut())
                .append(",\"in\":").append(s.netBytesReadIn()).append('}');
        sb.append(",\"onlinePlayers\":").append(s.onlinePlayers());
        sb.append(",\"latenciesMs\":{")
                .append("\"fetch\":").append(jsonPercentiles(s.fetchPercentiles()))
                .append(",\"saveEnqueue\":").append(jsonPercentiles(s.saveEnqueuePercentiles()))
                .append(",\"saveDisk\":").append(jsonPercentiles(s.saveDiskPercentiles()))
                .append(",\"broadcast\":").append(jsonPercentiles(s.broadcastPercentiles()))
                .append(",\"total\":").append(jsonPercentiles(s.totalPercentiles())).append('}');
        sb.append(",\"players\":[");
        boolean first = true;
        for (Map.Entry<UUID, PlayerSnapshot> e : s.perPlayer().entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"uuid\":\"").append(e.getKey()).append("\",\"refreshCount\":")
                    .append(e.getValue().refreshCount()).append(",\"lastRefreshAtMs\":")
                    .append(e.getValue().lastRefreshAtMs()).append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String jsonPercentiles(Map<String, Long> p) {
        return "{\"p50\":" + p.get("p50") + ",\"p95\":" + p.get("p95")
                + ",\"p99\":" + p.get("p99") + ",\"max\":" + p.get("max") + '}';
    }

    private static String formatPercentiles(Map<String, Long> p) {
        return "p50=" + usToMs(p.get("p50")) + ", p95=" + usToMs(p.get("p95"))
                + ", p99=" + usToMs(p.get("p99")) + ", max=" + usToMs(p.get("max"));
    }

    private static long usToMs(long us) {
        return us / 1000;
    }

    private static String formatUptime(long ms) {
        long totalMin = ms / 60_000;
        long hours = totalMin / 60;
        long minutes = totalMin % 60;
        return hours + "h " + minutes + "m";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }

    public record Snapshot(
            long refreshesInitiated, long refreshesCompleted, long refreshesFailed,
            long refreshesDebounced, long refreshesSkipped, long refreshesRateLimited,
            long broadcastsSent, long bytesWritten, long savesSubmitted, long savesCompleted,
            long ioFailures, int pendingAsyncWrites, long onlinePlayers, long uptimeMs,
            long netBytesWrittenOut, long netBytesReadIn,
            Map<String, Long> fetchPercentiles, Map<String, Long> saveEnqueuePercentiles,
            Map<String, Long> saveDiskPercentiles, Map<String, Long> broadcastPercentiles,
            Map<String, Long> totalPercentiles,
            Map<UUID, PlayerSnapshot> perPlayer) {
    }

    public record PlayerSnapshot(long refreshCount, long lastRefreshAtMs) {
    }

    static final class PlayerMetrics {
        final LongAdder refreshCount = new LongAdder();
        final AtomicLong lastRefreshAtMs = new AtomicLong();
    }
}
