/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.server.management.PlayerList;

/**
 * Production {@link SkinProfileBroadcaster} for Minecraft 1.10.2. Builds
 * the REMOVE+ADD tab-list pair and delivers it via the live
 * {@link PlayerList#sendPacketToAllPlayers} — the same shape the mc1.12.2
 * {@code VanillaSkinBroadcaster} uses (1.10.2's PlayerList has the
 * sendPacketToAllPlayers(Packet) surface; the rename to
 * sendToAllTracking was 1.16+).
 *
 * <p>The ADD packet serializes the GameProfile at construction time, so
 * this must run after the GameProfile mutation in {@code SkinRefreshTask}.
 */
public final class VanillaProfileBroadcaster implements SkinProfileBroadcaster {

    @Override
    public void broadcastProfileChange(EntityPlayerMP target) {
        PlayerList playerList = target.mcServer.getPlayerList();
        SPacketPlayerListItem removePacket =
            new SPacketPlayerListItem(SPacketPlayerListItem.Action.REMOVE_PLAYER, target);
        SPacketPlayerListItem addPacket =
            new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, target);
        playerList.sendPacketToAllPlayers(removePacket);
        playerList.sendPacketToAllPlayers(addPacket);
    }
}
