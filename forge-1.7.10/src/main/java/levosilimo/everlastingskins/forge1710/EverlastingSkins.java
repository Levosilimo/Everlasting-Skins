/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge1710;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import levosilimo.everlastingskins.broadcast.SkinBroadcaster;
import levosilimo.everlastingskins.forge1710.metrics.MetricsDumper;
import levosilimo.everlastingskins.forge1710.util.I18nUtils;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.VanillaPermissionService;
import levosilimo.everlastingskins.permission.forge.ForgePermissionService;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 1.7.10 entry point. LaunchWrapper surface (pre-ModLauncher): FML classes
 * live under {@code cpw.mods.fml.*}, not {@code net.minecraftforge.fml.*}
 * (that split is 1.8+).
 *
 * <p>Skin storage is keyed by UUID ONLY (memory #1123): the lane extracts
 * the UUID at the binding boundary via {@code player.getGameProfile().getId()}
 * — 1.7.10 has no {@code getPersistentID()} (1.8+) — and :common never sees
 * a player object. {@link SkinRestorer} and the command layer both hand
 * UUIDs into {@code SkinStorage}; the GameProfile is only mutated after the
 * lookup.
 *
 * <p>Network surface: FML 1.7.10 removed the 1.6.4 {@code @NetworkMod}
 * annotation (verified absent from 10.13.4.1614); the skin-broadcast channel
 * is registered with the netty-based {@link SkinBroadcaster} during init.
 */
@Mod(
    modid = EverlastingSkins.MOD_ID,
    name = EverlastingSkins.MOD_NAME,
    version = EverlastingSkins.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    // FML 7.x handshake-parity opt-out (FIX-6): FML 7 enforces mod-list
    // parity at join — a server mod absent from the client's mod list is
    // rejected via NetworkModHolder's DefaultNetworkChecker ("Mod
    // rejections [everlastingskins]") unless the mod opts out. FML 7's
    // @Mod has no serverSideOnly (that is 1.8+); acceptableRemoteVersions
    // = "*" is the canonical opt-out — the holder constructor special-cases
    // exactly "*" to IgnoredChecker (accepts any remote incl. absence, i.e.
    // vanilla clients). Same pattern as the 1.8.9/1.10.2 lanes.
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
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // :common removed init() — per-version bootstrap registers candidates
        // itself; highest priority wins (Forge ops 10 > Vanilla 0).
        PermissionServiceManager.registerService(new VanillaPermissionService());
        ForgePermissionService.register();
        // 1.7.10 skin-broadcast channel (replaces the removed @NetworkMod).
        SkinBroadcaster.init();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        MinecraftForge.EVENT_BUS.register(new MetricsDumper());
        SkinRestorer.onServerStarting(event);
        I18nUtils.loadAll();
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        SkinRestorer.onServerStopping(event);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        SkinRestorer.onPlayerLoggedIn(event);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SkinRestorer.onPlayerLoggedOut(event);
    }
}
