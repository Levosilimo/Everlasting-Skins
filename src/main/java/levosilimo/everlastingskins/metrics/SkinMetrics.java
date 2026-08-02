/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


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
    private final LongAdder mineSkinDelayTotalMs = new LongAdder();

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

    public void recordMineSkinDelay(long millis) {
        mineSkinDelayTotalMs.add(millis);
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
        mineSkinDelayTotalMs.reset();
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
                mineSkinDelayTotalMs.sum(),
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
                .toList();
    }

    private Map<UUID, PlayerSnapshot> snapshotPerPlayer() {
        Map<UUID, PlayerSnapshot> map = new LinkedHashMap<>();
        perPlayer.forEach((uuid, pm) -> map.put(uuid, new PlayerSnapshot(pm.refreshCount.sum(), pm.lastRefreshAtMs.get())));
        return map;
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
            long mineSkinDelayTotalMs,
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
