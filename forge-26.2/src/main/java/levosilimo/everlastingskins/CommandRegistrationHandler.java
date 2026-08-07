/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins;

import com.mojang.brigadier.CommandDispatcher;
import levosilimo.everlastingskins.forge26.skinchanger.SkinCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * Static holder for the /skin command tree registration. EventBus 7 note:
 * this lane has no {@code @Mod.EventBusSubscriber} — EverlastingSkins wires
 * {@link #onRegisterCommands} onto {@code RegisterCommandsEvent.BUS}
 * explicitly.
 */
public final class CommandRegistrationHandler {
    private CommandRegistrationHandler() {
    }

    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        SkinCommand.register(dispatcher);
    }
}
