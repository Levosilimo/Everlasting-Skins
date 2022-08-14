package levosilimo.everlastingskins.mixin.server.data;

import com.mojang.brigadier.CommandDispatcher;
import levosilimo.everlastingskins.skinchanger.SkinCommand;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public interface MixinTickingEntities {
    @Accessor("tickingEntities") boolean isTickingEntity();
}
