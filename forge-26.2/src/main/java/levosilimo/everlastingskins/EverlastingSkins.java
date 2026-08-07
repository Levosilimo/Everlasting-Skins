/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins;

import levosilimo.everlastingskins.forge26.metrics.MetricsDumper;
import levosilimo.everlastingskins.forge26.metrics.NetworkMetricsHandler;
import levosilimo.everlastingskins.forge26.permission.LuckPermsPermissionService;
import levosilimo.everlastingskins.forge26.permission.VanillaPermissionService;
import levosilimo.everlastingskins.forge26.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.forge26.util.I18nUtils;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.forge.ForgePermissionService;
import net.minecraft.network.Connection;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.network.ConnectionStartEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Forge 26.2 binding for {@code :common} (Minecraft 26.2, Forge 65.0.9,
 * Java 25, unobfuscated MC, EventBus 7).
 *
 * <p>Constraint restated per the project storage contract (memory #1123):
 * {@code :common}'s {@link levosilimo.everlastingskins.skinchanger.SkinStorage}
 * is keyed by UUID ONLY — the UUID is extracted at this lane boundary (e.g.
 * {@code player.getUUID()}) and no {@code ServerPlayer}/{@code EntityPlayer}
 * object ever crosses into {@code :common}. The binding layer is the only
 * place Forge/Minecraft types appear.
 *
 * <p>EventBus 7 note: Forge 65.x dropped {@code @Mod.EventBusSubscriber} and
 * the static-handler path. Every listener is registered explicitly against
 * the event's typed {@code EventBus<T>} (e.g. {@code ServerStartingEvent.BUS});
 * the module-local {@code net.minecraftforge:eventbus-validator} AP enforces
 * valid listener signatures at build time.
 */
@Mod(EverlastingSkins.MOD_ID)
public class EverlastingSkins {
    public static String language = "en_us";
    public static final Logger logger = LogManager.getLogger();
    public static final String MOD_ID = "everlastingskins";
    public static final String MOD_NAME = "Everlasting Skins";
    public static final String VERSION = "2.1.0-beta.1";

    public EverlastingSkins() {
        I18nUtils.loadAll();
        registerPermissionServices();
        ForgePermissionService.registerNodes();
        registerEventListeners();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_CONFIG);
    }

    private static void registerEventListeners() {
        // SkinRestorer lifecycle: server start/stop + player login/logout.
        SkinRestorer restorer = new SkinRestorer();
        ServerStartingEvent.BUS.addListener(restorer::onInitializeServer);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(restorer::onPlayerLoggedIn);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(restorer::onPlayerLoggedOut);
        ServerStoppingEvent.BUS.addListener(restorer::onServerStopping);
        // Periodic metrics.json dump (server tick, END phase).
        TickEvent.ServerTickEvent.Post.BUS.addListener(new MetricsDumper()::onServerTick);
        // Forge permission node registration (PermissionGatherEvent).
        PermissionGatherEvent.Nodes.BUS.addListener(ForgePermissionService::onPermissionGather);
        // Per-connection byte counters for the metrics view.
        ConnectionStartEvent.BUS.addListener(EverlastingSkins::onConnectionStart);
        // /skin command registration.
        RegisterCommandsEvent.BUS.addListener(CommandRegistrationHandler::onRegisterCommands);
        // Config-dependent re-registration once the server is about to start.
        ServerAboutToStartEvent.BUS.addListener(EverlastingSkins::onServerAboutToStart);
        // LuckPerms user preload so permission checks don't fall back to ops.
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(EverlastingSkins::onPlayerLoggedInPreload);
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

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        I18nUtils.loadAll();
        // Config file is loaded by now, so this picks up the real values.
        registerPermissionServices();
    }

    /**
     * Preloads the LuckPerms user when the active backend is LuckPerms, so
     * subsequent permission checks hit the cached user instead of falling
     * back to vanilla op levels (off the login thread).
     */
    private static void onPlayerLoggedInPreload(PlayerEvent.PlayerLoggedInEvent event) {
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
