package levosilimo.everlastingskins.mixin.server;

import com.mojang.brigadier.CommandDispatcher;
import levosilimo.everlastingskins.skinchanger.SkinCommand;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class MixinCommandManager {
    @Final
    @Shadow
    private CommandDispatcher<CommandSource> dispatcher;

    @Inject(method = "<init>", at = @At(value = "TAIL"))
    private void init(boolean p_i49161_1_, CallbackInfo ci) {
        SkinCommand.register(dispatcher);
    }
}
