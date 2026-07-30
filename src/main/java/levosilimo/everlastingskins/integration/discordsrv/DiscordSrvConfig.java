package levosilimo.everlastingskins.integration.discordsrv;

import levosilimo.everlastingskins.Config;

public final class DiscordSrvConfig {
    private DiscordSrvConfig() {}

    public static String getChannelId() {
        return Config.DISCORDSRV_CHANNEL_ID;
    }

    public static boolean isEnabled() {
        return Config.DISCORDSRV_ENABLED && !getChannelId().isEmpty();
    }
}
