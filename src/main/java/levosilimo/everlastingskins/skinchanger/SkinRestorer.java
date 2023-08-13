package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.properties.Property;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;

public class SkinRestorer {

    private static SkinStorage skinStorage;
    private static SkinIO skinIO;
    public static SkinIO getSkinIO(){return skinIO;}
    public static SkinStorage getSkinStorage() {
        return skinStorage;
    }
    public static MinecraftServer server;

    private static void applySkin(EntityPlayerMP playerEntity, Property skin) {
        playerEntity.getGameProfile().getProperties().removeAll("textures");
        playerEntity.getGameProfile().getProperties().put("textures", skin);
    }
    @SubscribeEvent
    public void onInitializeServer(FMLServerStartingEvent event) {
        server = event.getServer();
        skinIO = new SkinIO(event.getServer().getDataDirectory().toPath().resolve("EverlastingSkins"));
        skinStorage = new SkinStorage(skinIO);
        SkinCommand.register(event.getCommandDispatcher());
    }

    @SubscribeEvent
    public void onClosedServer(FMLServerStoppedEvent event) {
        server = null;
    }

    @SubscribeEvent
    public void onPlayerLoading(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
        if (SkinRestorer.getSkinStorage().getSkin(event.getPlayer().getUniqueID()) == SkinStorage.DEFAULT_SKIN)
            SkinRestorer.getSkinStorage().setSkin(event.getPlayer().getUniqueID(), MojangSkinProvider.getSkin(event.getPlayer().getGameProfile().getName()));

        applySkin(((EntityPlayerMP)event.getPlayer()), SkinRestorer.getSkinStorage().getSkin(event.getPlayer().getUniqueID()));
    }

    @SubscribeEvent
    public void onPlayerLeaving(PlayerEvent.PlayerLoggedOutEvent event) {
        SkinRestorer.getSkinStorage().removeSkin(event.getPlayer().getUniqueID());
    }

    @SubscribeEvent
    public void onServerClosing(FMLServerStoppingEvent event) {
        for (EntityPlayerMP player : event.getServer().getPlayerList().getPlayers()) {
            SkinRestorer.getSkinStorage().removeSkin(player.getUniqueID());
        }
    }
}