/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Recording fake of {@link SkinBroadcaster} for tests that want to assert
 * the handler's call shape (broadcast then tracker) without rebuilding the
 * REMOVE+ADD+cascade pipeline. Records every call into observable lists so
 * tests can pin order and arguments.
 *
 * <p>Java 8 port note: the 1.21 lane's inner {@code record} is flattened to
 * a plain final class — this lane compiles at source level 8.
 */
public class FakeSkinBroadcaster implements SkinBroadcaster {

    public static final class BroadcastCall {
        public final GameProfile profile;
        public final ServerPlayerEntity target;
        public final List<ServerPlayerEntity> observers;

        BroadcastCall(GameProfile profile, ServerPlayerEntity target, ServerPlayerEntity[] observers) {
            this.profile = profile;
            this.target = target;
            this.observers = observers == null ? Collections.emptyList() : Arrays.asList(observers);
        }

        public GameProfile profile() {
            return profile;
        }

        public ServerPlayerEntity target() {
            return target;
        }

        public List<ServerPlayerEntity> observers() {
            return observers;
        }
    }

    public final List<BroadcastCall> broadcastCalls = new ArrayList<>();
    public final List<Entity> trackerCalls = new ArrayList<>();

    @Override
    public void broadcastProfileChange(GameProfile newProfile, ServerPlayerEntity target) {
        broadcastCalls.add(new BroadcastCall(newProfile, target, (ServerPlayerEntity[]) null));
    }

    @Override
    public void broadcastProfileChange(GameProfile newProfile, ServerPlayerEntity target, ServerPlayerEntity[] observers) {
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
