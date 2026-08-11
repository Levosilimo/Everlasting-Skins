/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * 1.10.2 login/logout binding for the {@code :common} {@link SkinStorage}
 * (era-adapted mirror of the mc1.12.2 SkinRestorer event handlers).
 *
 * <p>Login-apply: the stored skin is applied synchronously by mutating the
 * GameProfile's textures property — the skin is already on disk, so no HTTP
 * runs on the login thread (the async default-skin resolution of the
 * mc1.12.2 lane is Config-driven and does not exist on this lane). NOTE:
 * PlayerLoggedInEvent fires after the player is already visible to other
 * players, so there is a brief flash of the default skin before the saved
 * custom skin applies.
 *
 * <p>Logout: persist the player's skin so it survives the session.
 */
public class SkinLoginHandler {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        SkinMetrics.INSTANCE.recordPlayerJoined();

        SkinStorage storage = SkinRestorer.getSkinStorage();
        if (storage == null) return;
        CustomSkinProperty skin = storage.getSkin(player.getUniqueID());
        if (skin != null && !skin.isEmpty()) {
            player.getGameProfile().getProperties().removeAll("textures");
            player.getGameProfile().getProperties().put("textures", skin.getOriginalProperty());
        }
        // When skin is null or empty (no custom skin on disk), leave the
        // profile unmutated — the client renders Steve or Alex by UUID hash.
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        SkinMetrics.INSTANCE.recordPlayerLeft();

        SkinStorage storage = SkinRestorer.getSkinStorage();
        if (storage != null && storage.getSkin(player.getUniqueID()) != null) {
            storage.saveSkin(player.getUniqueID());
        }
    }
}
