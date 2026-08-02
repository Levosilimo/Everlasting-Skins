package levosilimo.everlastingskins;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;

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


    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("Messages");
        LANGUAGE = builder.comment("Language of mod messages").define("localization","en");
        TOGGLE = builder.comment("Display mod messages").define("display",true);
        MINESKIN_API_KEY = builder.comment("Mineskin api key").define("key","");
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
        COMMON_CONFIG = builder.build();
    }
}
