/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration.discordsrv;

/**
 * DiscordSRV integration settings, decoupled from the per-version mod
 * {@code Config}. Per-version code calls {@link #configure(boolean, String)}
 * with its own Config values at startup; the defaults below match the
 * per-version Config defaults (integration off, no channel).
 */
public final class DiscordSrvConfig {
    private static boolean enabled = false;
    private static String channelId = "";

    private DiscordSrvConfig() {}

    /** Injects the per-version Config values; null channel id is treated as empty. */
    public static void configure(boolean discordsrvEnabled, String discordsrvChannelId) {
        enabled = discordsrvEnabled;
        channelId = discordsrvChannelId == null ? "" : discordsrvChannelId;
    }

    public static String getChannelId() {
        return channelId;
    }

    public static boolean isEnabled() {
        return enabled && !getChannelId().isEmpty();
    }
}
