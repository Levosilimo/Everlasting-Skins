/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.GameProfile;
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
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPlayerAbilitiesPacket;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.network.play.server.SRespawnPacket;
import net.minecraft.server.management.PlayerList;
import net.minecraft.world.server.ServerWorld;

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

    public static void task(ServerPlayerEntity player) {
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
            recordSaveEnqueue(playerId, skin);
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

    private static void recordSaveEnqueue(UUID playerId, CustomSkinProperty skin) {
        long start = System.nanoTime();
        SkinRestorer.getSkinStorage().saveSkinAsync(playerId, skin);
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
    private static void recordObserverBroadcast(ServerPlayerEntity player, CustomSkinProperty skin) {
        broadcaster.broadcastProfileChange(player.getGameProfile(), player);
        broadcaster.trackerUntrackRetrack(player);
    }

    /**
     * Respawn cascade for the target's own view: respawn is the only way the
     * client rebuilds its own player model with the new textures. KEEP_ALL_DATA
     * == (byte) 3 in 1.21 (1/2/3 flags) — preserves the client-side inventory.
     */
    private static void recordCascade(ServerPlayerEntity player) {
        ServerWorld serverLevel = player.getLevel();
        PlayerList playerlist = player.getServer().getPlayerList();
        double x = player.position().x;
        double y = player.position().y;
        double z = player.position().z;
        float yaw = player.yRot;
        float pitch = player.xRot;
        long start = System.nanoTime();

        // 1.16.5 respawn packet (SRespawnPacket): no KEEP_ALL_DATA constant —
        // the last ctor boolean is keepAllPlayerData (added in 1.16.2), the
        // 1.16.5 equivalent of 1.21's KEEP_ALL_DATA. Argument order mirrors
        // vanilla PlayerList#respawn (verified against 36.2.34 bytecode).
        player.connection.send(new SRespawnPacket(
                serverLevel.dimensionType(),
                serverLevel.dimension(),
                serverLevel.getSeed(),
                player.gameMode.getGameModeForPlayer(),
                player.gameMode.getPreviousGameModeForPlayer(),
                serverLevel.isDebug(),
                serverLevel.isFlat(),
                true));
        player.setPos(x, y, z);
        player.yRot = yaw;
        player.xRot = pitch;
        // Vanilla respawn path: ServerPlayNetHandler#teleport sends the
        // SPlayerPositionLookPacket (absolute, empty relative set, id 0).
        player.connection.teleport(x, y, z, yaw, pitch);
        playerlist.sendLevelInfo(player, serverLevel);
        playerlist.sendPlayerPermissionLevel(player);
        playerlist.sendAllPlayerInfo(player);
        // 1.16.5 sendAllPlayerInfo does NOT include abilities — send explicitly.
        player.connection.send(new SPlayerAbilitiesPacket(player.abilities));
        // 1.21's sendActivePlayerEffects does not exist on 1.16.5 (1.17+
        // addition); active effects re-sync on the next effect change.

        SkinMetrics.INSTANCE.recordSpikeCascade(System.nanoTime() - start);
        SkinMetrics.INSTANCE.recordBroadcast(wireSize(new SPlayerPositionLookPacket(x, y, z, yaw, pitch, Collections.emptySet(), 0))
                + wireSize(new SPlayerAbilitiesPacket(player.abilities))
                + CASCADE_SEND_LEVEL_INFO_BYTES + CASCADE_SEND_PERMISSION_BYTES
                + CASCADE_SEND_ALL_PLAYER_INFO_BYTES);
    }

    /** Approximations for the player-list helper sends in the cascade (per call). */
    private static final int CASCADE_SEND_LEVEL_INFO_BYTES = 256;
    private static final int CASCADE_SEND_PERMISSION_BYTES = 128;
    private static final int CASCADE_SEND_ALL_PLAYER_INFO_BYTES = 192;

    private static final long TICK_SPIKE_THRESHOLD_NANOS = 50_000_000;

    /** Serialized byte size of a packet, measured by writing it to a buffer
     *  (1.16.5 has no stream codecs; every IPacket serializes via write). */
    private static int wireSize(IPacket<?> packet) {
        try {
            PacketBuffer buf = new PacketBuffer(io.netty.buffer.Unpooled.buffer());
            packet.write(buf);
            int size = buf.readableBytes();
            buf.release();
            return size;
        } catch (Exception e) {
            EverlastingSkins.logger.debug("Failed to measure packet wire size", e);
        }
        return 0;
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
