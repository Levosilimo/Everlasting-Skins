/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.metrics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Fixed-bucket latency histogram (no HdrHistogram dependency). Samples land on
 * the smallest bucket bound >= their duration; anything above the last bound
 * falls into the overflow bucket. p50/p95/p99 walk the cumulative distribution.
 */
final class LatencyHistogram {

    static final long[] BUCKET_BOUNDS_US = {
            1, 5, 10, 50, 100, 500, 1_000, 5_000, 10_000, 50_000,
            100_000, 250_000, 500_000, 1_000_000, 5_000_000, 10_000_000
    };

    private final LongAdder[] buckets = new LongAdder[BUCKET_BOUNDS_US.length + 1];

    LatencyHistogram() {
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new LongAdder();
        }
    }

    void record(long nanos) {
        if (nanos <= 0) return;
        long micros = Math.max(1, nanos / 1000);
        int idx = java.util.Arrays.binarySearch(BUCKET_BOUNDS_US, micros);
        if (idx < 0) idx = -idx - 1;
        buckets[idx].increment();
    }

    long totalCount() {
        long sum = 0;
        for (LongAdder bucket : buckets) {
            sum += bucket.sum();
        }
        return sum;
    }

    /** p50/p95/p99/max in microseconds; 0 when empty. */
    Map<String, Long> percentiles() {
        Map<String, Long> result = new LinkedHashMap<>();
        long total = totalCount();
        if (total == 0) {
            result.put("p50", 0L);
            result.put("p95", 0L);
            result.put("p99", 0L);
            result.put("max", 0L);
            return result;
        }
        long p50 = percentile(total, 0.50);
        long p95 = percentile(total, 0.95);
        long p99 = percentile(total, 0.99);
        long max = 0;
        for (int i = 0; i < buckets.length; i++) {
            long count = buckets[i].sum();
            if (count == 0) continue;
            max = bound(i);
        }
        result.put("p50", p50);
        result.put("p95", p95);
        result.put("p99", p99);
        result.put("max", max);
        return result;
    }

    private long percentile(long total, double fraction) {
        long target = Math.round(total * fraction);
        long cumulative = 0;
        for (int i = 0; i < buckets.length; i++) {
            long count = buckets[i].sum();
            if (count == 0) continue;
            cumulative += count;
            if (cumulative >= target) return bound(i);
        }
        return 0;
    }

    private long bound(int index) {
        return index < BUCKET_BOUNDS_US.length ? BUCKET_BOUNDS_US[index] : BUCKET_BOUNDS_US[BUCKET_BOUNDS_US.length - 1];
    }

    void reset() {
        for (LongAdder bucket : buckets) {
            bucket.reset();
        }
    }
}
