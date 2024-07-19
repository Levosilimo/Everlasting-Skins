package levosilimo.everlastingskins;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class Config {
    public static ForgeConfigSpec COMMON_CONFIG;

    public static ForgeConfigSpec.ConfigValue<String> LANGUAGE;
    public static ForgeConfigSpec.BooleanValue TOGGLE;

    public static ForgeConfigSpec.ConfigValue<String> MINESKIN_API_KEY;


    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("Messages");
        LANGUAGE = builder.comment("Language of mod messages").define("localization","en");
        TOGGLE = builder.comment("Display mod messages").define("display",true);
        MINESKIN_API_KEY = builder.comment("Mineskin api key").define("key","");
        builder.pop();
        COMMON_CONFIG = builder.build();
    }
}
