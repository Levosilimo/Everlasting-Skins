/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.command.SkinActionCommand;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.FileUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SkinRestorer {

    // Singleton storage instance, initialised via constructor injection in onInitializeServer.
    private static volatile SkinStorage skinStorage;
    private static volatile SkinIO skinIO;
    public static volatile MinecraftServer server;

    /** Off-login-thread executor for the default-skin Mojang restore fetch. */
    private static final ExecutorService loginExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "everlastingskins-login");
        t.setDaemon(true);
        return t;
    });

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
        // Startup integrity sweep: quarantine bit-rotten records before the
        // first player logs in, so SkinStorage only ever sees verified files.
        skinIO.validateAllFiles();
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
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        SkinMetrics.INSTANCE.recordPlayerJoined();

        UUID uuid = player.getUUID();
        boolean hasCustomSkin = !skinStorage.hasDefaultSkin(uuid);
        // applyForPremium=false (default): the default skin only applies when the
        // player has no saved custom skin. true: it also overrides players WITH a
        // saved custom skin — display-only; the stored custom skin is preserved.
        boolean applyDefault = Config.DEFAULT_SKINS_APPLY_FOR_PREMIUM.get() || !hasCustomSkin;

        if (applyDefault) {
            // Never block the login thread on the 3-provider HTTP chain: fetch
            // on the shared executor and apply back on the server thread.
            MinecraftServer srv = server;
            String name = player.getGameProfile().getName();
            loginExecutor.submit(() -> {
                // Config list takes precedence; the static default-skin.properties
                // restore is the fallback when the list is disabled or empty.
                if (Config.DEFAULT_SKINS_ENABLED.get() && !Config.DEFAULT_SKINS_LIST.get().isEmpty()) {
                    Property defaultProp = DefaultSkinResolver.resolveDefault(
                            Config.DEFAULT_SKINS_LIST.get(), SkinCommand.getMojangAPI());
                    if (defaultProp == null) return;
                    srv.execute(() -> {
                        // Re-check: skip unless the player still qualifies — unless
                        // applyForPremium explicitly allows overriding a saved custom skin.
                        if (!Config.DEFAULT_SKINS_APPLY_FOR_PREMIUM.get() && !skinStorage.hasDefaultSkin(uuid)) return;
                        ServerPlayer online = srv.getPlayerList().getPlayer(uuid);
                        if (online == null) return;
                        online.getGameProfile().getProperties().removeAll("textures");
                        online.getGameProfile().getProperties().put("textures", defaultProp);
                    });
                    return;
                }
                MojangSkinDataResult skinDataResult = SkinCommand.getMojangAPI().getSkin(name).orElse(null);
                if (skinDataResult == null) return;
                CustomSkinProperty property = skinDataResult.skinProperty();
                srv.execute(() -> {
                    if (skinStorage.hasDefaultSkin(uuid)) {
                        skinStorage.setSkin(uuid, property);
                        ServerPlayer online = srv.getPlayerList().getPlayer(uuid);
                        if (online != null) {
                            online.getGameProfile().getProperties().removeAll("textures");
                            online.getGameProfile().getProperties().put("textures", property.getOriginalProperty());
                        }
                    }
                });
            });
            return;
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
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        UUID uuid = player.getUUID();
        SkinMetrics.INSTANCE.recordPlayerLeft();
        SkinActionCommand.getLastRefreshByPlayer().remove(uuid);
        SkinActionCommand.clearRateLimitState(uuid);
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
