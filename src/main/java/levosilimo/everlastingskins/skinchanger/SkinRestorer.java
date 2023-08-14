package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.EverlastingSkins;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

public class SkinRestorer {

    private static SkinStorage skinStorage;
    private static SkinIO skinIO;
    public static SkinIO getSkinIO(){return skinIO;}
    public static SkinStorage getSkinStorage() {
        return skinStorage;
    }
    public static MinecraftServer server;

    public static void init(FMLServerStartingEvent event) {
        server = event.getServer();
        skinIO = new SkinIO(event.getServer().getDataDirectory().toPath().resolve("EverlastingSkins"));
        skinStorage = new SkinStorage(skinIO);
        event.registerServerCommand(new SkinCommand());
    }

    private static void applySkin(EntityPlayerMP playerEntity, Property skin) {
        playerEntity.getGameProfile().getProperties().removeAll("textures");
        playerEntity.getGameProfile().getProperties().put("textures", skin);
    }

    public static void onClosedServer(FMLServerStoppedEvent event) {
        server = null;
    }

    @SubscribeEvent
    public void onPlayerLoading(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
        EverlastingSkins.logger.warn(event.player.getUniqueID());
        if (SkinRestorer.getSkinStorage().getSkin(event.player.getUniqueID()) == SkinStorage.DEFAULT_SKIN)
            SkinRestorer.getSkinStorage().setSkin(event.player.getUniqueID(), MojangSkinProvider.getSkin(event.player.getGameProfile().getName()));

        applySkin(((EntityPlayerMP)event.player), SkinRestorer.getSkinStorage().getSkin(event.player.getUniqueID()));
    }

    @SubscribeEvent
    public void onPlayerLeaving(PlayerEvent.PlayerLoggedOutEvent event) {
        SkinRestorer.getSkinStorage().removeSkin(event.player.getUniqueID());
    }

    public static void onClosingServer(FMLServerStoppingEvent event) {
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            SkinRestorer.getSkinStorage().removeSkin(player.getUniqueID());
        }
    }
}