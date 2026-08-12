/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins;

import com.google.common.collect.Lists;
import levosilimo.everlastingskins.e2e.E2E;
import levosilimo.everlastingskins.integration.placeholderapi.PlaceholderApiHook;
import levosilimo.everlastingskins.metrics.MetricsDumper;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.forge.ForgePermissionService;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.util.I18nUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
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
        ForgePermissionService.registerNodes();        // SkinRestorer has FORGE-bus handlers (login/logout) and FML server
        // lifecycle handlers. 1.16.5's FML server events (FMLServerStarting-
        // Event etc.) are NOT IModBusEvents: ServerLifecycleHooks posts them
        // to MinecraftForge.EVENT_BUS (bytecode-verified). Registering on the
        // mod bus makes EventBus validate @SubscribeEvent args against
        // IModBusEvent and fails mod loading (caught by the slice-4
        // boot-smoke E2E, 2026-08-12) — one FORGE-bus registration only.
        SkinRestorer restorer = new SkinRestorer();
        MinecraftForge.EVENT_BUS.register(restorer);
        // 1.16.5 has no TickEvent.ServerTickEvent.BUS (1.19+ addition) and no
        // getServer() on ServerTickEvent; server ticks are plain EVENT_BUS
        // events here and MetricsDumper resolves the server via
        // ServerLifecycleHooks.getCurrentServer().
        MinecraftForge.EVENT_BUS.addListener(new MetricsDumper()::onServerTick);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_CONFIG);
        PlaceholderApiHook.tryRegister();
        // Real-client full in-jar E2E (slice 3): E2E.install() is
        // shipped-gated by -Deverlastingskins.e2e=true and side-gated via
        // DistExecutor.safeRunWhenOn (client → E2EDriver phase machine,
        // dedicated server → E2ESentinelHook seed). The slice-4 boot-smoke
        // E2EJoinDriver stays in the tree (dormant) as the reference for
        // the launcher-arg privileges gate this driver replaces.
        E2E.install();
    }

    @Mod.EventBusSubscriber(modid = EverlastingSkins.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class EverlastingSkinsEventHandlers {
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

    // 1.16.5 lifecycle events are FML events fired on the FORGE bus
    // (ServerLifecycleHooks posts them to MinecraftForge.EVENT_BUS —
    // bytecode-verified 2026-08-12; they are not IModBusEvents, so a MOD-bus
    // subscriber fails validation at mod load).
    @Mod.EventBusSubscriber(modid = EverlastingSkins.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class EverlastingSkinsServerLifecycle {
        @SubscribeEvent
        public static void onServerAboutToStart(FMLServerAboutToStartEvent event) {
            I18nUtils.loadAll();
        }
    }
}
