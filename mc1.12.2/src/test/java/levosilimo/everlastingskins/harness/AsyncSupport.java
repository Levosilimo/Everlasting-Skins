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
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return true;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }
}
