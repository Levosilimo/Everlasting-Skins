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

    /** A task phase exceeding this threshold counts as a tick spike. */
    private static final long TICK_SPIKE_THRESHOLD_NANOS = 50_000_000;

    public static final SkinMetrics INSTANCE = new SkinMetrics();

    private final LongAdder refreshesInitiated = new LongAdder();
    private final LongAdder refreshesCompleted = new LongAdder();
    private final LongAdder refreshesFailed = new LongAdder();
    private final LongAdder refreshesTimedOut = new LongAdder();
    private final LongAdder refreshesDebounced = new LongAdder();
    private final LongAdder refreshesSkipped = new LongAdder();
    private final LongAdder refreshesSkippedStored = new LongAdder();
    private final LongAdder refreshesRateLimited = new LongAdder();
    private final LongAdder broadcastsSent = new LongAdder();
    private final LongAdder bytesWritten = new LongAdder();
    private final LongAdder savesSubmitted = new LongAdder();
    private final LongAdder savesCompleted = new LongAdder();
    private final LongAdder savesCoalesced = new LongAdder();
    private final LongAdder realWrites = new LongAdder();
    private final LongAdder ioFailures = new LongAdder();
    private final LongAdder netBytesWrittenOut = new LongAdder();
    private final LongAdder netBytesReadIn = new LongAdder();
    private final LongAdder tickSpikes = new LongAdder();
    private final LongAdder tickSpikesBroadcast = new LongAdder();
    private final LongAdder tickSpikesCascade = new LongAdder();
    private final LongAdder tickSpikesSaveEnqueue = new LongAdder();
    private final LongAdder providerHttp429 = new LongAdder();
    private final LongAdder providerHttp5xx = new LongAdder();
    private final LongAdder providerHttp4xxOther = new LongAdder();
    private final LongAdder providerExceptions = new LongAdder();
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();

    private final ConcurrentHashMap<String, LongAdder> ioFailuresByType = new ConcurrentHashMap<>();

    private final LongAdder[] fetchLatencyBuckets = new LongAdder[LATENCY_BUCKETS_US.length + 1];
    private final LongAdder[] saveEnqueueLatencyBuckets = new LongAdder[LATENCY_BUCKETS_US.length + 1];
    private final LongAdder[] saveDiskLatencyBuckets = new LongAdder[LATENCY_BUCKETS_US.length + 1];
    private final LongAdder[] broadcastLatencyBuckets = new LongAdder[LATENCY_BUCKETS_US.length + 1];
    private final LongAdder[] commandTotalLatencyBuckets = new LongAdder[LATENCY_BUCKETS_US.length + 1];
    private final LongAdder[] taskDurationLatencyBuckets = new LongAdder[LATENCY_BUCKETS_US.length + 1];
    private final LongAdder[] tickSpikeLatencyBuckets = new LongAdder[LATENCY_BUCKETS_US.length + 1];

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
            commandTotalLatencyBuckets[i] = new LongAdder();
            taskDurationLatencyBuckets[i] = new LongAdder();
            tickSpikeLatencyBuckets[i] = new LongAdder();
        }
    }

    public void recordRefreshStarted(UUID player) {
        refreshesInitiated.increment();
    }

    /**
     * Records a completed refresh. fetch/save/broadcast latencies are recorded
     * into their own histograms when non-zero (the save and broadcast phases
     * run asynchronously from the command completion, so callers pass what
     * they measured); the command-span latency (startNanos to now) is recorded
     * into the commandTotal histogram.
     */
    public void recordRefreshCompleted(UUID player, long startNanos, long fetchNanos, long saveNanos, long broadcastNanos) {
        refreshesCompleted.increment();
        PlayerMetrics pm = perPlayer.computeIfAbsent(player, k -> new PlayerMetrics());
        pm.refreshCount.increment();
        pm.lastRefreshAtMs.set(System.currentTimeMillis());
        recordLatency(fetchLatencyBuckets, fetchNanos);
        recordLatency(saveEnqueueLatencyBuckets, saveNanos);
        recordLatency(broadcastLatencyBuckets, broadcastNanos);
        recordLatency(commandTotalLatencyBuckets, System.nanoTime() - startNanos);
    }

    public void recordRefreshFailed(UUID player) {
        refreshesFailed.increment();
    }

    /** Fetch timed out (10s), distinct from a provider failure. */
    public void recordTimedOut(UUID player) {
        refreshesTimedOut.increment();
    }

    public void recordRefreshDebounced(UUID player) {
        refreshesDebounced.increment();
    }

    public void recordRefreshSkipped(UUID player) {
        refreshesSkipped.increment();
    }

    /** Skipped because the stored skin's source already matches the request. */
    public void recordRefreshSkippedStored(UUID player) {
        refreshesSkippedStored.increment();
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

    /** Records the full task() duration into the task-duration histogram. */
    public void recordTaskDuration(long nanos) {
        recordLatency(taskDurationLatencyBuckets, nanos);
    }

    /** Consumes the per-connection byte deltas measured around a refresh. */
    public void recordNetworkDelta(long outDelta, long inDelta) {
        if (outDelta > 0) netBytesWrittenOut.add(outDelta);
        if (inDelta > 0) netBytesReadIn.add(inDelta);
    }

    /** A task exceeded the 50ms spike threshold (generic count + histogram). */
    public void recordTickSpike(long nanos) {
        tickSpikes.increment();
        recordLatency(tickSpikeLatencyBuckets, nanos);
    }

    /** A broadcast-phase spike (the REMOVE + ADD_PLAYER fan-out). */
    public void recordSpikeBroadcast(long nanos) {
        if (nanos >= TICK_SPIKE_THRESHOLD_NANOS) tickSpikesBroadcast.increment();
    }

    /** A respawn-cascade-phase spike (per-target packet sequence). */
    public void recordSpikeCascade(long nanos) {
        if (nanos >= TICK_SPIKE_THRESHOLD_NANOS) tickSpikesCascade.increment();
    }

    /** A save-submit-phase spike (coalescing enqueue). */
    public void recordSpikeSaveEnqueue(long nanos) {
        if (nanos >= TICK_SPIKE_THRESHOLD_NANOS) tickSpikesSaveEnqueue.increment();
    }

    /** Provider HTTP status code observed by the HTTP layer. */
    public void recordProviderStatus(int statusCode) {
        if (statusCode == 429) {
            providerHttp429.increment();
        } else if (statusCode >= 500) {
            providerHttp5xx.increment();
        } else if (statusCode >= 400) {
            providerHttp4xxOther.increment();
        }
    }

    public void recordProviderException() {
        providerExceptions.increment();
    }

    public void recordCacheHit() {
        cacheHits.increment();
    }

    public void recordCacheMiss() {
        cacheMisses.increment();
    }

    public void recordSaveSubmitted() {
        savesSubmitted.increment();
        pendingAsyncWrites.incrementAndGet();
    }

    public void recordSaveCompleted() {
        savesCompleted.increment();
        pendingAsyncWrites.decrementAndGet();
    }

    /** A submission was superseded by a newer payload for the same UUID. */
    public void recordSaveCoalesced() {
        savesCoalesced.increment();
    }

    /** An actual disk write happened (write + fsync + rename). */
    public void recordRealWrite() {
        realWrites.increment();
    }

    public void recordIoFailure() {
        ioFailures.increment();
    }

    /** IOException with type attribution (NoSpaceLeft, AccessDenied, ...). */
    public void recordIoFailure(Throwable t) {
        ioFailures.increment();
        String type = t != null ? t.getClass().getSimpleName() : "unknown";
        ioFailuresByType.computeIfAbsent(type, k -> new LongAdder()).increment();
    }

    public Map<String, Long> ioFailuresByType() {
        Map<String, Long> map = new LinkedHashMap<>();
        ioFailuresByType.forEach((type, adder) -> map.put(type, adder.sum()));
        return map;
    }

    public void recordPlayerJoined() {
        onlinePlayers.increment();
    }

    public void recordPlayerLeft() {
        onlinePlayers.decrement();
    }

    /** Prunes per-player entries whose last refresh is older than the cutoff. */
    public int cleanupStalePlayers(long olderThanMs) {
        long cutoff = System.currentTimeMillis() - olderThanMs;
        int[] removed = {0};
        perPlayer.forEach((uuid, pm) -> {
            if (pm.lastRefreshAtMs.get() < cutoff) {
                if (perPlayer.remove(uuid, pm)) removed[0]++;
            }
        });
        return removed[0];
    }

    /** Zeroes every counter and clears per-player state. */
    public void reset() {
        refreshesInitiated.reset();
        refreshesCompleted.reset();
        refreshesFailed.reset();
        refreshesTimedOut.reset();
        refreshesDebounced.reset();
        refreshesSkipped.reset();
        refreshesSkippedStored.reset();
        refreshesRateLimited.reset();
        broadcastsSent.reset();
        bytesWritten.reset();
        savesSubmitted.reset();
        savesCompleted.reset();
        savesCoalesced.reset();
        realWrites.reset();
        ioFailures.reset();
        netBytesWrittenOut.reset();
        netBytesReadIn.reset();
        tickSpikes.reset();
        tickSpikesBroadcast.reset();
        tickSpikesCascade.reset();
        tickSpikesSaveEnqueue.reset();
        providerHttp429.reset();
        providerHttp5xx.reset();
        providerHttp4xxOther.reset();
        providerExceptions.reset();
        cacheHits.reset();
        cacheMisses.reset();
        ioFailuresByType.clear();
        for (int i = 0; i <= LATENCY_BUCKETS_US.length; i++) {
            fetchLatencyBuckets[i].reset();
            saveEnqueueLatencyBuckets[i].reset();
            saveDiskLatencyBuckets[i].reset();
            broadcastLatencyBuckets[i].reset();
            commandTotalLatencyBuckets[i].reset();
            taskDurationLatencyBuckets[i].reset();
            tickSpikeLatencyBuckets[i].reset();
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
                refreshesTimedOut.sum(), refreshesDebounced.sum(), refreshesSkipped.sum(),
                refreshesSkippedStored.sum(), refreshesRateLimited.sum(),
                broadcastsSent.sum(), bytesWritten.sum(), savesSubmitted.sum(), savesCompleted.sum(),
                savesCoalesced.sum(), realWrites.sum(),
                ioFailures.sum(), pendingAsyncWrites.get(), onlinePlayers.sum(),
                System.currentTimeMillis() - startedAtMs.get(),
                netBytesWrittenOut.sum(), netBytesReadIn.sum(),
                tickSpikes.sum(), tickSpikesBroadcast.sum(), tickSpikesCascade.sum(),
                tickSpikesSaveEnqueue.sum(),
                providerHttp429.sum(), providerHttp5xx.sum(), providerHttp4xxOther.sum(),
                providerExceptions.sum(), cacheHits.sum(), cacheMisses.sum(),
                ioFailuresByType(),
                histogramPercentiles(fetchLatencyBuckets, LATENCY_BUCKETS_US),
                histogramPercentiles(saveEnqueueLatencyBuckets, LATENCY_BUCKETS_US),
                histogramPercentiles(saveDiskLatencyBuckets, LATENCY_BUCKETS_US),
                histogramPercentiles(broadcastLatencyBuckets, LATENCY_BUCKETS_US),
                histogramPercentiles(commandTotalLatencyBuckets, LATENCY_BUCKETS_US),
                histogramPercentiles(taskDurationLatencyBuckets, LATENCY_BUCKETS_US),
                histogramPercentiles(tickSpikeLatencyBuckets, LATENCY_BUCKETS_US),
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
                .append(s.refreshesFailed()).append(" failed, ")
                .append(s.refreshesTimedOut()).append(" timed out\n");
        sb.append("  skipped: ").append(s.refreshesSkipped()).append(" (identical), ")
                .append(s.refreshesSkippedStored()).append(" (stored source), debounced: ")
                .append(s.refreshesDebounced()).append(", rate-limited: ").append(s.refreshesRateLimited()).append('\n');
        sb.append("  broadcasts: ").append(s.broadcastsSent()).append(" (").append(formatBytes(s.bytesWritten())).append(" written)\n");
        sb.append("  saves: ").append(s.savesSubmitted()).append(" submitted, ").append(s.savesCompleted())
                .append(" completed, ").append(s.savesCoalesced()).append(" coalesced, ")
                .append(s.realWrites()).append(" real writes, ").append(s.ioFailures()).append(" failed, ")
                .append(s.pendingAsyncWrites()).append(" pending\n");
        sb.append("  io failures by type: ").append(formatIoFailures(s.ioFailuresByType())).append('\n');
        sb.append("  network: ").append(formatBytes(s.netBytesWrittenOut())).append(" out, ")
                .append(formatBytes(s.netBytesReadIn())).append(" in (per-connection)\n");
        sb.append("  provider: ").append(s.providerHttp429()).append("x429, ")
                .append(s.providerHttp5xx()).append("x5xx, ")
                .append(s.providerHttp4xxOther()).append("x4xx, ")
                .append(s.providerExceptions()).append(" exceptions\n");
        sb.append("  cache: ").append(s.cacheHits()).append(" hits, ")
                .append(s.cacheMisses()).append(" misses\n");
        sb.append("  tick spikes: ").append(s.tickSpikes()).append(" (broadcast ")
                .append(s.tickSpikesBroadcast()).append(", cascade ").append(s.tickSpikesCascade())
                .append(", save-enqueue ").append(s.tickSpikesSaveEnqueue()).append(")\n");
        sb.append("  online players: ").append(s.onlinePlayers()).append('\n');
        sb.append("  latencies (ms):\n");
        sb.append("    fetch:        ").append(formatPercentiles(s.fetchPercentiles())).append('\n');
        sb.append("    save enq:     ").append(formatPercentiles(s.saveEnqueuePercentiles())).append('\n');
        sb.append("    save disk:    ").append(formatPercentiles(s.saveDiskPercentiles())).append('\n');
        sb.append("    broadcast:    ").append(formatPercentiles(s.broadcastPercentiles())).append('\n');
        sb.append("    command total:").append(formatPercentiles(s.commandTotalPercentiles())).append('\n');
        sb.append("    task duration:").append(formatPercentiles(s.taskDurationPercentiles())).append('\n');
        sb.append("    tick spike:   ").append(formatPercentiles(s.tickSpikePercentiles()));
        return sb.toString();
    }

    /** Formats the snapshot as a single JSON object. */
    public String formatJson(Snapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"uptimeMs\":").append(s.uptimeMs());
        sb.append(",\"refreshes\":{\"initiated\":").append(s.refreshesInitiated())
                .append(",\"completed\":").append(s.refreshesCompleted())
                .append(",\"failed\":").append(s.refreshesFailed())
                .append(",\"timedOut\":").append(s.refreshesTimedOut())
                .append(",\"skipped\":").append(s.refreshesSkipped())
                .append(",\"skippedStored\":").append(s.refreshesSkippedStored())
                .append(",\"debounced\":").append(s.refreshesDebounced())
                .append(",\"rateLimited\":").append(s.refreshesRateLimited()).append('}');
        sb.append(",\"broadcasts\":{\"sent\":").append(s.broadcastsSent())
                .append(",\"bytesWritten\":").append(s.bytesWritten()).append('}');
        sb.append(",\"saves\":{\"submitted\":").append(s.savesSubmitted())
                .append(",\"completed\":").append(s.savesCompleted())
                .append(",\"coalesced\":").append(s.savesCoalesced())
                .append(",\"realWrites\":").append(s.realWrites())
                .append(",\"ioFailures\":").append(s.ioFailures())
                .append(",\"pending\":").append(s.pendingAsyncWrites()).append('}');
        sb.append(",\"ioFailuresByType\":{");
        boolean firstType = true;
        for (Map.Entry<String, Long> e : s.ioFailuresByType().entrySet()) {
            if (!firstType) sb.append(',');
            firstType = false;
            sb.append('"').append(e.getKey()).append("\":").append(e.getValue());
        }
        sb.append('}');
        sb.append(",\"networkBytes\":{\"out\":").append(s.netBytesWrittenOut())
                .append(",\"in\":").append(s.netBytesReadIn()).append('}');
        sb.append(",\"provider\":{\"http429\":").append(s.providerHttp429())
                .append(",\"http5xx\":").append(s.providerHttp5xx())
                .append(",\"http4xxOther\":").append(s.providerHttp4xxOther())
                .append(",\"exceptions\":").append(s.providerExceptions()).append('}');
        sb.append(",\"cache\":{\"hits\":").append(s.cacheHits())
                .append(",\"misses\":").append(s.cacheMisses()).append('}');
        sb.append(",\"tickSpikes\":{\"total\":").append(s.tickSpikes())
                .append(",\"broadcast\":").append(s.tickSpikesBroadcast())
                .append(",\"cascade\":").append(s.tickSpikesCascade())
                .append(",\"saveEnqueue\":").append(s.tickSpikesSaveEnqueue()).append('}');
        sb.append(",\"onlinePlayers\":").append(s.onlinePlayers());
        sb.append(",\"latenciesMs\":{")
                .append("\"fetch\":").append(jsonPercentiles(s.fetchPercentiles()))
                .append(",\"saveEnqueue\":").append(jsonPercentiles(s.saveEnqueuePercentiles()))
                .append(",\"saveDisk\":").append(jsonPercentiles(s.saveDiskPercentiles()))
                .append(",\"broadcast\":").append(jsonPercentiles(s.broadcastPercentiles()))
                .append(",\"commandTotal\":").append(jsonPercentiles(s.commandTotalPercentiles()))
                .append(",\"taskDuration\":").append(jsonPercentiles(s.taskDurationPercentiles()))
                .append(",\"tickSpike\":").append(jsonPercentiles(s.tickSpikePercentiles())).append('}');
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

    private static String formatIoFailures(Map<String, Long> byType) {
        if (byType.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        byType.forEach((type, count) -> sb.append(type).append(": ").append(count).append(", "));
        return sb.substring(0, sb.length() - 2);
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
            long refreshesTimedOut, long refreshesDebounced, long refreshesSkipped,
            long refreshesSkippedStored, long refreshesRateLimited,
            long broadcastsSent, long bytesWritten, long savesSubmitted, long savesCompleted,
            long savesCoalesced, long realWrites,
            long ioFailures, int pendingAsyncWrites, long onlinePlayers, long uptimeMs,
            long netBytesWrittenOut, long netBytesReadIn,
            long tickSpikes, long tickSpikesBroadcast, long tickSpikesCascade, long tickSpikesSaveEnqueue,
            long providerHttp429, long providerHttp5xx, long providerHttp4xxOther,
            long providerExceptions, long cacheHits, long cacheMisses,
            Map<String, Long> ioFailuresByType,
            Map<String, Long> fetchPercentiles, Map<String, Long> saveEnqueuePercentiles,
            Map<String, Long> saveDiskPercentiles, Map<String, Long> broadcastPercentiles,
            Map<String, Long> commandTotalPercentiles, Map<String, Long> taskDurationPercentiles,
            Map<String, Long> tickSpikePercentiles,
            Map<UUID, PlayerSnapshot> perPlayer) {
    }

    public record PlayerSnapshot(long refreshCount, long lastRefreshAtMs) {
    }

    static final class PlayerMetrics {
        final LongAdder refreshCount = new LongAdder();
        final AtomicLong lastRefreshAtMs = new AtomicLong();
    }
}
