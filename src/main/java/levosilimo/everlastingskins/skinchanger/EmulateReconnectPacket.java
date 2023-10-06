package levosilimo.everlastingskins.skinchanger;

import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.List;
import java.util.Set;

public record EmulateReconnectPacket (ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch, float headYaw, byte yawPacket, Set<RelativeMovement> flags, int HeldSlot, Abilities abilities, ResourceKey<DimensionType> dimensionType, ResourceKey<Level> registryKey, long seedEncrypted, GameType gameType, GameType previousGameType, boolean isDebug, boolean isFlat) {

    public void emulateReconnect() {
        SkinRestorer.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
        SkinRestorer.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, player));
        player.connection.send(new ClientboundRespawnPacket(new CommonPlayerSpawnInfo(dimensionType, registryKey, seedEncrypted, gameType, previousGameType, isDebug, isFlat, player.getLastDeathLocation(),player.getPortalCooldown()), (byte) 3));
        world.removePlayerImmediately(player, Entity.RemovalReason.CHANGED_DIMENSION);
        player.revive();
        player.setServerLevel(world);
        player.setPos(x, y, z);
        player.setXRot(yaw);
        player.setYRot(pitch);
        player.setYHeadRot(headYaw);
        world.addDuringCommandTeleport(player);
        player.connection.send(new ClientboundPlayerPositionPacket(x, y, z, pitch, yaw, flags, 0));
        player.connection.send(new ClientboundSetCarriedItemPacket(HeldSlot));
        player.connection.send(new ClientboundPlayerAbilitiesPacket(abilities));
        SkinRestorer.server.getPlayerList().sendAllPlayerInfo(player);
        SkinRestorer.server.getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(player, yawPacket));
        SkinRestorer.server.getPlayerList().sendPlayerPermissionLevel(player);
        for (MobEffectInstance effectinstance : player.getActiveEffects()) {
            player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), effectinstance));
        }
        SkinRestorer.server.getPlayerList().sendLevelInfo(player, player.serverLevel());
    }
}
