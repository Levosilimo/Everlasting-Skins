/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge102;

import levosilimo.everlastingskins.broadcast.SkinBroadcaster;
import levosilimo.everlastingskins.command.SkinRestorerCommand;
import levosilimo.everlastingskins.e2e.E2E;
import levosilimo.everlastingskins.permission.forge.ForgePermissionService;
import levosilimo.everlastingskins.skinchanger.SkinCommand;
import levosilimo.everlastingskins.skinchanger.SkinLoginHandler;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 1.10.2 entry point — LaunchWrapper / FML 10.x (pre-ModLauncher): a plain
 * {@code @Mod}-annotated class, no mod constructor, no mods.toml (the
 * metadata lives in mcmod.info).
 *
 * Storage constraint (memory #1123): {@code :common}'s SkinStorage is keyed
 * by UUID only — no EntityPlayer/GameProfile object ever crosses the lane
 * boundary into :common. Every binding in this lane extracts the player
 * UUID at this layer (see the skinchanger and permission adapters).
 */
@Mod(
    modid = EverlastingSkins.MOD_ID,
    name = EverlastingSkins.MOD_NAME,
    version = EverlastingSkins.VERSION,
    acceptedMinecraftVersions = "[1.10.2]",
    // Dual-side since slice 2: the real-client E2E's in-jar driver runs on
    // the client (the lane has no HeadlessMC specifics build); the mod has
    // no client-side behaviors — serverStarting/EVENT_BUS registrations are
    // server-only events, and the E2E driver gates itself on
    // -Deverlastingskins.e2e=true (never active in production).
    acceptableRemoteVersions = "*"
)
public class EverlastingSkins {

    public static final String MOD_ID = "everlastingskins";
    public static final String MOD_NAME = "Everlasting Skins";
    public static final String VERSION = "@VERSION@";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Mod.Instance(MOD_ID)
    public static EverlastingSkins instance;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Register the op-level backend with the fail-closed manager
        // (PermissionServiceManager stays fail-closed until this runs).
        ForgePermissionService.register();
        // Skin broadcast channel (SimpleNetworkWrapper, pre-ModLauncher).
        SkinBroadcaster.init();
        // Real-client E2E support (in-jar driver; no-op without the gate).
        E2E.install();
        LOGGER.info("{} {} initializing (server-side)", MOD_NAME, VERSION);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        SkinRestorer.init(event);
        event.registerServerCommand(new SkinRestorerCommand());
        // /skin full-parity surface (set mojang|web|random, source, clear,
        // metrics) + login-apply of stored skins.
        event.registerServerCommand(new SkinCommand());
        MinecraftForge.EVENT_BUS.register(new SkinLoginHandler());
        LOGGER.info("{} server starting", MOD_NAME);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        // Persist all queued storage writes before shutdown.
        SkinRestorer.onServerStopping();
    }
}
