package levosilimo.everlastingskins.mixin.server;

import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.skinchanger.SkinCommand;
import levosilimo.everlastingskins.skinchanger.SkinStorage;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import net.minecraft.network.Connection;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PlayerList.class)
public abstract class MixinPlayerManager {

    private static void applySkin(ServerPlayer playerEntity, Property skin) {
        playerEntity.getGameProfile().getProperties().removeAll("textures");
        playerEntity.getGameProfile().getProperties().put("textures", skin);
    }

    @Shadow
    public abstract List<ServerPlayer> getPlayers();

    @Inject(method = "placeNewPlayer", at = @At(value = "HEAD"))
    private void onPlayerConnect(Connection connection, ServerPlayer player, CommonListenerCookie p_297215_, CallbackInfo ci) {
        if (SkinStorage.getInstance().hasDefaultSkin(player)) {
            MojangSkinDataResult skinDataResult = SkinCommand.mojangAPI.getSkin(player.getGameProfile().getName());
            if (skinDataResult != null) SkinStorage.getInstance().setSkin(player,skinDataResult.skinProperty());
        }

        applySkin(player, SkinStorage.getInstance().getSkin(player).getOriginalProperty());
    }

    @Inject(method = "remove", at = @At("TAIL"))
    private void remove(ServerPlayer player, CallbackInfo ci) {
        SkinStorage.getInstance().saveSkin(player);
    }

    @Inject(method = "removeAll", at = @At("HEAD"))
    private void disconnectAllPlayers(CallbackInfo ci) {
        for (ServerPlayer player : getPlayers()) {
            SkinStorage.getInstance().saveSkin(player);
        }
    }
}
