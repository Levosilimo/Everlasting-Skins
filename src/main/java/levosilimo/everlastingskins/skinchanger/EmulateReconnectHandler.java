package levosilimo.everlastingskins.skinchanger;

import com.google.common.collect.Sets;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.*;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldType;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ChunkManager;
import net.minecraft.world.server.ServerWorld;

import java.util.Collections;
import java.util.Set;

public class EmulateReconnectHandler {
    private final ServerPlayerEntity player;
    private final ServerWorld world;
    private final DimensionType dimensionType;
    private final WorldType worldType;
    private final GameType gameType;

    private Set<ServerPlayerEntity> seenBy = Sets.newHashSet();

    public EmulateReconnectHandler(ServerPlayerEntity player) {
        this.player = player;
        this.world = player.getServerWorld();
        for(ChunkManager.EntityTracker chunkmanager$entitytracker : world.getChunkProvider().chunkManager.entities.values()) {
            if(chunkmanager$entitytracker.entity.equals(player)) this.seenBy = chunkmanager$entitytracker.trackingPlayers;
        }
        this.dimensionType = player.dimension;
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
        SkinRestorer.getSkinStorage().removeSkin(PlayerEntity.getUUID(player.getGameProfile()));
        player.getGameProfile().getProperties().removeAll("textures");
        player.getGameProfile().getProperties().put("textures", SkinRestorer.getSkinStorage().getSkin(PlayerEntity.getUUID(player.getGameProfile())));
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
        player.connection.sendPacket(new SPlayerPositionLookPacket(x,y,z,yaw,pitch, Collections.emptySet(), 0));
        int yHeadPlayerRot = MathHelper.floor(player.getRotationYawHead() * 256.0F / 360.0F);
        this.seenBy.forEach((entity) -> {
            entity.connection.sendPacket(new SEntityHeadLookPacket(player, (byte)yHeadPlayerRot));
            int yHeadEntityRot = MathHelper.floor(entity.getRotationYawHead() * 256.0F / 360.0F);
            player.connection.sendPacket(new SEntityHeadLookPacket(entity, (byte)yHeadEntityRot));
        });
    }

    private void updatePlayerAbilities() {
        PlayerAbilities abilities = player.abilities;
        player.connection.sendPacket(new SPlayerAbilitiesPacket(abilities));
    }

    private void sendPacketsAndBroadcast() {
        player.server.getPlayerList().sendPacketToAllPlayers(new SPlayerListItemPacket(SPlayerListItemPacket.Action.REMOVE_PLAYER, player));
        player.server.getPlayerList().sendPacketToAllPlayers(new SPlayerListItemPacket(SPlayerListItemPacket.Action.ADD_PLAYER, player));
        player.connection.sendPacket(new SRespawnPacket(dimensionType, worldType, gameType));
        world.removePlayer(player, true);
        player.revive();
        player.setWorld(world);
        world.addRespawnedPlayer(player);
        player.server.getPlayerList().updatePermissionLevel(player);
        player.interactionManager.setWorld(world);
        player.server.getPlayerList().sendWorldInfo(player, world);
        player.server.getPlayerList().sendInventory(player);
    }
}
