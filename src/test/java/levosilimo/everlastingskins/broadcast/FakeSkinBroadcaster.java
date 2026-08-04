/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Recording fake of {@link SkinBroadcaster} for tests that want to assert
 * the handler's call shape (broadcast then tracker) without rebuilding the
 * REMOVE+ADD+cascade pipeline. Records every call into observable lists so
 * tests can pin order and arguments.
 */
public class FakeSkinBroadcaster implements SkinBroadcaster {

    public record BroadcastCall(GameProfile profile, EntityPlayerMP target, List<EntityPlayerMP> observers) {
        BroadcastCall(GameProfile profile, EntityPlayerMP target, EntityPlayerMP[] observers) {
            this(profile, target,
                observers == null ? Collections.emptyList() : Arrays.asList(observers));
        }
    }

    public final List<BroadcastCall> broadcastCalls = new ArrayList<>();
    public final List<Entity> trackerCalls = new ArrayList<>();

    @Override
    public void broadcastProfileChange(GameProfile newProfile, EntityPlayerMP target) {
        broadcastCalls.add(new BroadcastCall(newProfile, target, (EntityPlayerMP[]) null));
    }

    @Override
    public void broadcastProfileChange(GameProfile newProfile, EntityPlayerMP target, EntityPlayerMP[] observers) {
        broadcastCalls.add(new BroadcastCall(newProfile, target, observers));
    }

    @Override
    public void trackerUntrackRetrack(Entity entity) {
        trackerCalls.add(entity);
    }

    public void reset() {
        broadcastCalls.clear();
        trackerCalls.clear();
    }
}
