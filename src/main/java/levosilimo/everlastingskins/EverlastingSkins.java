package levosilimo.everlastingskins;

import com.google.common.collect.Lists;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;


@Mod(EverlastingSkins.MOD_ID)
public class EverlastingSkins {
    public static String language = "en_us";
    public static final Logger logger = LogManager.getLogger();
    public static final String MOD_ID = "everlastingskins";
    public static final String MOD_NAME = "Everlasting Skins";
    public static final String VERSION = "4.1.0";
    public static final List<String> languages = Lists.newArrayList();
    public EverlastingSkins() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_CONFIG);
        MinecraftForge.EVENT_BUS.register(new SkinRestorer());
    }
}