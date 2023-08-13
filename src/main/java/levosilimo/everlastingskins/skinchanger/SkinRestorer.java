package levosilimo.everlastingskins.skinchanger;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;

public class SkinRestorer {

    private static SkinStorage skinStorage;
    private static SkinIO skinIO;
    public static SkinIO getSkinIO(){return skinIO;}
    public static SkinStorage getSkinStorage() {
        return skinStorage;
    }
    public static MinecraftServer server;
    @SubscribeEvent
    public void onInitializeServer(FMLServerStartingEvent event) {
        server = event.getServer();
        skinIO = new SkinIO(event.getServer().getStorageSource().getBaseDir().resolve("EverlastingSkins"));
        skinStorage = new SkinStorage(skinIO);
    }

    @SubscribeEvent
    public void onClosedServer(FMLServerStoppedEvent event) {
        server = null;
    }
}