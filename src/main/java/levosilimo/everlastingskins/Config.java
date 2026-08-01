package levosilimo.everlastingskins;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class Config {
    public static String LANGUAGE = "en";
    public static boolean TOGGLE = true;
    public static String MINESKIN_API_KEY = "";
    public static boolean MINESKIN_ENABLED = false; // OFF for Phase 5 viability gate.

    public static boolean DISCORDSRV_ENABLED = false;
    public static String DISCORDSRV_CHANNEL_ID = "";

    public static boolean metricsEnabled = true;
    public static int metricsDumpIntervalSeconds = 60;
    public static boolean refreshViaEntityTracker = true;

    public static void load(File configFile) {
        Configuration cfg = new Configuration(configFile);
        try {
            cfg.load();
            LANGUAGE = cfg.getString("localization", "Messages", LANGUAGE, "Language of mod messages");
            TOGGLE = cfg.getBoolean("display", "Messages", TOGGLE, "Display mod messages");
            MINESKIN_API_KEY = cfg.getString("key", "Messages", MINESKIN_API_KEY, "Mineskin api key");
            MINESKIN_ENABLED = cfg.getBoolean("enabled", "MineSkin", false,
                "Enable MineSkin URL-based skin generation (off during Phase 5 viability gate)");
            DISCORDSRV_ENABLED = cfg.getBoolean("discordsrv_enabled", "Integration", false,
                "Enable DiscordSRV skin change announcements");
            DISCORDSRV_CHANNEL_ID = cfg.getString("discordsrv_channel_id", "Integration", "",
                "Discord channel ID for skin change announcements");
            metricsEnabled = cfg.getBoolean("metricsEnabled", "everlastingskins", metricsEnabled,
                "Enable in-process metrics");
            metricsDumpIntervalSeconds = cfg.getInt("metricsDumpIntervalSeconds", "everlastingskins",
                metricsDumpIntervalSeconds, 0, 3600, "Metrics dump interval (seconds)");
            refreshViaEntityTracker = cfg.getBoolean("refreshViaEntityTracker", "everlastingskins",
                refreshViaEntityTracker, "Force EntityTracker untrack/re-track on refresh for observer entity re-render");
        } catch (Exception e) {
            EverlastingSkins.logger.error("Failed to load config", e);
        } finally {
            if (cfg.hasChanged()) cfg.save();
        }
    }

    private Config() {}
}
