/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins;

import levosilimo.everlastingskins.broadcast.SkinBroadcaster;
import levosilimo.everlastingskins.permission.forge.ForgePermissionService;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 1.8.9 entry point — LaunchWrapper / FML 8.x (pre-ModLauncher): a plain
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
    acceptedMinecraftVersions = "[1.8.9]",
    serverSideOnly = true,
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
        LOGGER.info("{} {} initializing (server-side)", MOD_NAME, VERSION);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        SkinRestorer.init(event);
        LOGGER.info("{} server starting", MOD_NAME);
    }
}
