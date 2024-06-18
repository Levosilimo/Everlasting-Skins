package levosilimo.everlastingskins;

import levosilimo.everlastingskins.enums.LanguageEnum;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod.EventBusSubscriber
public class Config {
    public static ForgeConfigSpec COMMON_CONFIG;

    public static ForgeConfigSpec.ConfigValue<String> LANGUAGE;
    public static ForgeConfigSpec.BooleanValue TOGGLE;


    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("Messages");
        LANGUAGE = builder.comment("Language of mod messages").define("localization","en");
        TOGGLE = builder.comment("Display mod messages").define("display",true);
        builder.pop();
        COMMON_CONFIG = builder.build();
    }

    @SubscribeEvent
    public static void onLoad(final ModConfig.Loading configEvent) {

    }

    @SubscribeEvent
    public static void onReload(final ModConfig.Reloading configEvent) {
    }
}
