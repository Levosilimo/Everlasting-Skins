/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import levosilimo.everlastingskins.skinchanger.MojangAPI;
import levosilimo.everlastingskins.skinchanger.SkinStorage;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.broadcast.SkinBroadcaster;
import levosilimo.everlastingskins.broadcast.VanillaSkinBroadcaster;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.I18nUtils;
import levosilimo.everlastingskins.util.EverlastingHelpers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.biome.BiomeManager;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.UUID;

public class SkinRefreshHandler {

    /** Test-visible counter of task() invocations (see gametest assertions). */
    public static volatile long refreshTaskCount = 0;

    /**
     * Seam for the per-viewer packet fan-out. Production uses
     * {@link VanillaSkinBroadcaster}; tests inject a recording fake to
     * assert call shape without re-implementing the REMOVE+ADD+cascade
     * pipeline. Static so the {@code task} entry point stays
     * signature-compatible with all existing callers (R3 invariant:
     * public API unchanged).
     */
    private static volatile SkinBroadcaster broadcaster = new VanillaSkinBroadcaster();

    /** Test seam: replace the broadcaster. */
    public static void setBroadcaster(SkinBroadcaster newBroadcaster) {
        broadcaster = newBroadcaster;
    }

    /** Current broadcaster (mainly for tests asserting the wiring). */
    public static SkinBroadcaster getBroadcaster() {
        return broadcaster;
    }

    public static void resetRefreshTaskCount() {
        refreshTaskCount = 0;
    }

    public static long getRefreshTaskCount() {
        return refreshTaskCount;
    }

    public static String getLocalizedString(String key) {
        return I18nUtils.getLocalizedString(key, Config.LANGUAGE.get());
    }

    public static void task(ServerPlayer player) {
        refreshTaskCount++;
        CustomSkinProperty skin = SkinRestorer.getSkinStorage().getSkin(player.getUUID());
        if (skin == null || skin.isEmpty()) {
            // Stored skin is cleared (or never set): drop the applied textures
            // property so the applied GameProfile matches storage, then let
            // observers re-learn the profile and rebuild the target's own view.
            // Without this, /skin clear with no Mojang profile left the applied
            // profile showing the old texture while storage said "cleared".
            mutateProfileCleared(player.getGameProfile());
            recordObserverBroadcast(player, null);
            recordCascade(player);
            return;
        }
        long tStart = System.nanoTime();
        // Atomicity invariant (R3): persistence is enqueued before the
        // GameProfile is mutated. recordSaveEnqueue captures the stored skin
        // synchronously (saveSkinAsync serializes the in-memory map entry at
        // enqueue time), so when the enqueue throws, the applied profile is
        // left untouched and in-memory and on-disk state still agree — a
        // mutated profile with no persisted skin would silently revert on the
        // next server restart. applyAtomicPersistence records the failure
        // metric itself; broadcast/cascade only run after it succeeded.
        if (applyAtomicPersistence(player.getUUID(), player.getGameProfile(), skin)) {
            try {
                recordObserverBroadcast(player, skin);
                recordCascade(player);
            } catch (Throwable t) {
                // Fail soft: a partial cascade (profile and disk hold the new
                // skin, observers stale) must not abort scheduled-task
                // execution or the server tick.
                EverlastingSkins.logger.error("Skin refresh failed for {}", player.getUUID(), t);
                SkinMetrics.INSTANCE.recordRefreshFailed(player.getUUID());
            }
        }
        long totalNanos = System.nanoTime() - tStart;
        if (totalNanos > TICK_SPIKE_THRESHOLD_NANOS) {
            EverlastingSkins.logger.warn("SKIN_REFRESH tick spike {}ms for {}",
                    totalNanos / 1_000_000, player.getUUID());
            SkinMetrics.INSTANCE.recordTickSpike(totalNanos);
        }
        SkinMetrics.INSTANCE.recordTaskDuration(totalNanos);
    }

