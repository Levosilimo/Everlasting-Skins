package levosilimo.everlastingskins;

import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = EverlastingSkins.MOD_ID,
    name = EverlastingSkins.MOD_NAME,
    version = EverlastingSkins.VERSION,
    acceptedMinecraftVersions = "[1.12.2]"
)
public class EverlastingSkins {
    public static final String MOD_ID = "everlastingskins";
    public static final String MOD_NAME = "EverlastingSkins";
    public static final String VERSION = "@VERSION@";
    public static final Logger logger = LogManager.getLogger(MOD_ID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Config loaded by Config.load; SkinRestorer registered on serverStarting.
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        MinecraftForge.EVENT_BUS.register(new SkinRestorer());
        SkinRestorer.onServerStarting(event);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        SkinRestorer.onServerStopping();
    }
}
