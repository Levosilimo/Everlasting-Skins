/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins;

import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.concurrent.TimeUnit;

public final class Config {
    public static String LANGUAGE = "en";
    public static boolean TOGGLE = true;
    public static String MINESKIN_API_KEY = "";
    public static boolean MINESKIN_ENABLED = false; // Set to true to enable MineSkin URL-skin generation.

    public static boolean DISCORDSRV_ENABLED = false;
    public static String DISCORDSRV_CHANNEL_ID = "";

    public static boolean metricsEnabled = true;
    public static int metricsDumpIntervalSeconds = 60;
    public static boolean refreshViaEntityTracker = true;
    public static int COOLDOWN_SECONDS = 3;
    public static boolean RATE_LIMIT_ENABLED = true;
    public static int MAX_COMMANDS_PER_MINUTE = 5;
    public static int DEBOUNCE_MILLIS = 100;

    public static boolean urlAllowlistEnabled = false;
    public static String[] urlAllowlistDomains = new String[]{
        "imgur.com", "storage.googleapis.com", "cdn.discordapp.com",
        "textures.minecraft.net", "namemc.com", "crafatar.com",
        "mc-heads.net", "githubusercontent.com", "minecraftskins.com"
    };

    public static boolean mojangProfileCacheEnabled = true;
    public static long mojangProfileCacheTtlMs = TimeUnit.HOURS.toMillis(1);
    public static int mojangProfileCacheMaxSize = 1000;

    public static boolean DEFAULT_SKINS_ENABLED = false;
    public static boolean DEFAULT_SKINS_APPLY_FOR_PREMIUM = false;
    public static String[] DEFAULT_SKINS_LIST = {"Steve", "<random>"};

    public static void load(File configFile) {
        Configuration cfg = new Configuration(configFile);
        try {
            cfg.load();
            LANGUAGE = cfg.getString("localization", "Messages", LANGUAGE, "Language of mod messages");
            TOGGLE = cfg.getBoolean("display", "Messages", TOGGLE, "Display mod messages");
            MINESKIN_API_KEY = cfg.getString("key", "Messages", MINESKIN_API_KEY, "Mineskin api key");
            MINESKIN_ENABLED = cfg.getBoolean("enabled", "MineSkin", false,
                "Enable MineSkin URL-based skin generation");
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
            RATE_LIMIT_ENABLED = cfg.getBoolean("rate_limit_enabled", "everlastingskins",
                RATE_LIMIT_ENABLED, "Enable /skin rate limiting");
            COOLDOWN_SECONDS = cfg.getInt("cooldown_seconds", "everlastingskins",
                COOLDOWN_SECONDS, 0, 60, "Cooldown between /skin commands (seconds)");
            MAX_COMMANDS_PER_MINUTE = cfg.getInt("max_commands_per_minute", "everlastingskins",
                MAX_COMMANDS_PER_MINUTE, 1, 60, "Max /skin commands per minute (per player)");
            DEBOUNCE_MILLIS = cfg.getInt("debounce_millis", "everlastingskins",
                DEBOUNCE_MILLIS, 0, 5000, "Per-player refresh debounce window (milliseconds)");
            DEFAULT_SKINS_ENABLED = cfg.getBoolean("enabled", "DefaultSkins", DEFAULT_SKINS_ENABLED,
                "Apply a default skin from 'list' to players without a saved custom skin");
            DEFAULT_SKINS_APPLY_FOR_PREMIUM = cfg.getBoolean("applyForPremium", "DefaultSkins",
                DEFAULT_SKINS_APPLY_FOR_PREMIUM,
                "Also apply the default skin to players WITH a saved custom skin (display-only override)");
            DEFAULT_SKINS_LIST = cfg.getStringList("list", "DefaultSkins", DEFAULT_SKINS_LIST,
                "Default skins list: Mojang usernames or the literal '<random>' token");
            urlAllowlistEnabled = cfg.getBoolean("urlAllowlistEnabled", "Security", urlAllowlistEnabled,
                "Enable URL domain allowlist for /skin set web (empty list = deny all)");
            urlAllowlistDomains = cfg.getStringList("urlAllowlistDomains", "Security", urlAllowlistDomains,
                "Domains allowed for /skin set web (eTLD+1 suffix match; one entry covers all subdomains)");
        } catch (Exception e) {
            EverlastingSkins.logger.error("Failed to load config", e);
        } finally {
            if (cfg.hasChanged()) cfg.save();
        }
    }

    private Config() {}
}
