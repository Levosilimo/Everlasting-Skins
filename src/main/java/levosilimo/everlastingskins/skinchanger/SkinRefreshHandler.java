package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.I18nUtils;
import levosilimo.everlastingskins.util.EverlastingHelpers;
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

    static String getLocalizedString(String key) {
        return I18nUtils.getInstance().getLocalizedString(key, Config.LANGUAGE.get());
    }

    public static void task(ServerPlayer player) {
        refreshTaskCount++;
        CustomSkinProperty skin = SkinRestorer.getSkinStorage().getSkin(player.getUUID());
        if (skin == null || skin.isEmpty()) {
            return;
        }
        double x = player.position().x;
        double y = player.position().y;
        double z = player.position().z;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        ServerLevel serverLevel = player.serverLevel();
        PlayerList playerlist = player.server.getPlayerList();
        SkinRestorer.getSkinStorage().saveSkinAsync(player.getUUID());
        player.getGameProfile().getProperties().removeAll("textures");
        player.getGameProfile().getProperties().put("textures", skin.getOriginalProperty());
        EverlastingSkins.logger.info("SKIN_REFRESH: profile={}, property={}",
                player.getGameProfile().getName(),
                player.getGameProfile().getProperties().get("textures"));

        // Bug fix: UPDATE_DISPLAY_NAME does not serialize the GameProfile, so
        // observers never received the new textures. REMOVE + ADD_PLAYER forces
        // clients to drop and re-learn the full profile (with textures).
        // Dimension-scoped only when DIMENSION_SCOPED_BROADCAST is enabled;
        // the default (off) matches vanilla and 1.12.2 behavior.
        var dimension = serverLevel.dimension();
        if (Config.DIMENSION_SCOPED_BROADCAST.get()) {
            playerlist.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())), dimension);
            playerlist.broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, player), dimension);
        } else {
            playerlist.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
            playerlist.broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, player));
        }

        // Respawn cascade for the target's OWN view: respawn is the only way
        // the client rebuilds its own player model with the new textures.
        // KEEP_ALL_DATA == (byte) 3 in 1.21 (KEEP_ATTRIBUTE_MODIFIERS=1,
        // KEEP_ENTITY_DATA=2, KEEP_ALL_DATA=3) — preserves the client-side
        // inventory, the SkinsRestorer convention.
        player.connection.send(new ClientboundRespawnPacket(player.createCommonSpawnInfo(serverLevel), ClientboundRespawnPacket.KEEP_ALL_DATA));
        player.absMoveTo(x, y, z, yaw, pitch);
        player.connection.send(new ClientboundPlayerPositionPacket(x, y, z, yaw, pitch, Collections.emptySet(), 0));
        playerlist.sendLevelInfo(player, serverLevel);
        playerlist.sendPlayerPermissionLevel(player);
        playerlist.sendAllPlayerInfo(player);
        // 1.21 sendAllPlayerInfo does NOT include abilities — send explicitly.
        player.connection.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
        playerlist.sendActivePlayerEffects(player);
    }

    @Nullable
    static MojangRestoreResult tryRestoreFromMojang(MojangAPI mojangAPI, @Nullable String storedSource, String playerName) {
        String licensedUsername = (storedSource != null && !storedSource.trim().isEmpty())
                ? storedSource : playerName;
        CustomSkinProperty skin = mojangAPI.getSkin(licensedUsername)
                .map(MojangSkinDataResult::skinProperty)
                .filter(s -> !s.isEmpty())
                .orElse(null);
        if (skin == null) return null;
        return new MojangRestoreResult(skin, licensedUsername);
    }

    static class MojangRestoreResult {
        final CustomSkinProperty skin;
        final String licensedUsername;

        MojangRestoreResult(CustomSkinProperty skin, String licensedUsername) {
            this.skin = skin;
            this.licensedUsername = licensedUsername;
        }
    }

    static String deriveReason(SkinActionType type, @Nullable String customSource) {
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
