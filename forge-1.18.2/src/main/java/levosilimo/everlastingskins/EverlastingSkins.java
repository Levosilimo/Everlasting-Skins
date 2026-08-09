/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins;

import com.google.common.collect.Lists;
import levosilimo.everlastingskins.integration.discordsrv.DiscordSrvConfig;
import levosilimo.everlastingskins.integration.placeholderapi.PlaceholderApiHook;
import levosilimo.everlastingskins.metrics.MetricsDumper;
import levosilimo.everlastingskins.metrics.NetworkMetricsHandler;
import levosilimo.everlastingskins.permission.LuckPermsPermissionService;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.VanillaPermissionService;
import levosilimo.everlastingskins.permission.forge.ForgePermissionService;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.util.I18nUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerNegotiationEvent;
import net.minecraftforge.event.TickEvent;
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
        I18nUtils.loadAll();
        registerPermissionServices();
        ForgePermissionService.registerNodes();
        MinecraftForge.EVENT_BUS.register(new SkinRestorer());
        MinecraftForge.EVENT_BUS.addListener(new MetricsDumper()::onServerTick);
        registerCommonConfig();
        MinecraftForge.EVENT_BUS.addListener(ForgePermissionService::onPermissionGather);
        MinecraftForge.EVENT_BUS.addListener(EverlastingSkins::onPlayerNegotiation);
        PlaceholderApiHook.tryRegister();
    }

    /**
     * ModLoadingContext.get().registerConfig is not deprecated in Forge 40
     * (1.18.2), so no suppression is required here (the 1.20.1 lane carried
     * @SuppressWarnings("removal") for Forge 47's deprecation).
     */
    private static void registerCommonConfig() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_CONFIG);
    }

    /**
     * Attaches the per-connection byte counter when the channel is already
     * live; connections whose pipeline is not yet configured are covered by
     * NetworkMetricsHandler#getOrAttach on first refresh.
     *
     * <p>1.20.1 delta: Forge 47 has no ConnectionStartEvent (1.20.5+
     * addition) and no ServerConnectionInitializedEvent (1.20.2+
     * addition); PlayerNegotiationEvent is the closest 1.20.1 analog -
     * it fires server-side during login negotiation with a live
     * Connection. The 1.21 isClient() guard is dropped: negotiation
     * events only fire on the server side.
     */
    private static void onPlayerNegotiation(PlayerNegotiationEvent event) {
        NetworkMetricsHandler.getOrAttach(event.getConnection());
    }

    /**
     * Registers the permission backends :common can see. Forge (highest
     * relevance for this lane) is registered by
     * {@link ForgePermissionService#registerNodes()}; LuckPerms registers
     * only when its API is present at runtime.
     */
    private static void registerPermissionServices() {
        PermissionServiceManager.registerService(LuckPermsPermissionService.tryCreate());
        PermissionServiceManager.registerService(new VanillaPermissionService());
    }

    @Mod.EventBusSubscriber(modid = EverlastingSkins.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class EverlastingSkinsEventHandlers {
        @SubscribeEvent
        public static void onServerAboutToStart(ServerAboutToStartEvent event) {
            I18nUtils.loadAll();
            // Config file is loaded by now, so this picks up the real values.
            registerPermissionServices();
            DiscordSrvConfig.configure(Config.DISCORDSRV_ENABLED.get(), Config.DISCORDSRV_CHANNEL_ID.get());
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
