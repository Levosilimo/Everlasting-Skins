/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber
public class Config {
    public static ForgeConfigSpec COMMON_CONFIG;

    public static ForgeConfigSpec.ConfigValue<String> LANGUAGE;
    public static ForgeConfigSpec.BooleanValue TOGGLE;

    public static ForgeConfigSpec.ConfigValue<String> MINESKIN_API_KEY;

    public static ForgeConfigSpec.BooleanValue DISCORDSRV_ENABLED;
    public static ForgeConfigSpec.ConfigValue<String> DISCORDSRV_CHANNEL_ID;

    public static ForgeConfigSpec.IntValue COOLDOWN_SECONDS;
    public static ForgeConfigSpec.BooleanValue RATE_LIMIT_ENABLED;
    public static ForgeConfigSpec.IntValue MAX_COMMANDS_PER_MINUTE;

    public static ForgeConfigSpec.BooleanValue DIMENSION_SCOPED_BROADCAST;
    public static ForgeConfigSpec.BooleanValue REFRESH_VIA_ENTITY_TRACKER;

    public static ForgeConfigSpec.BooleanValue METRICS_ENABLED;
    public static ForgeConfigSpec.IntValue METRICS_DUMP_INTERVAL_SECONDS;

    public static ForgeConfigSpec.BooleanValue BROADCAST_USE_BUNDLE;
    public static ForgeConfigSpec.IntValue DEBOUNCE_MILLIS;
    public static ForgeConfigSpec.ConfigValue<String> HTTP_CLIENT_VERSION;
    public static ForgeConfigSpec.IntValue HTTP_CONNECT_TIMEOUT_SECONDS;

