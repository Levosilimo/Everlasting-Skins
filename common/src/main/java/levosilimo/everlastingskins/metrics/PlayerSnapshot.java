/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.metrics;

/** Immutable per-player refresh summary. Java 8 value class (1.21 uses a record). */
public final class PlayerSnapshot {
    private final long refreshCount;
    private final long lastRefreshAtMs;

    public PlayerSnapshot(long refreshCount, long lastRefreshAtMs) {
        this.refreshCount = refreshCount;
        this.lastRefreshAtMs = lastRefreshAtMs;
    }

    public long refreshCount() { return refreshCount; }
    public long lastRefreshAtMs() { return lastRefreshAtMs; }
}
