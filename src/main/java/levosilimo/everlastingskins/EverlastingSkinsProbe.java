package levosilimo.everlastingskins;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = EverlastingSkinsProbe.MOD_ID,
    name = EverlastingSkinsProbe.MOD_NAME,
    version = EverlastingSkinsProbe.VERSION,
    acceptedMinecraftVersions = "[1.12.2]"
)
public class EverlastingSkinsProbe {
    public static final String MOD_ID = "everlastingskins";
    public static final String MOD_NAME = "EverlastingSkins";
    public static final String VERSION = "@VERSION@";
    private static final Logger logger = LogManager.getLogger(MOD_NAME);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger.info("Phase 3 probe preInit on 1.12.2");
    }
}
