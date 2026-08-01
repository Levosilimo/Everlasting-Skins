package levosilimo.everlastingskins.metrics;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * In-process metrics for the EverlastingSkins /skin refresh flow.
 * Zero external dependencies, lock-free writes from any thread.
 * Latency distributions live in {@link LatencyHistogram} fixed buckets;
 * rendering lives in {@link MetricsFormat}.
 */
public final class SkinMetrics {

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

    private final LatencyHistogram fetchLatency = new LatencyHistogram();
    private final LatencyHistogram saveEnqueueLatency = new LatencyHistogram();
    private final LatencyHistogram saveDiskLatency = new LatencyHistogram();
    private final LatencyHistogram broadcastLatency = new LatencyHistogram();
    private final LatencyHistogram commandTotalLatency = new LatencyHistogram();
    private final LatencyHistogram taskDurationLatency = new LatencyHistogram();
    private final LatencyHistogram tickSpikeLatency = new LatencyHistogram();

    private final AtomicInteger pendingAsyncWrites = new AtomicInteger();
    private final LongAdder onlinePlayers = new LongAdder();
    private final AtomicLong startedAtMs = new AtomicLong(System.currentTimeMillis());

    private final ConcurrentHashMap<UUID, PlayerMetrics> perPlayer = new ConcurrentHashMap<>();

    /** Package-private for tests; production uses {@link #INSTANCE}. */
    SkinMetrics() {
    }

    public void recordRefreshStarted(UUID player) {
        refreshesInitiated.increment();
    }

    public void recordRefreshCompleted(UUID player, long startNanos, long fetchNanos, long saveNanos, long broadcastNanos) {
        refreshesCompleted.increment();
        PlayerMetrics pm = perPlayer.computeIfAbsent(player, k -> new PlayerMetrics());
        pm.refreshCount.increment();
        pm.lastRefreshAtMs.set(System.currentTimeMillis());
        fetchLatency.record(fetchNanos);
        saveEnqueueLatency.record(saveNanos);
        broadcastLatency.record(broadcastNanos);
        commandTotalLatency.record(System.nanoTime() - startNanos);
    }

    public void recordRefreshFailed(UUID player) {
        refreshesFailed.increment();
    }

    public void recordTimedOut(UUID player) {
        refreshesTimedOut.increment();
    }

    public void recordRefreshDebounced(UUID player) {
        refreshesDebounced.increment();
    }

    public void recordRefreshSkipped(UUID player) {
        refreshesSkipped.increment();
    }

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

    public void recordSaveEnqueueLatency(long nanos) {
        saveEnqueueLatency.record(nanos);
    }

    public void recordSaveDiskLatency(long nanos) {
        saveDiskLatency.record(nanos);
    }

    public void recordBroadcastLatency(long nanos) {
        broadcastLatency.record(nanos);
    }

    public void recordTaskDuration(long nanos) {
        taskDurationLatency.record(nanos);
    }

    public void recordNetworkDelta(long outDelta, long inDelta) {
        if (outDelta > 0) netBytesWrittenOut.add(outDelta);
        if (inDelta > 0) netBytesReadIn.add(inDelta);
    }

    public void recordTickSpike(long nanos) {
        tickSpikes.increment();
        tickSpikeLatency.record(nanos);
    }

    public void recordSpikeBroadcast(long nanos) {
        if (nanos >= TICK_SPIKE_THRESHOLD_NANOS) tickSpikesBroadcast.increment();
    }

    public void recordSpikeCascade(long nanos) {
        if (nanos >= TICK_SPIKE_THRESHOLD_NANOS) tickSpikesCascade.increment();
    }

    public void recordSpikeSaveEnqueue(long nanos) {
        if (nanos >= TICK_SPIKE_THRESHOLD_NANOS) tickSpikesSaveEnqueue.increment();
    }

    private static final long TICK_SPIKE_THRESHOLD_NANOS = 50_000_000;

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

    public void recordSaveCoalesced() {
        savesCoalesced.increment();
    }

    public void recordRealWrite() {
        realWrites.increment();
    }

