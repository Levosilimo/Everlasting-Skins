/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Profile-change fan-out seam for the 1.10.2 refresh cascade. The lane's
 * {@link SkinBroadcaster} is the SimpleNetworkWrapper channel (client
 * payloads); this is the server-side tab-list re-broadcast, kept separate
 * so {@link SkinRefreshTask} can be tested with a recording fake without
 * constructing real packets.
 */
public interface SkinProfileBroadcaster {

    /**
     * Forces every client to re-learn the target's GameProfile via the
     * tab-list REMOVE+ADD pair. Must run after the GameProfile mutation.
     */
    void broadcastProfileChange(EntityPlayerMP target);
}
