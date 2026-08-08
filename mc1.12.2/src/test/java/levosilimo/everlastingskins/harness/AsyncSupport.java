/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.harness;

import java.util.function.BooleanSupplier;

/**
 * Poll-with-timeout for the CompletableFuture path in SkinCommand.fetchSkinProperty.
 */
public final class AsyncSupport {

    private AsyncSupport() {
    }

    public static boolean await(long deadlineMs, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + deadlineMs;
        long pollMs = 1;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return true;
            try {
                Thread.sleep(pollMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            pollMs = Math.min(100, pollMs * 2);  // 1 -> 2 -> 4 -> ... -> 100ms cap
        }
        return condition.getAsBoolean();
    }
}
