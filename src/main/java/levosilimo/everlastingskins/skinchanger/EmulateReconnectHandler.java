package levosilimo.everlastingskins.skinchanger;

import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.*;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldType;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ChunkManager;
import net.minecraft.world.server.ServerWorld;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

public class EmulateReconnectHandler {
    private final ServerPlayerEntity player;
    private final ServerWorld world;
    private final DimensionType dimensionType;
    private final WorldType worldType;
    private final long seedEncrypted;
    private final GameType gameType;

    private Set<ServerPlayerEntity> seenBy = Sets.newHashSet();

    public EmulateReconnectHandler(ServerPlayerEntity player) {
        this.player = player;
        this.world = player.getLevel();
        for(ChunkManager.EntityTracker chunkmanager$entitytracker : world.getChunkSource().chunkMap.entityMap.values()) {
            if(chunkmanager$entitytracker.entity.equals(player)) this.seenBy = chunkmanager$entitytracker.seenBy;
        }
        this.dimensionType = player.dimension;
        this.worldType = player.getLevel().getGeneratorType();
        this.seedEncrypted = Hashing.sha256().hashString(String.valueOf(player.getLevel().getSeed()), StandardCharsets.UTF_8).asLong();
        this.gameType = player.gameMode.getGameModeForPlayer();
    }

    public void emulateReconnect() {
        updatePlayerSkin();
        sendPacketsAndBroadcast();
        updatePlayerAbilities();
        updatePlayerPosition();
    }

    private void updatePlayerSkin() {
        SkinRestorer.getSkinStorage().removeSkin(player.getUUID());
        player.getGameProfile().getProperties().removeAll("textures");
        player.getGameProfile().getProperties().put("textures", SkinRestorer.getSkinStorage().getSkin(player.getUUID()));
    }

    private void updatePlayerPosition() {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.yRot;
        float pitch = player.xRot;
        float headYaw = player.getYHeadRot();
        player.absMoveTo(x, y, z, yaw, pitch);
        player.setYHeadRot(headYaw);
        player.connection.send(new SPlayerPositionLookPacket(x,y,z,yaw,pitch, Collections.emptySet(), 0));
        int yHeadPlayerRot = MathHelper.floor(player.getYHeadRot() * 256.0F / 360.0F);
        this.seenBy.forEach((entity) -> {
            entity.connection.send(new SEntityHeadLookPacket(player, (byte)yHeadPlayerRot));
            int yHeadEntityRot = MathHelper.floor(entity.getYHeadRot() * 256.0F / 360.0F);
            player.connection.send(new SEntityHeadLookPacket(entity, (byte)yHeadEntityRot));
        });
    }

    private void updatePlayerAbilities() {
        PlayerAbilities abilities = player.abilities;
        player.connection.send(new SPlayerAbilitiesPacket(abilities));
    }

    private void sendPacketsAndBroadcast() {
        player.server.getPlayerList().broadcastAll(new SPlayerListItemPacket(SPlayerListItemPacket.Action.REMOVE_PLAYER, player));
        player.server.getPlayerList().broadcastAll(new SPlayerListItemPacket(SPlayerListItemPacket.Action.ADD_PLAYER, player));
        player.connection.send(new SRespawnPacket(dimensionType, seedEncrypted, worldType, gameType));
        world.removePlayer(player, true);
        player.revive();
        player.setLevel(world);
        world.addDuringCommandTeleport(player);
        player.server.getPlayerList().sendPlayerPermissionLevel(player);
        player.gameMode.setLevel(world);
        player.server.getPlayerList().sendLevelInfo(player, world);
        player.server.getPlayerList().sendAllPlayerInfo(player);
    }
}
