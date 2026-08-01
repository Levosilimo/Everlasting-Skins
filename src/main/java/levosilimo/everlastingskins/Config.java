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
        COMMON_CONFIG = builder.build();
    }
}
