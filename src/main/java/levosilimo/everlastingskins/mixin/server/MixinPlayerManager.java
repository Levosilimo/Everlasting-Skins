package levosilimo.everlastingskins.mixin.server;

import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.skinchanger.MojangSkinProvider;
import levosilimo.everlastingskins.skinchanger.SkinStorage;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.management.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PlayerList.class)
public abstract class MixinPlayerManager {

    private static void applySkin(ServerPlayerEntity playerEntity, Property skin) {
        playerEntity.getGameProfile().getProperties().removeAll("textures");
        playerEntity.getGameProfile().getProperties().put("textures", skin);
    }

    @Shadow
    public abstract List<ServerPlayerEntity> getPlayers();

    @Inject(method = "initializeConnectionToPlayer", at = @At(value = "HEAD"))
    private void onPlayerConnect(NetworkManager connection, ServerPlayerEntity player, CallbackInfo ci) {
        if (SkinStorage.getInstance().hasDefaultSkin(player))
            SkinStorage.getInstance().setSkin(player, MojangSkinProvider.getSkin(player.getGameProfile().getName()));

        applySkin(player, SkinStorage.getInstance().getSkin(player));
    }

    @Inject(method = "playerLoggedOut", at = @At("TAIL"))
    private void remove(ServerPlayerEntity player, CallbackInfo ci) {
        SkinStorage.getInstance().saveSkin(player);
    }

    @Inject(method = "removeAllPlayers", at = @At("HEAD"))
    private void disconnectAllPlayers(CallbackInfo ci) {
        for (ServerPlayerEntity player : getPlayers()) {
            SkinStorage.getInstance().saveSkin(player);
        }
    }
}
