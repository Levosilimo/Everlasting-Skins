package levosilimo.everlastingskins.mixin.client;


import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Inject(method = "isMultiplayerEnabled", at = @At("HEAD"), cancellable = true)
    private void alwaysEnableMP(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @Inject(method = "isChatEnabled", at = @At("HEAD"), cancellable = true)
    private void alwaysEnableChat(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}

