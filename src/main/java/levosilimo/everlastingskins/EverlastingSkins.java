package levosilimo.everlastingskins;

import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.forge.ForgePermissionService;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
        ForgePermissionService.registerNodes(event);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        PermissionServiceManager.init();
        MinecraftForge.EVENT_BUS.register(new SkinRestorer());
        SkinRestorer.onServerStarting(event);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        SkinRestorer.onServerStopping();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!PermissionServiceManager.getActiveBackendName().startsWith("LuckPerms")) {
            return;
        }
        UUID uuid = event.player.getUniqueID();
        new Thread(() -> {
            try {
                Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPerms");
                Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
                Method getApiMethod = providerClass.getMethod("get");
                Object luckPermsApi = getApiMethod.invoke(null);
                Method getUserManagerMethod = luckPermsClass.getMethod("getUserManager");
                Object userManagerObj = getUserManagerMethod.invoke(luckPermsApi);
                Method loadUserMethod = userManagerObj.getClass().getMethod("loadUser", UUID.class);
                Object userFuture = loadUserMethod.invoke(userManagerObj, uuid);
                Method getMethod = userFuture.getClass().getMethod("get", long.class, TimeUnit.class);
                getMethod.invoke(userFuture, 5L, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }).start();
    }
}
