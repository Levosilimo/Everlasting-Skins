package levosilimo.everlastingskins;
import levosilimo.everlastingskins.enums.LanguageEnum;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod.EventBusSubscriber
public class Config {

    public static final String CATEGORY_GENERAL = "general";

    public static ForgeConfigSpec COMMON_CONFIG;

    public static ForgeConfigSpec.EnumValue<LanguageEnum> LANGUAGE;
    public static ForgeConfigSpec.BooleanValue TOGGLE;


    static {
        ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
        COMMON_BUILDER.push("Messages");
        LANGUAGE = COMMON_BUILDER.comment("Language of mod messages").defineEnum("language",LanguageEnum.English);
        TOGGLE = COMMON_BUILDER.comment("Display mod messages").define("display",true);
        COMMON_BUILDER.pop();
        COMMON_CONFIG = COMMON_BUILDER.build();
    }

    @SubscribeEvent
    public static void onLoad(final ModConfig.Loading configEvent) {

    }

    @SubscribeEvent
    public static void onReload(final ModConfig.Reloading configEvent) {
    }
}
