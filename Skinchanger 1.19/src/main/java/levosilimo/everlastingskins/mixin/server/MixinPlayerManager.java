package levosilimo.everlastingskins.mixin.server;

import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.skinchanger.MojangSkinProvider;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.skinchanger.SkinStorage;
import net.minecraft.network.Connection;
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
    private void onPlayerConnect(Connection mutablecomponent, ServerPlayer player, CallbackInfo ci) {
        if (SkinRestorer.getSkinStorage().getSkin(player.getUUID()) == SkinStorage.DEFAULT_SKIN)
            SkinRestorer.getSkinStorage().setSkin(player.getUUID(), MojangSkinProvider.getSkin(player.getGameProfile().getName()));

        applySkin(player, SkinRestorer.getSkinStorage().getSkin(player.getUUID()));
    }

    @Inject(method = "remove", at = @At("TAIL"))
    private void remove(ServerPlayer player, CallbackInfo ci) {
        SkinRestorer.getSkinStorage().removeSkin(player.getUUID());
    }

    @Inject(method = "removeAll", at = @At("HEAD"))
    private void disconnectAllPlayers(CallbackInfo ci) {
        for (ServerPlayer player : getPlayers()) {
            SkinRestorer.getSkinStorage().removeSkin(player.getUUID());
        }
    }
}
