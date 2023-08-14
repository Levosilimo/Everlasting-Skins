package levosilimo.everlastingskins;

import com.google.common.collect.Lists;
import levosilimo.everlastingskins.enums.LanguageEnum;
import levosilimo.everlastingskins.skinchanger.SkinCommand;
import levosilimo.everlastingskins.skinchanger.SkinIO;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.skinchanger.SkinStorage;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static net.minecraftforge.common.config.Configuration.CATEGORY_GENERAL;


@Mod(modid = EverlastingSkins.MOD_ID)
public class EverlastingSkins {
    public static String language = "en_us";
    public static final Logger logger = LogManager.getLogger();
    public static final String MOD_ID = "everlastingskins";
    public static final String MOD_NAME = "Everlasting Skins";
    public static final String VERSION = "4.1.0";
    public static final List<String> languages = Lists.newArrayList();
    public static Configuration config;
    public static final ExecutorService skinCommandExecutor = Executors.newCachedThreadPool();
    public EverlastingSkins() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new SkinRestorer());
        File cfgFile = new File(Loader.instance().getConfigDir(), "everlastingskins.cfg");
        config = new Configuration(cfgFile);
        syncConfig(true);
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event)
    {
        if (MOD_ID.equals(event.getModID()))
        {
            syncConfig(false);
        }
    }

    @Mod.EventHandler
    public void onInitializeServer(FMLServerStartingEvent event) {
        SkinRestorer.init(event);
    }

    @Mod.EventHandler
    public void onClosedServer(FMLServerStoppedEvent event) {
        SkinRestorer.onClosedServer(event);
    }

    @Mod.EventHandler
    public void onServerClosing(FMLServerStoppingEvent event) {
        SkinRestorer.onClosingServer(event);
    }


    private static void syncConfig(boolean load)
    {
        List<String> propOrder = new ArrayList<String>();

        if (!config.isChild)
        {
            if (load)
            {
                config.load();
            }
        }
        Property prop;
        prop = config.get(CATEGORY_GENERAL, "language", LanguageEnum.English.toString(), "Language of mod messages", LanguageEnum.getStringValues());
        language = prop.getString();
        propOrder.add(prop.getName());
        config.setCategoryPropertyOrder(CATEGORY_GENERAL, propOrder);
        if (config.hasChanged())
        {
            config.save();
        }
    }
}