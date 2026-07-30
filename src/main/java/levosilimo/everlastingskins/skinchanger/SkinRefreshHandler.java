package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.I18nUtils;
import levosilimo.everlastingskins.util.SRHelpers;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.LevelData;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SkinRefreshHandler {

    static String getLocalizedString(String key) {
        return I18nUtils.getInstance().getLocalizedString(key, Config.LANGUAGE.get());
    }

    static void task(ServerPlayer player) {
        double x = player.position().x;
        double y = player.position().y;
        double z = player.position().z;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        ServerLevel serverLevel = player.serverLevel();
        LevelData levelData = serverLevel.getLevelData();
        PlayerList playerlist = player.server.getPlayerList();
        SkinRestorer.getSkinStorage().saveSkin(player.getUUID());
        player.getGameProfile().getProperties().removeAll("textures");
        player.getGameProfile().getProperties().put("textures", SkinRestorer.getSkinStorage().getSkin(player.getUUID()).getOriginalProperty());

        SkinRestorer.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
        SkinRestorer.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, player));
        player.connection.send(new ClientboundRespawnPacket(player.createCommonSpawnInfo(serverLevel), (byte) 3));
        player.absMoveTo(x, y, z, yaw, pitch);
        player.connection.send(new ClientboundPlayerPositionPacket(x, y, z, yaw, pitch, Collections.emptySet(), 0));
        playerlist.sendLevelInfo(player, serverLevel);
        playerlist.sendPlayerPermissionLevel(player);
        playerlist.sendAllPlayerInfo(player);
        playerlist.sendActivePlayerEffects(player);
        player.connection.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
        player.connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
        SkinRestorer.server.getPlayerList().sendPlayerPermissionLevel(player);

        serverLevel.getChunkSource().chunkMap.removeEntity(player);
        serverLevel.getChunkSource().chunkMap.addEntity(player);
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
                    String sanitized = SRHelpers.sanitizeSkinInput(customSource);
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
