package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.FileUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

public class SkinRestorer {

    // Singleton storage instance, initialised via constructor injection in onInitializeServer.
    private static volatile SkinStorage skinStorage;
    private static volatile SkinIO skinIO;
    public static volatile MinecraftServer server;

    @Nullable
    public static SkinStorage getSkinStorage() {
        return skinStorage;
    }

    @SubscribeEvent
    public void onInitializeServer(ServerStartingEvent event) {
        server = event.getServer();
        Path path = event.getServer().getFile("EverlastingSkins");
        try {
            FileUtil.createDirectoriesSafe(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        skinIO = new SkinIO(path);
        skinStorage = new SkinStorage(skinIO);
    }

    /**
     * Applies the player's saved skin on login.
     *
     * NOTE: PlayerLoggedInEvent fires after the player is already visible to
     * other players on the server. This means there is a brief flash of the
     * default/vanilla skin before the saved custom skin is applied — a known
     * timing trade-off versus mixing into PlayerList#placeNewPlayer HEAD,
     * which applied the skin before the player became visible. The Forge event
     * is preferred for maintainability; the visual flash is imperceptible under
     * normal network conditions on a local server.
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SkinMetrics.INSTANCE.recordPlayerJoined();

        if (skinStorage.hasDefaultSkin(player.getUUID())) {
            MojangSkinDataResult skinDataResult = SkinCommand.getMojangAPI().getSkin(player.getGameProfile().getName()).orElse(null);
            if (skinDataResult != null) {
                skinStorage.setSkin(player.getUUID(), skinDataResult.skinProperty());
            }
        }

        CustomSkinProperty skin = skinStorage.getSkin(player.getUUID());
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
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        SkinMetrics.INSTANCE.recordPlayerLeft();
        SkinCommand.getLastRefreshByPlayer().remove(uuid);
        SkinCommand.clearRateLimitState(uuid);
        if (skinStorage.getSkin(uuid) != null) {
            skinStorage.saveSkin(uuid);
            skinIO.flushPending();
        }
    }

    /**
     * Saves all online players' skin data during graceful server shutdown.
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            skinStorage.saveSkin(player.getUUID());
        }
        skinIO.flushPending();
        SkinIO.shutdown();
    }
}