    /**
     * saveSkinAsync + mutateProfile are atomic: either both succeed or both
     * fail (returning false). The caller ({@link #task(ServerPlayer)}) skips
     * broadcast/cascade on failure. The save captures the stored skin at
     * enqueue time, before the profile is mutated. Test-visible so
     * SkinRefreshHandlerTest can exercise the invariant without a ServerPlayer.
     */
    static boolean applyAtomicPersistence(UUID playerId, GameProfile profile, CustomSkinProperty skin) {
        try {
            recordSaveEnqueue(playerId);
            mutateProfile(profile, skin);
            return true;
        } catch (Throwable t) {
            EverlastingSkins.logger.warn("Skin refresh failed for {}", playerId, t);
            SkinMetrics.INSTANCE.recordRefreshFailed(playerId);
            return false;
        }
    }

    private static void mutateProfile(GameProfile profile, CustomSkinProperty skin) {
        profile.getProperties().removeAll("textures");
        profile.getProperties().put("textures", skin.getOriginalProperty());
        EverlastingSkins.logger.info("SKIN_REFRESH: profile={}, property={}",
                profile.getName(),
                profile.getProperties().get("textures"));
    }

    private static void mutateProfileCleared(GameProfile profile) {
        profile.getProperties().removeAll("textures");
        EverlastingSkins.logger.info("SKIN_REFRESH: profile={}, property={} (cleared)",
                profile.getName(),
                profile.getProperties().get("textures"));
    }

    private static void recordSaveEnqueue(UUID playerId) {
        long start = System.nanoTime();
        SkinStorage storage = SkinRestorer.getSkinStorage();
        CustomSkinProperty skin = storage.getSkin(playerId);
        if (skin != null) {
            storage.saveSkinAsync(playerId, skin);
        }
        long enqueueNanos = System.nanoTime() - start;
        SkinMetrics.INSTANCE.recordSaveEnqueueLatency(enqueueNanos);
        SkinMetrics.INSTANCE.recordSpikeSaveEnqueue(enqueueNanos);
    }

    /**
     * UPDATE_DISPLAY_NAME does not serialize the GameProfile, so observers never
     * received new textures; REMOVE + ADD_PLAYER forces clients to re-learn the
     * profile. Dimension-scoped only when DIMENSION_SCOPED_BROADCAST is enabled.
     *
     * <p>Per-viewer packet fan-out is delegated to the injected
     * {@link SkinBroadcaster}; the call shape is broadcaster-agnostic so
     * config-flag tests can swap in a recording fake without rebuilding
     * the REMOVE+ADD+cascade pipeline.
     */
    private static void recordObserverBroadcast(ServerPlayer player, CustomSkinProperty skin) {
        broadcaster.broadcastProfileChange(player.getGameProfile(), player);
        broadcaster.trackerUntrackRetrack(player);
    }

