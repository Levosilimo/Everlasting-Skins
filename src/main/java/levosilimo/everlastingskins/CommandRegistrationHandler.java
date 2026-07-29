package levosilimo.everlastingskins;

import com.mojang.brigadier.CommandDispatcher;
import levosilimo.everlastingskins.skinchanger.SkinCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EverlastingSkins.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommandRegistrationHandler {
    private CommandRegistrationHandler() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        SkinCommand.register(dispatcher);
    }
}
