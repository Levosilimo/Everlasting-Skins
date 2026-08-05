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
import levosilimo.everlastingskins.permission.LuckPermsPermissionService;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.VanillaPermissionService;
import levosilimo.everlastingskins.permission.forge.ForgePermissionService;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.util.I18nUtils;
import net.minecraft.network.Connection;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.network.ConnectionStartEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
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
        // M2 step 5: PermissionServiceManager lives in /common as a
        // registration registry; the hardcoded init() candidate discovery
        // (vanilla + LuckPerms, highest priority wins) is now this bootstrap.
        // ForgePermissionService.registerNodes() self-registers (priority 10),
        // so the active backend is LuckPerms (20) > Forge (10) > Vanilla (0).
        registerPermissionBackends();
        ForgePermissionService.registerNodes();
        MinecraftForge.EVENT_BUS.register(new SkinRestorer());
        TickEvent.ServerTickEvent.BUS.addListener(new MetricsDumper()::onServerTick);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_CONFIG);
        PermissionGatherEvent.Nodes.BUS.addListener(ForgePermissionService::onPermissionGather);
        ConnectionStartEvent.BUS.addListener(EverlastingSkins::onConnectionStart);
        PlaceholderApiHook.tryRegister();
    }

    /**
     * M2 step 5: registers the /common-managed permission backends. Vanilla is
     * always registered; LuckPerms self-detects and wins on priority. Forge
     * self-registers via {@link ForgePermissionService#registerNodes()}.
     */
    private static void registerPermissionBackends() {
        PermissionServiceManager.registerService(new VanillaPermissionService());
        LuckPermsPermissionService lp = LuckPermsPermissionService.tryCreate();
        if (lp != null) {
            PermissionServiceManager.registerService(lp);
        }
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
            I18nUtils.loadAll();
            // Re-run the bootstrap: registration is idempotent and re-picks up
            // LuckPerms once the server (and its plugins) are actually loaded.
            registerPermissionBackends();
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
