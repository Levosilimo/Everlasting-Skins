package levosilimo.everlastingskins.skinchanger;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class SkinRestorer {

    private static SkinStorage skinStorage;
    private static SkinIO skinIO;
    public static SkinIO getSkinIO(){return skinIO;}
    public static SkinStorage getSkinStorage() {
        return skinStorage;
    }
    public static MinecraftServer server;
    @SubscribeEvent
    public void onInitializeServer(ServerStartingEvent event) {
        server = event.getServer();
        skinIO=new SkinIO(event.getServer().getWorldPath(new LevelResource("EverlastingSkins")));
        skinStorage = new SkinStorage(skinIO);
    }

    @SubscribeEvent
    public void onClosedServer(ServerStoppedEvent event) {
        server = null;
    }
}