package levosilimo.everlastingskins.skinchanger;

import io.netty.buffer.Unpooled;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.metrics.NetworkMetricsHandler;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.I18nUtils;
import levosilimo.everlastingskins.util.EverlastingHelpers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SkinRefreshHandler {

    /** Test-visible counter of task() invocations (see gametest assertions). */
    public static volatile long refreshTaskCount = 0;

    public static void resetRefreshTaskCount() {
        refreshTaskCount = 0;
    }

    public static long getRefreshTaskCount() {
        return refreshTaskCount;
    }

    public static String getLocalizedString(String key) {
        return I18nUtils.getInstance().getLocalizedString(key, Config.LANGUAGE.get());
    }

    public static void task(ServerPlayer player) {
        refreshTaskCount++;
        CustomSkinProperty skin = SkinRestorer.getSkinStorage().getSkin(player.getUUID());
        if (skin == null || skin.isEmpty()) {
            return;
        }
        long tStart = System.nanoTime();
        mutateProfile(player, skin);
        recordSaveEnqueue(player);
        recordObserverBroadcast(player, skin);
        recordCascade(player);
        long totalNanos = System.nanoTime() - tStart;
        if (totalNanos > TICK_SPIKE_THRESHOLD_NANOS) {
            EverlastingSkins.logger.warn("SKIN_REFRESH tick spike {}ms for {}",
                    totalNanos / 1_000_000, player.getUUID());
            SkinMetrics.INSTANCE.recordTickSpike(totalNanos);
        }
        SkinMetrics.INSTANCE.recordTaskDuration(totalNanos);
    }

    private static void mutateProfile(ServerPlayer player, CustomSkinProperty skin) {
        player.getGameProfile().getProperties().removeAll("textures");
        player.getGameProfile().getProperties().put("textures", skin.getOriginalProperty());
        EverlastingSkins.logger.info("SKIN_REFRESH: profile={}, property={}",
                player.getGameProfile().getName(),
                player.getGameProfile().getProperties().get("textures"));
    }

    private static void recordSaveEnqueue(ServerPlayer player) {
        long start = System.nanoTime();
        SkinRestorer.getSkinStorage().saveSkinAsync(player.getUUID());
        long enqueueNanos = System.nanoTime() - start;
        SkinMetrics.INSTANCE.recordSaveEnqueueLatency(enqueueNanos);
        SkinMetrics.INSTANCE.recordSpikeSaveEnqueue(enqueueNanos);
    }

    /**
     * UPDATE_DISPLAY_NAME does not serialize the GameProfile, so observers never
     * received new textures; REMOVE + ADD_PLAYER forces clients to re-learn the
     * profile. Dimension-scoped only when DIMENSION_SCOPED_BROADCAST is enabled.
     */
    private static void recordObserverBroadcast(ServerPlayer player, CustomSkinProperty skin) {
        PlayerList playerlist = player.server.getPlayerList();
        ServerLevel serverLevel = player.serverLevel();
        var dimension = serverLevel.dimension();
        Packet<ClientGamePacketListener> removePacket = new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID()));
        Packet<ClientGamePacketListener> addPacket = new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, player);

        NetworkMetricsHandler netHandler = NetworkMetricsHandler.getOrAttach(player.connection.getConnection());
        long outBefore = netHandler != null ? netHandler.outboundBytes() : 0;
        long inBefore = netHandler != null ? netHandler.inboundBytes() : 0;
        long start = System.nanoTime();

        if (Config.BROADCAST_USE_BUNDLE.get()) {
            ClientboundBundlePacket bundle = new ClientboundBundlePacket(List.of(removePacket, addPacket));
            for (ServerPlayer online : playerlist.getPlayers()) {
                if (Config.DIMENSION_SCOPED_BROADCAST.get() && !online.serverLevel().dimension().equals(dimension)) {
                    continue;
                }
                online.connection.send(bundle);
            }
        } else if (Config.DIMENSION_SCOPED_BROADCAST.get()) {
            playerlist.broadcastAll(removePacket, dimension);
            playerlist.broadcastAll(addPacket, dimension);
        } else {
            playerlist.broadcastAll(removePacket);
            playerlist.broadcastAll(addPacket);
        }

        long broadcastNanos = System.nanoTime() - start;
        SkinMetrics.INSTANCE.recordBroadcastLatency(broadcastNanos);
        SkinMetrics.INSTANCE.recordSpikeBroadcast(broadcastNanos);
        SkinMetrics.INSTANCE.recordBroadcast(wireSize(removePacket) + wireSize(addPacket));
        if (netHandler != null) {
            SkinMetrics.INSTANCE.recordNetworkDelta(
                    netHandler.outboundBytes() - outBefore,
                    netHandler.inboundBytes() - inBefore);
        }
    }

    /**
     * Respawn cascade for the target's own view: respawn is the only way the
     * client rebuilds its own player model with the new textures. KEEP_ALL_DATA
     * == (byte) 3 in 1.21 (1/2/3 flags) — preserves the client-side inventory.
     */
    private static void recordCascade(ServerPlayer player) {
        ServerLevel serverLevel = player.serverLevel();
        PlayerList playerlist = player.server.getPlayerList();
        double x = player.position().x;
        double y = player.position().y;
        double z = player.position().z;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        long start = System.nanoTime();

        player.connection.send(new ClientboundRespawnPacket(player.createCommonSpawnInfo(serverLevel), ClientboundRespawnPacket.KEEP_ALL_DATA));
        player.absMoveTo(x, y, z, yaw, pitch);
        player.connection.send(new ClientboundPlayerPositionPacket(x, y, z, yaw, pitch, Collections.emptySet(), 0));
        playerlist.sendLevelInfo(player, serverLevel);
        playerlist.sendPlayerPermissionLevel(player);
        playerlist.sendAllPlayerInfo(player);
        // 1.21 sendAllPlayerInfo does NOT include abilities — send explicitly.
        player.connection.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
        playerlist.sendActivePlayerEffects(player);

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

    /** Serialized byte size of the packets, measured with their stream codecs. */
    private static int wireSize(net.minecraft.network.protocol.Packet<?> packet) {
        try {
            if (packet instanceof ClientboundPlayerInfoRemovePacket remove) {
                return encodeSize(remove, ClientboundPlayerInfoRemovePacket.STREAM_CODEC);
            }
            if (packet instanceof ClientboundPlayerInfoUpdatePacket update) {
                return encodeSize(update, ClientboundPlayerInfoUpdatePacket.STREAM_CODEC);
            }
            if (packet instanceof ClientboundPlayerPositionPacket position) {
                return encodeSize(position, ClientboundPlayerPositionPacket.STREAM_CODEC);
            }
            if (packet instanceof ClientboundPlayerAbilitiesPacket abilities) {
                return encodeSize(abilities, ClientboundPlayerAbilitiesPacket.STREAM_CODEC);
            }
            if (packet instanceof ClientboundRespawnPacket respawn) {
                return encodeSize(respawn, ClientboundRespawnPacket.STREAM_CODEC);
            }
        } catch (Exception e) {
            EverlastingSkins.logger.debug("Failed to measure packet wire size", e);
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static <T> int encodeSize(T packet, net.minecraft.network.codec.StreamCodec<?, T> codec) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                SkinRestorer.server.registryAccess());
        ((net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, T>) codec).encode(buf, packet);
        int size = buf.readableBytes();
        buf.release();
        return size;
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
                        ? "No skin found for \"" + customSource + "\""
                        : "No skin found";
            case url: {
                if (customSource != null) {
                    String sanitized = EverlastingHelpers.sanitizeSkinInput(customSource);
                    if (!sanitized.equals(customSource)) {
                        return "No skin found for \"" + sanitized + "\"";
                    }
                }
                return "MineSkin rejected the URL";
            }
            case random:
            case NEW:
                return "No random username available";
            default:
                return "Provider returned no result";
        }
    }
}
