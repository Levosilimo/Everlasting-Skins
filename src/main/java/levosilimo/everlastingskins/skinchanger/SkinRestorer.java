package levosilimo.everlastingskins.skinchanger;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.FolderName;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import java.nio.file.Path;

public class SkinRestorer {

    private static SkinStorage skinStorage;
    private static SkinIO skinIO;
    public static SkinStorage getSkinStorage() {
        return skinStorage;
    }
    public static MinecraftServer server;
    @SubscribeEvent
    public void onInitializeServer(FMLServerStartingEvent event) {
        server = event.getServer();
        Path path = event.getServer().getWorldPath(new FolderName("EverlastingSkins"));
        skinIO=new SkinIO(path);
        skinStorage = new SkinStorage(skinIO);
    }
}
