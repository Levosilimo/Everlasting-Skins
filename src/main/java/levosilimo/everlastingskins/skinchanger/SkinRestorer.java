package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SkinRestorer {

    private static volatile SkinStorage skinStorage;
    private static volatile SkinIO skinIO;
    private static volatile MinecraftServer server;

    @Nullable
    public static SkinStorage getSkinStorage() {
        return skinStorage;
    }

    public static MinecraftServer getServer() {
        return server;
    }

    public static void onServerStarting(FMLServerStartingEvent event) {
        server = event.getServer();
        Path dataDir = server.getFile("EverlastingSkins").toPath();
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            EverlastingSkins.logger.error("Failed to create skin data directory", e);
        }
        skinIO = new SkinIO(dataDir);
        skinStorage = new SkinStorage(skinIO);
        Config.load(new File(server.getFile("config"), "everlastingskins.cfg"));
        SkinCommand.register(server);
    }

    /**
     * Applies the player's saved skin on login.
     *
     * NOTE: PlayerLoggedInEvent fires after the player is already visible to
     * other players on the server. This means there is a brief flash of the
     * default/vanilla skin before the saved custom skin is applied.
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;

        if (skinStorage.hasDefaultSkin(player.getUniqueID())) {
            MojangSkinDataResult skinDataResult = SkinCommand.mojangAPI.getSkin(player.getGameProfile().getName()).orElse(null);
            if (skinDataResult != null) {
                skinStorage.setSkin(player.getUniqueID(), skinDataResult.skinProperty());
            }
        }

        CustomSkinProperty skin = skinStorage.getSkin(player.getUniqueID());
        if (skin != null && !skin.isEmpty()) {
            player.getGameProfile().getProperties().removeAll("textures");
            player.getGameProfile().getProperties().put("textures", skin.getOriginalProperty());
        }
        // When skin is null or empty (no custom skin on disk), leave the profile
        // unmutated — the client renders Steve or Alex based on UUID hash.
    }

    /**
     * Saves the player's skin data on disconnect so it persists across sessions.
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (skinStorage.getSkin(player.getUniqueID()) != null) {
            skinStorage.saveSkin(player.getUniqueID());
        }
    }

    /**
     * Saves all online players' skin data during graceful server shutdown.
     */
    public static void onServerStopping() {
        if (server == null) return;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            skinStorage.saveSkin(player.getUniqueID());
        }
    }
}
