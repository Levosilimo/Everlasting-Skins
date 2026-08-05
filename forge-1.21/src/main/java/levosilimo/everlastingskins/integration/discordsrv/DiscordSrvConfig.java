/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.integration.discordsrv;

import levosilimo.everlastingskins.Config;

public final class DiscordSrvConfig {
    private DiscordSrvConfig() {}

    public static String getChannelId() {
        return Config.DISCORDSRV_CHANNEL_ID.get();
    }

    public static boolean isEnabled() {
        return Config.DISCORDSRV_ENABLED.get() && !getChannelId().isEmpty();
    }
}
