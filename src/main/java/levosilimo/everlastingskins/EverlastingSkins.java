/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins;

import com.google.common.collect.Lists;
import levosilimo.everlastingskins.integration.placeholderapi.PlaceholderApiHook;
import levosilimo.everlastingskins.metrics.MetricsDumper;
import levosilimo.everlastingskins.metrics.NetworkMetricsHandler;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.forge.ForgePermissionService;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import net.minecraft.network.Connection;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.network.ConnectionStartEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Mod(EverlastingSkins.MOD_ID)
public class EverlastingSkins {
    public static String language = "en_us";
    public static final Logger logger = LogManager.getLogger();
    public static final String MOD_ID = "everlastingskins";
    public static final String MOD_NAME = "Everlasting Skins";
    public static final String VERSION = "4.1.0";
    public static final List<String> languages = Lists.newArrayList();

    public EverlastingSkins() {
        PermissionServiceManager.init();
        ForgePermissionService.registerNodes();
        MinecraftForge.EVENT_BUS.addListener(ForgePermissionService::onPermissionGather);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_CONFIG);
        MinecraftForge.EVENT_BUS.register(new SkinRestorer());
        MinecraftForge.EVENT_BUS.register(new MetricsDumper());
        MinecraftForge.EVENT_BUS.addListener(EverlastingSkins::onConnectionStart);
        PlaceholderApiHook.tryRegister();
    }

    /**
     * Attaches the per-connection byte counter when the channel is already
     * live; connections whose pipeline is not yet configured are covered by
     * NetworkMetricsHandler#getOrAttach on first refresh.
     */
    private static void onConnectionStart(ConnectionStartEvent event) {
        if (event.isClient()) return;
        Connection connection = event.getConnection();
        if (connection.channel() != null) {
            NetworkMetricsHandler.getOrAttach(connection);
        }
    }

    @Mod.EventBusSubscriber(modid = EverlastingSkins.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class EverlastingSkinsEventHandlers {
        @SubscribeEvent
        public static void onServerAboutToStart(ServerAboutToStartEvent event) {
            PermissionServiceManager.init();
        }

        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (!PermissionServiceManager.getActiveBackendName().startsWith("LuckPerms")) {
                return;
            }
            UUID uuid = event.getEntity().getUUID();
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
}