    public static ForgeConfigSpec.BooleanValue MOJANG_CACHE_ENABLED;
    public static ForgeConfigSpec.LongValue MOJANG_CACHE_TTL_MS;
    public static ForgeConfigSpec.IntValue MOJANG_CACHE_MAX_SIZE;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_CHANGE;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_FULFILLED;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_TIMEOUT;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_ERROR;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_RESTORED_FROM;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_CLEARED_NO_PROFILE;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_NO_SOURCE;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_PLAYER_ONLY;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_PERMISSION_DENIED;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_COOLDOWN;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_RATE_LIMITED;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_NO_SKIN_FOUND;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_NO_SKIN_FOUND_PLAIN;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_MINESKIN_REJECTED;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_NO_RANDOM_USERNAME;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_PROVIDER_NO_RESULT;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_METRICS_TOP_PLAYERS;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_METRICS_REFRESHES;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_METRICS_NO_REFRESHES;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_METRICS_CLEANUP;
    public static ForgeConfigSpec.ConfigValue<String> MESSAGES_METRICS_RESET;
    public static ForgeConfigSpec.BooleanValue DEFAULT_SKINS_ENABLED;
    public static ForgeConfigSpec.BooleanValue DEFAULT_SKINS_APPLY_FOR_PREMIUM;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> DEFAULT_SKINS_LIST;
    public static ForgeConfigSpec.BooleanValue URL_ALLOWLIST_ENABLED;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> URL_ALLOWLIST_DOMAINS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("Messages");
        LANGUAGE = builder.comment("Language of mod messages").define("localization","en");
        TOGGLE = builder.comment("Display mod messages").define("display",true);
        MINESKIN_API_KEY = builder.comment("Mineskin api key").define("key","");
        MESSAGES_CHANGE = builder.define("messages_change", "Skin change queued");
        MESSAGES_FULFILLED = builder.define("messages_fulfilled", "Skin has been applied.");
        MESSAGES_TIMEOUT = builder.define("messages_timeout", "Skin fetch timed out.");
        MESSAGES_ERROR = builder.define("messages_error", "Skin fetch failed.");
        MESSAGES_RESTORED_FROM = builder.define("messages_restored_from", "Skin restored from %s");
        MESSAGES_CLEARED_NO_PROFILE = builder.define("messages_cleared_no_profile", "Skin cleared (no Mojang profile found)");
        MESSAGES_NO_SOURCE = builder.define("messages_no_source", "No source available");
        MESSAGES_PLAYER_ONLY = builder.define("messages_player_only", "Player only command");
        MESSAGES_PERMISSION_DENIED = builder.define("messages_permission_denied", "Permission denied");
        MESSAGES_COOLDOWN = builder.define("messages_cooldown", "Please wait %ds before using /skin again");
        MESSAGES_RATE_LIMITED = builder.define("messages_rate_limited", "Too many /skin commands. Try again later.");
        MESSAGES_NO_SKIN_FOUND = builder.define("messages_no_skin_found", "No skin found for \"%s\"");
        MESSAGES_NO_SKIN_FOUND_PLAIN = builder.define("messages_no_skin_found_plain", "No skin found");
        MESSAGES_MINESKIN_REJECTED = builder.define("messages_mineskin_rejected", "MineSkin rejected the URL");
        MESSAGES_NO_RANDOM_USERNAME = builder.define("messages_no_random_username", "No random username available");
        MESSAGES_PROVIDER_NO_RESULT = builder.define("messages_provider_no_result", "Provider returned no result");
        MESSAGES_METRICS_TOP_PLAYERS = builder.define("messages_metrics_top_players", "Top players by refresh count:");
        MESSAGES_METRICS_REFRESHES = builder.define("messages_metrics_refreshes", " refreshes");
        MESSAGES_METRICS_NO_REFRESHES = builder.define("messages_metrics_no_refreshes", "(no refreshes recorded)");
        MESSAGES_METRICS_CLEANUP = builder.define("messages_metrics_cleanup", "Metrics cleanup: pruned %d stale player entries");
        MESSAGES_METRICS_RESET = builder.define("messages_metrics_reset", "Metrics reset");
        builder.pop();
        builder.push("Integration");
        DISCORDSRV_ENABLED = builder.comment("Enable DiscordSRV skin change announcements")
            .define("discordsrv_enabled", false);
        DISCORDSRV_CHANNEL_ID = builder.comment("Discord channel ID for skin change announcements")
            .define("discordsrv_channel_id", "");
        builder.pop();
        builder.push("RateLimit");
        COOLDOWN_SECONDS = builder.comment("Cooldown between /skin commands (seconds)")
            .defineInRange("cooldown_seconds", 3, 0, 60);
        RATE_LIMIT_ENABLED = builder.comment("Enable /skin rate limiting")
            .define("rate_limit_enabled", true);
        MAX_COMMANDS_PER_MINUTE = builder.comment("Max /skin commands per minute (per player)")
            .defineInRange("max_commands_per_minute", 5, 1, 60);
        builder.pop();
        builder.push("Broadcast");
        DIMENSION_SCOPED_BROADCAST = builder.comment("Restrict skin refresh broadcasts to the target player's dimension."
                + " Off by default to match vanilla and 1.12.2 behavior (all players notified).")
            .define("dimension_scoped_broadcast", false);
        BROADCAST_USE_BUNDLE = builder.comment("Send the REMOVE + ADD_PLAYER broadcast as one ClientboundBundlePacket (A/B test; off by default)")
            .define("broadcast_use_bundle", false);
        DEBOUNCE_MILLIS = builder.comment("Per-player refresh debounce window (milliseconds)")
            .defineInRange("debounce_millis", 100, 0, 5000);
        REFRESH_VIA_ENTITY_TRACKER = builder.comment("Untrack/re-track the target entity so observers re-fetch the updated profile"
                + " (fixes stale skin renders on remote clients; the chunkMap step PR #121 dropped)")
            .define("refresh_via_entity_tracker", true);
        builder.pop();
        builder.push("Metrics");
        METRICS_ENABLED = builder.comment("Enable in-process metrics collection and periodic metrics.json dump")
            .define("metrics_enabled", true);
        METRICS_DUMP_INTERVAL_SECONDS = builder.comment("Interval between metrics.json dumps (seconds; 0 disables the dump)")
            .defineInRange("metrics_dump_interval_seconds", 60, 0, 3600);
        builder.pop();
        builder.push("Http");
        HTTP_CLIENT_VERSION = builder.comment("JDK HTTP client version: HTTP_2 (default) or HTTP_1_1")
            .define("http_client_version", "HTTP_2");
        HTTP_CONNECT_TIMEOUT_SECONDS = builder.comment("Connection timeout for provider requests (seconds)")
            .defineInRange("http_connect_timeout_seconds", 5, 1, 60);
        builder.pop();
        builder.push("MojangCache");
        MOJANG_CACHE_ENABLED = builder.comment("Enable the in-process Mojang profile cache")
            .define("mojang_profile_cache_enabled", true);
        MOJANG_CACHE_TTL_MS = builder.comment("Mojang profile cache entry lifetime (milliseconds; 0 disables caching)")
            .defineInRange("mojang_profile_cache_ttl_ms", 3600000L, 0L, 604800000L);
        MOJANG_CACHE_MAX_SIZE = builder.comment("Max Mojang profile cache entries (oldest evicted first)")
            .defineInRange("mojang_profile_cache_max_size", 1000, 0, 1000000);
        builder.pop();
        builder.push("DefaultSkins");
        DEFAULT_SKINS_ENABLED = builder.comment("Apply a default skin from 'list' to players without a saved custom skin")
            .define("enabled", false);
        DEFAULT_SKINS_APPLY_FOR_PREMIUM = builder.comment("Also apply the default skin to players WITH a saved custom skin"
                + " (display-only override; their stored custom skin is preserved)")
            .define("applyForPremium", false);
        DEFAULT_SKINS_LIST = builder.comment("Default skins list: Mojang usernames or the literal '<random>' token"
                + " (random Mojang username on each login)")
            .defineList("list", List.of("Steve", "<random>"), o -> o instanceof String);
        builder.pop();
        builder.push("Security");
        URL_ALLOWLIST_ENABLED = builder.comment("Enable URL domain allowlist for /skin set web (empty list = deny all)")
            .define("urlAllowlistEnabled", false);
        URL_ALLOWLIST_DOMAINS = builder.comment("Domains allowed for /skin set web (eTLD+1 suffix match; one entry covers all subdomains)")
            .defineList("urlAllowlistDomains", List.of(
                "imgur.com", "storage.googleapis.com", "cdn.discordapp.com",
                "textures.minecraft.net", "namemc.com", "crafatar.com",
                "mc-heads.net", "githubusercontent.com", "minecraftskins.com"
            ), o -> o instanceof String);
        builder.pop();
        COMMON_CONFIG = builder.build();
    }
}
