package levosilimo.everlastingskins.skinchanger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.network.play.server.*;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;

import java.util.Collections;
import java.util.Set;

public class EmulateReconnectHandler {
    private final EntityPlayerMP player;
    private final WorldServer world;
    private final int dimensionId;
    private final WorldType worldType;
    private final GameType gameType;

    private final Set<EntityPlayerMP> seenBy;

    public EmulateReconnectHandler(EntityPlayerMP player) {
        this.player = player;
        this.world = player.getServerWorld();
        this.seenBy = (Set<EntityPlayerMP>) world.getEntityTracker().getTrackingPlayers(player);
        this.dimensionId = player.dimension;
        this.worldType = world.getWorldType();
        this.gameType = player.getServer().getGameType();
    }

    public void emulateReconnect() {
        updatePlayerSkin();
        sendPacketsAndBroadcast();
        updatePlayerAbilities();
        updatePlayerPosition();
    }

    private void updatePlayerSkin() {
        SkinRestorer.getSkinStorage().removeSkin(player.getUniqueID());
        player.getGameProfile().getProperties().removeAll("textures");
        player.getGameProfile().getProperties().put("textures", SkinRestorer.getSkinStorage().getSkin(player.getUniqueID()));
    }

    private void updatePlayerPosition() {
        double x = player.posX;
        double y = player.posY;
        double z = player.posZ;
        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;
        float headYaw = player.rotationYawHead;
        player.setLocationAndAngles(x, y, z, yaw, pitch);
        player.setRotationYawHead(headYaw);
        player.connection.sendPacket(new SPacketPlayerPosLook(x,y,z,yaw,pitch, Collections.emptySet(), 0));
        int yHeadPlayerRot = MathHelper.floor(player.getRotationYawHead() * 256.0F / 360.0F);
        this.seenBy.forEach((entity) -> {
            entity.connection.sendPacket(new SPacketEntityHeadLook(player, (byte)yHeadPlayerRot));
            int yHeadEntityRot = MathHelper.floor(entity.getRotationYawHead() * 256.0F / 360.0F);
            player.connection.sendPacket(new SPacketEntityHeadLook(entity, (byte)yHeadEntityRot));
        });
    }

    private void updatePlayerAbilities() {
        PlayerCapabilities abilities = player.capabilities;
        player.connection.sendPacket(new SPacketPlayerAbilities(abilities));
    }

    private void sendPacketsAndBroadcast() {
        player.server.getPlayerList().sendPacketToAllPlayers(new SPacketPlayerListItem(SPacketPlayerListItem.Action.REMOVE_PLAYER, player));
        player.server.getPlayerList().sendPacketToAllPlayers(new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, player));
        player.connection.sendPacket(new SPacketRespawn(dimensionId, world.getDifficulty(), worldType, gameType));
        world.removeEntity(player);
        player.isDead = false;
        player.setWorld(world);
        world.spawnEntity(player);
        player.server.getPlayerList().updatePermissionLevel(player);
        player.interactionManager.setWorld(world);
        player.server.getPlayerList().updateTimeAndWeatherForPlayer(player, world);
        player.server.getPlayerList().syncPlayerInventory(player);
    }
}
