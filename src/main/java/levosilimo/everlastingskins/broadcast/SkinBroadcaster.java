/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Abstraction over the per-viewer packet fan-out for a skin change. The
 * refresh pipeline builds a fresh GameProfile, then asks the broadcaster
 * to deliver the tab-list REMOVE+ADD pair (1.12.2 has no
 * ClientboundBundlePacket; bundle-mode is documented as 1.21-only) and
 * to drive the entity-tracker untrack/retrack so observers re-fetch the
 * profile.
 *
 * <p>{@link VanillaSkinBroadcaster} is the production implementation; it
 * reads {@code Config.refreshViaEntityTracker} and emits the vanilla
 * packets via the live PlayerList. Tests inject a recording fake so the
 * handler's call shape can be asserted without re-implementing the
 * REMOVE+ADD+cascade pipeline.
 */
public interface SkinBroadcaster {

    /**
     * Broadcast the REMOVE+ADD tab-list pair for {@code target}. The
     * implementation is responsible for honoring
     * {@code Config.refreshViaEntityTracker} (the only broadcast-related
     * config flag on 1.12.2).
     */
    void broadcastProfileChange(GameProfile newProfile, EntityPlayerMP target);

    /**
     * Broadcast the REMOVE+ADD tab-list pair for {@code target} to an
     * explicit observer set. Used when the caller has already filtered
     * by dimension or other criteria and wants to bypass the
     * sendPacketToAllPlayers helper.
     */
    void broadcastProfileChange(GameProfile newProfile, EntityPlayerMP target, EntityPlayerMP[] observers);

    /**
     * Untrack/re-track {@code entity} on its server world's entity
     * tracker. The implementation reads {@code refreshViaEntityTracker}
     * and is a no-op when disabled.
     */
    void trackerUntrackRetrack(Entity entity);
}
