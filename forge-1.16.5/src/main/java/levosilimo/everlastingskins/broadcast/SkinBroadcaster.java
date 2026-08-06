/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;

/**
 * Abstraction over the per-viewer packet fan-out for a skin change. The
 * refresh pipeline builds a fresh GameProfile, then asks the broadcaster
 * to deliver the tab-list REMOVE+ADD pair (or one bundled REMOVE+ADD in
 * 1.21) and to drive the entity-tracker untrack/retrack so observers
 * re-fetch the profile.
 *
 * <p>{@link VanillaSkinBroadcaster} is the production implementation; it
 * reads {@code BROADCAST_USE_BUNDLE}, {@code DIMENSION_SCOPED_BROADCAST}
 * and {@code REFRESH_VIA_ENTITY_TRACKER} from {@link
 * levosilimo.everlastingskins.Config} and emits the vanilla packets.
 * Tests inject a recording fake so the handler's call shape can be
 * asserted without re-implementing the REMOVE+ADD+cascade pipeline.
 */
public interface SkinBroadcaster {

    /**
     * Broadcast the REMOVE+ADD (or one bundled REMOVE+ADD) tab-list pair
     * for {@code target}. The implementation is responsible for honoring
     * {@code BROADCAST_USE_BUNDLE} and {@code DIMENSION_SCOPED_BROADCAST}.
     */
    void broadcastProfileChange(GameProfile newProfile, ServerPlayerEntity target);

    /**
     * Broadcast the REMOVE+ADD (or one bundled REMOVE+ADD) tab-list pair
     * for {@code target} to an explicit observer set. Used when the
     * caller has already filtered by dimension or other criteria and
     * wants to bypass the broadcast-all helper.
     */
    void broadcastProfileChange(GameProfile newProfile, ServerPlayerEntity target, ServerPlayerEntity[] observers);

    /**
     * Untrack/re-track {@code entity} on its server level's entity
     * tracker. The implementation reads {@code REFRESH_VIA_ENTITY_TRACKER}
     * and is a no-op when disabled. Forced re-track is what makes remote
     * clients destroy+re-spawn the entity so their cached playerInfo
     * re-fetches the new profile entry.
     */
    void trackerUntrackRetrack(Entity entity);
}
