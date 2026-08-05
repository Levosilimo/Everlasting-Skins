/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.metrics;

import java.util.Map;
import java.util.UUID;

/**
 * Human-readable and JSON rendering of a metrics snapshot. Pure functions of
 * the snapshot, kept separate from the recording class.
 */
public final class MetricsFormat {

    private MetricsFormat() {
    }

    public static String human(Snapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("EverlastingSkins Metrics (uptime ").append(uptime(s.uptimeMs())).append(")\n");
        sb.append("  refreshes: ").append(s.refreshesInitiated()).append(" initiated, ")
                .append(s.refreshesCompleted()).append(" completed, ")
                .append(s.refreshesFailed()).append(" failed, ")
                .append(s.refreshesTimedOut()).append(" timed out\n");
        sb.append("  skipped: ").append(s.refreshesSkipped()).append(" (identical), ")
                .append(s.refreshesSkippedStored()).append(" (stored source), debounced: ")
                .append(s.refreshesDebounced()).append(", rate-limited: ").append(s.refreshesRateLimited()).append('\n');
        sb.append("  broadcasts: ").append(s.broadcastsSent()).append(" (").append(bytes(s.bytesWritten())).append(" written)\n");
        sb.append("  saves: ").append(s.savesSubmitted()).append(" submitted, ").append(s.savesCompleted())
                .append(" completed, ").append(s.savesCoalesced()).append(" coalesced, ")
                .append(s.realWrites()).append(" real writes, ").append(s.ioFailures()).append(" failed, ")
                .append(s.pendingAsyncWrites()).append(" pending\n");
        sb.append("  reads: ").append(s.readsSubmitted()).append(" submitted, ").append(s.readsCompleted())
                .append(" completed, ").append(s.readFailures()).append(" failed\n");
        sb.append("  io failures by type: ").append(ioFailures(s.ioFailuresByType())).append('\n');
        sb.append("  network: ").append(bytes(s.netBytesWrittenOut())).append(" out, ")
                .append(bytes(s.netBytesReadIn())).append(" in (per-connection)\n");
        sb.append("  provider: ").append(s.providerHttp429()).append("x429, ")
                .append(s.providerHttp5xx()).append("x5xx, ")
                .append(s.providerHttp4xxOther()).append("x4xx, ")
                .append(s.providerExceptions()).append(" exceptions\n");
        sb.append("  cache: ").append(s.cacheHits()).append(" hits, ")
                .append(s.cacheMisses()).append(" misses\n");
        sb.append("  mine skin rate-limit sleeps: ").append(s.mineSkinDelayTotalMs()).append(" ms\n");
        sb.append("  tick spikes: ").append(s.tickSpikes()).append(" (broadcast ")
                .append(s.tickSpikesBroadcast()).append(", cascade ").append(s.tickSpikesCascade())
                .append(", save-enqueue ").append(s.tickSpikesSaveEnqueue()).append(")\n");
        sb.append("  online players: ").append(s.onlinePlayers()).append('\n');
        sb.append("  latencies (ms):\n");
        sb.append("    fetch:        ").append(percentiles(s.fetchPercentiles())).append('\n');
        sb.append("    save enq:     ").append(percentiles(s.saveEnqueuePercentiles())).append('\n');
        sb.append("    save disk:    ").append(percentiles(s.saveDiskPercentiles())).append('\n');
        sb.append("    read disk:    ").append(percentiles(s.readDiskPercentiles())).append('\n');
        sb.append("    broadcast:    ").append(percentiles(s.broadcastPercentiles())).append('\n');
        sb.append("    command total:").append(percentiles(s.commandTotalPercentiles())).append('\n');
        sb.append("    task duration:").append(percentiles(s.taskDurationPercentiles())).append('\n');
        sb.append("    tick spike:   ").append(percentiles(s.tickSpikePercentiles()));
        return sb.toString();
    }

    public static String json(Snapshot s) {
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
        sb.append(",\"reads\":{\"submitted\":").append(s.readsSubmitted())
                .append(",\"completed\":").append(s.readsCompleted())
                .append(",\"failed\":").append(s.readFailures()).append('}');
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
                .append("\"fetch\":").append(percentilesJson(s.fetchPercentiles()))
                .append(",\"saveEnqueue\":").append(percentilesJson(s.saveEnqueuePercentiles()))
                .append(",\"saveDisk\":").append(percentilesJson(s.saveDiskPercentiles()))
                .append(",\"readDisk\":").append(percentilesJson(s.readDiskPercentiles()))
                .append(",\"broadcast\":").append(percentilesJson(s.broadcastPercentiles()))
                .append(",\"commandTotal\":").append(percentilesJson(s.commandTotalPercentiles()))
                .append(",\"taskDuration\":").append(percentilesJson(s.taskDurationPercentiles()))
                .append(",\"tickSpike\":").append(percentilesJson(s.tickSpikePercentiles())).append('}');
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

    private static String ioFailures(Map<String, Long> byType) {
        if (byType.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        byType.forEach((type, count) -> sb.append(type).append(": ").append(count).append(", "));
        return sb.substring(0, sb.length() - 2);
    }

    private static String percentilesJson(Map<String, Long> p) {
        return "{\"p50\":" + p.get("p50") + ",\"p95\":" + p.get("p95")
                + ",\"p99\":" + p.get("p99") + ",\"max\":" + p.get("max") + '}';
    }

    private static String percentiles(Map<String, Long> p) {
        return "p50=" + usToMs(p.get("p50")) + ", p95=" + usToMs(p.get("p95"))
                + ", p99=" + usToMs(p.get("p99")) + ", max=" + usToMs(p.get("max"));
    }

    private static long usToMs(long us) {
        return us / 1000;
    }

    private static String uptime(long ms) {
        long totalMin = ms / 60_000;
        return totalMin / 60 + "h " + totalMin % 60 + "m";
    }

    private static String bytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return bytes / 1024 + " KB";
        return bytes / (1024 * 1024) + " MB";
    }
}