    public void recordIoFailure() {
        ioFailures.increment();
    }

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
        fetchLatency.reset();
        saveEnqueueLatency.reset();
        saveDiskLatency.reset();
        broadcastLatency.reset();
        commandTotalLatency.reset();
        taskDurationLatency.reset();
        tickSpikeLatency.reset();
        pendingAsyncWrites.set(0);
        onlinePlayers.reset();
        perPlayer.clear();
        startedAtMs.set(System.currentTimeMillis());
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
                fetchLatency.percentiles(), saveEnqueueLatency.percentiles(),
                saveDiskLatency.percentiles(), broadcastLatency.percentiles(),
                commandTotalLatency.percentiles(), taskDurationLatency.percentiles(),
                tickSpikeLatency.percentiles(),
                snapshotPerPlayer());
    }

    /** Per-player refresh counts, most active first. */
    public List<Map.Entry<UUID, PlayerSnapshot>> topPlayers(int limit) {
        return snapshotPerPlayer().entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<UUID, PlayerSnapshot> e) -> e.getValue().refreshCount()).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private Map<UUID, PlayerSnapshot> snapshotPerPlayer() {
        Map<UUID, PlayerSnapshot> map = new LinkedHashMap<>();
        perPlayer.forEach((uuid, pm) -> map.put(uuid, new PlayerSnapshot(pm.refreshCount.sum(), pm.lastRefreshAtMs.get())));
        return map;
    }

    /**
     * Immutable snapshot of all counters and histograms. Java 8 value class
     * (the 1.21 branch declares this as a {@code record}).
     */
    public static final class Snapshot {
        private final long refreshesInitiated;
        private final long refreshesCompleted;
        private final long refreshesFailed;
        private final long refreshesTimedOut;
        private final long refreshesDebounced;
        private final long refreshesSkipped;
        private final long refreshesSkippedStored;
        private final long refreshesRateLimited;
        private final long broadcastsSent;
        private final long bytesWritten;
        private final long savesSubmitted;
        private final long savesCompleted;
        private final long savesCoalesced;
        private final long realWrites;
        private final long ioFailures;
        private final int pendingAsyncWrites;
        private final long onlinePlayers;
        private final long uptimeMs;
        private final long netBytesWrittenOut;
        private final long netBytesReadIn;
        private final long tickSpikes;
        private final long tickSpikesBroadcast;
        private final long tickSpikesCascade;
        private final long tickSpikesSaveEnqueue;
        private final long providerHttp429;
        private final long providerHttp5xx;
        private final long providerHttp4xxOther;
        private final long providerExceptions;
        private final long cacheHits;
        private final long cacheMisses;
        private final Map<String, Long> ioFailuresByType;
        private final Map<String, Long> fetchPercentiles;
        private final Map<String, Long> saveEnqueuePercentiles;
        private final Map<String, Long> saveDiskPercentiles;
        private final Map<String, Long> broadcastPercentiles;
        private final Map<String, Long> commandTotalPercentiles;
        private final Map<String, Long> taskDurationPercentiles;
        private final Map<String, Long> tickSpikePercentiles;
        private final Map<UUID, PlayerSnapshot> perPlayer;

        public Snapshot(long refreshesInitiated, long refreshesCompleted, long refreshesFailed,
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
            this.refreshesInitiated = refreshesInitiated;
            this.refreshesCompleted = refreshesCompleted;
            this.refreshesFailed = refreshesFailed;
            this.refreshesTimedOut = refreshesTimedOut;
            this.refreshesDebounced = refreshesDebounced;
            this.refreshesSkipped = refreshesSkipped;
            this.refreshesSkippedStored = refreshesSkippedStored;
            this.refreshesRateLimited = refreshesRateLimited;
            this.broadcastsSent = broadcastsSent;
            this.bytesWritten = bytesWritten;
            this.savesSubmitted = savesSubmitted;
            this.savesCompleted = savesCompleted;
            this.savesCoalesced = savesCoalesced;
            this.realWrites = realWrites;
            this.ioFailures = ioFailures;
            this.pendingAsyncWrites = pendingAsyncWrites;
            this.onlinePlayers = onlinePlayers;
            this.uptimeMs = uptimeMs;
            this.netBytesWrittenOut = netBytesWrittenOut;
            this.netBytesReadIn = netBytesReadIn;
            this.tickSpikes = tickSpikes;
            this.tickSpikesBroadcast = tickSpikesBroadcast;
            this.tickSpikesCascade = tickSpikesCascade;
            this.tickSpikesSaveEnqueue = tickSpikesSaveEnqueue;
            this.providerHttp429 = providerHttp429;
            this.providerHttp5xx = providerHttp5xx;
            this.providerHttp4xxOther = providerHttp4xxOther;
            this.providerExceptions = providerExceptions;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.ioFailuresByType = ioFailuresByType;
            this.fetchPercentiles = fetchPercentiles;
            this.saveEnqueuePercentiles = saveEnqueuePercentiles;
            this.saveDiskPercentiles = saveDiskPercentiles;
            this.broadcastPercentiles = broadcastPercentiles;
            this.commandTotalPercentiles = commandTotalPercentiles;
            this.taskDurationPercentiles = taskDurationPercentiles;
            this.tickSpikePercentiles = tickSpikePercentiles;
            this.perPlayer = perPlayer;
        }

        public long refreshesInitiated() { return refreshesInitiated; }
        public long refreshesCompleted() { return refreshesCompleted; }
        public long refreshesFailed() { return refreshesFailed; }
        public long refreshesTimedOut() { return refreshesTimedOut; }
        public long refreshesDebounced() { return refreshesDebounced; }
        public long refreshesSkipped() { return refreshesSkipped; }
        public long refreshesSkippedStored() { return refreshesSkippedStored; }
        public long refreshesRateLimited() { return refreshesRateLimited; }
        public long broadcastsSent() { return broadcastsSent; }
        public long bytesWritten() { return bytesWritten; }
        public long savesSubmitted() { return savesSubmitted; }
        public long savesCompleted() { return savesCompleted; }
        public long savesCoalesced() { return savesCoalesced; }
        public long realWrites() { return realWrites; }
        public long ioFailures() { return ioFailures; }
        public int pendingAsyncWrites() { return pendingAsyncWrites; }
        public long onlinePlayers() { return onlinePlayers; }
        public long uptimeMs() { return uptimeMs; }
        public long netBytesWrittenOut() { return netBytesWrittenOut; }
        public long netBytesReadIn() { return netBytesReadIn; }
        public long tickSpikes() { return tickSpikes; }
        public long tickSpikesBroadcast() { return tickSpikesBroadcast; }
        public long tickSpikesCascade() { return tickSpikesCascade; }
        public long tickSpikesSaveEnqueue() { return tickSpikesSaveEnqueue; }
        public long providerHttp429() { return providerHttp429; }
        public long providerHttp5xx() { return providerHttp5xx; }
        public long providerHttp4xxOther() { return providerHttp4xxOther; }
        public long providerExceptions() { return providerExceptions; }
        public long cacheHits() { return cacheHits; }
        public long cacheMisses() { return cacheMisses; }
        public Map<String, Long> ioFailuresByType() { return ioFailuresByType; }
        public Map<String, Long> fetchPercentiles() { return fetchPercentiles; }
        public Map<String, Long> saveEnqueuePercentiles() { return saveEnqueuePercentiles; }
        public Map<String, Long> saveDiskPercentiles() { return saveDiskPercentiles; }
        public Map<String, Long> broadcastPercentiles() { return broadcastPercentiles; }
        public Map<String, Long> commandTotalPercentiles() { return commandTotalPercentiles; }
        public Map<String, Long> taskDurationPercentiles() { return taskDurationPercentiles; }
        public Map<String, Long> tickSpikePercentiles() { return tickSpikePercentiles; }
        public Map<UUID, PlayerSnapshot> perPlayer() { return perPlayer; }
    }

    /** Immutable per-player refresh summary. Java 8 value class (1.21 uses a record). */
    public static final class PlayerSnapshot {
        private final long refreshCount;
        private final long lastRefreshAtMs;

        public PlayerSnapshot(long refreshCount, long lastRefreshAtMs) {
            this.refreshCount = refreshCount;
            this.lastRefreshAtMs = lastRefreshAtMs;
        }

        public long refreshCount() { return refreshCount; }
        public long lastRefreshAtMs() { return lastRefreshAtMs; }
    }

    static final class PlayerMetrics {
        final LongAdder refreshCount = new LongAdder();
        final AtomicLong lastRefreshAtMs = new AtomicLong();
    }
}
