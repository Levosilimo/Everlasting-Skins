/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins;

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
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Mod(
    modid = EverlastingSkins.MOD_ID,
    name = EverlastingSkins.MOD_NAME,
    version = EverlastingSkins.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    // NOT serverSideOnly: FML 1.12.2's handshake hard-rejects a client whose
    // mod list lacks any mod in the server's NetworkRegistry (DefaultNetwork-
    // Checker: "Requires ... but mod is not found on client"), and every mod
    // gets a registry holder regardless of channels. serverSideOnly would
    // disable the mod client-side, drop it from the client's ModList message,
    // and break every modded-client join (verified live in the command-driven
    // E2E trial: client timed out in the handshake). The client-side lifecycle
    // stays inert (preInit/init are no-ops without a server), mirroring the
    // forge-1.7.10 lane which omits the flag.
    acceptableRemoteVersions = "*"
)
public class EverlastingSkins {
    public static final String MOD_ID = "everlastingskins";
    public static final String MOD_NAME = "EverlastingSkins";
    public static final String VERSION = "@VERSION@";
    public static final Logger logger = LogManager.getLogger(MOD_ID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        PlaceholderApiHook.tryRegister();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // :common removed init() — per-version bootstrap registers candidates
        // itself; highest priority wins (LuckPerms 20 > Forge 10 > Vanilla 0).
        PermissionServiceManager.registerService(new VanillaPermissionService());
        LuckPermsPermissionService luckPerms = LuckPermsPermissionService.tryCreate();
        if (luckPerms != null) {
            PermissionServiceManager.registerService(luckPerms);
        }
        ForgePermissionService.registerNodes(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        MinecraftForge.EVENT_BUS.register(new SkinRestorer());
        MinecraftForge.EVENT_BUS.register(new MetricsDumper());
        SkinRestorer.onServerStarting(event);
        I18nUtils.loadAll();
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        SkinRestorer.onServerStopping();
    }

    @SubscribeEvent
    public void onServerConnectionFromClient(FMLNetworkEvent.ServerConnectionFromClientEvent event) {
        NetworkMetricsHandler.getOrAttach(event.getManager());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!PermissionServiceManager.getActiveBackendName().startsWith("LuckPerms")) {
            return;
        }
        UUID uuid = event.player.getUniqueID();
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