    /**
     * Respawn cascade for the target's own view: respawn is the only way the
     * client rebuilds its own player model with the new textures. KEEP_ALL_DATA
     * == (byte) 3 in 1.21 (1/2/3 flags) — preserves the client-side inventory.
     */
    private static void recordCascade(ServerPlayer player) {
        ServerLevel serverLevel = player.serverLevel();
        PlayerList playerlist = player.getServer().getPlayerList();
        double x = player.position().x;
        double y = player.position().y;
        double z = player.position().z;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        long start = System.nanoTime();

        player.connection.send(new ClientboundRespawnPacket(
                player.level().dimensionTypeId(),
                player.level().dimension(),
                BiomeManager.obfuscateSeed(serverLevel.getSeed()),
                player.gameMode.getGameModeForPlayer(),
                player.gameMode.getPreviousGameModeForPlayer(),
                player.level().isDebug(),
                serverLevel.isFlat(),
                ClientboundRespawnPacket.KEEP_ALL_DATA,
                player.getLastDeathLocation(),
                player.getPortalCooldown()));
        player.setPos(x, y, z);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.connection.send(new ClientboundPlayerPositionPacket(x, y, z, yaw, pitch, Collections.emptySet(), 0));
        playerlist.sendLevelInfo(player, serverLevel);
        playerlist.sendPlayerPermissionLevel(player);
        playerlist.sendAllPlayerInfo(player);
        // sendAllPlayerInfo does NOT include abilities (1.21 and 1.20.1
        // alike, verified against the 47.4.10 bytecode) — send explicitly.
        player.connection.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
        // 1.20.1 PlayerList has no sendActivePlayerEffects; vanilla
        // placeNewPlayer resends effects inline via UpdateMobEffectPacket.
        for (MobEffectInstance effect : player.getActiveEffects()) {
            player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), effect));
        }

        SkinMetrics.INSTANCE.recordSpikeCascade(System.nanoTime() - start);
        SkinMetrics.INSTANCE.recordBroadcast(wireSize(new ClientboundPlayerPositionPacket(x, y, z, yaw, pitch, Collections.emptySet(), 0))
                + wireSize(new ClientboundPlayerAbilitiesPacket(player.getAbilities()))
                + CASCADE_SEND_LEVEL_INFO_BYTES + CASCADE_SEND_PERMISSION_BYTES
                + CASCADE_SEND_ALL_PLAYER_INFO_BYTES + CASCADE_SEND_EFFECTS_BYTES);
    }

    /** Approximations for the player-list helper sends in the cascade (per call). */
    private static final int CASCADE_SEND_LEVEL_INFO_BYTES = 256;
    private static final int CASCADE_SEND_PERMISSION_BYTES = 128;
    private static final int CASCADE_SEND_ALL_PLAYER_INFO_BYTES = 192;
    private static final int CASCADE_SEND_EFFECTS_BYTES = 96;

    private static final long TICK_SPIKE_THRESHOLD_NANOS = 50_000_000;

    /** Serialized byte size of the cascade packets (1.20.1: no StreamCodec). */
    private static int wireSize(net.minecraft.network.protocol.Packet<?> packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.write(buf);
            return buf.readableBytes();
        } catch (Exception e) {
            EverlastingSkins.logger.debug("Failed to measure packet wire size", e);
            return 0;
        } finally {
            buf.release();
        }
    }

    @Nullable
    public static MojangRestoreResult tryRestoreFromMojang(MojangAPI mojangAPI, @Nullable String storedSource, String playerName) {
        String licensedUsername = (storedSource != null && !storedSource.trim().isEmpty())
                ? storedSource : playerName;
        CustomSkinProperty skin = mojangAPI.getSkin(licensedUsername)
                .map(MojangSkinDataResult::skinProperty)
                .filter(s -> !s.isEmpty())
                .orElse(null);
        if (skin == null) return null;
        return new MojangRestoreResult(skin, licensedUsername);
    }

    public static class MojangRestoreResult {
        public final CustomSkinProperty skin;
        public final String licensedUsername;

        MojangRestoreResult(CustomSkinProperty skin, String licensedUsername) {
            this.skin = skin;
            this.licensedUsername = licensedUsername;
        }
    }

    public static String deriveReason(SkinActionType type, @Nullable String customSource) {
        switch (type) {
            case username:
                return customSource != null
                        ? I18nUtils.get("no_skin_found", customSource)
                        : I18nUtils.get("no_skin_found_plain");
            case url: {
                if (customSource != null) {
                    String sanitized = EverlastingHelpers.sanitizeSkinInput(customSource);
                    if (!sanitized.equals(customSource)) {
                        return I18nUtils.get("no_skin_found", sanitized);
                    }
                }
                return I18nUtils.get("mineskin_rejected");
            }
            case random:
            case NEW:
                return I18nUtils.get("no_random_username");
            default:
                return I18nUtils.get("provider_no_result");
        }
    }
}
