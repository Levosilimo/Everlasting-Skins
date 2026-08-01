package levosilimo.everlastingskins.metrics;

import java.util.Map;
import java.util.UUID;

/**
 * Immutable snapshot of all counters and histograms. Java 8 value class
 * (the 1.21 branch declares this as a {@code record} nested in SkinMetrics).
 */
public final class Snapshot {
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
