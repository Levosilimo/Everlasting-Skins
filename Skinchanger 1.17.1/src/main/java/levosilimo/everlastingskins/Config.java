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


    static {
        ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
        COMMON_BUILDER.comment("Language Setting").push(CATEGORY_GENERAL);
        LANGUAGE = COMMON_BUILDER.comment("Language of mod messages").defineEnum("language",LanguageEnum.English);
        COMMON_BUILDER.pop();
        COMMON_CONFIG = COMMON_BUILDER.build();
    }
}
