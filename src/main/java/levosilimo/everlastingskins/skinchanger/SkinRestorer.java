package levosilimo.everlastingskins.skinchanger;

import net.minecraft.FileUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.nio.file.Path;

public class SkinRestorer {

    private static SkinStorage skinStorage;
    private static SkinIO skinIO;
    public static SkinStorage getSkinStorage() {
        return skinStorage;
    }
    public static MinecraftServer server;
    @SubscribeEvent
    public void onInitializeServer(ServerStartingEvent event) {
        server = event.getServer();
        Path path = event.getServer().getFile("EverlastingSkins");
        try {
            FileUtil.createDirectoriesSafe(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        skinIO=new SkinIO(path);
        skinStorage = new SkinStorage(skinIO);
    }
}
