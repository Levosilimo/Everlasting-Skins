/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Dispatches /skin metrics subcommands through a real CommandHandler with a
 * console-style sender, asserting the message content sent back. Console
 * senders bypass the permission gate (checkMetricsPermission returns true),
 * so no mock permission backend is needed.
 */
class MetricsCommandIT {

    private static final class FakeSender implements ICommandSender {
        final StringBuilder output = new StringBuilder();

        @Override
        public String getName() {
            return "TestConsole";
        }

        @Override
        public void sendMessage(ITextComponent component) {
            output.append(component.getUnformattedText()).append('\n');
        }

        @Override
        public boolean canUseCommand(int permLevel, String commandName) {
            return true;
        }

        @Override
        public World getEntityWorld() {
            return null;
        }

        @Override
        public MinecraftServer getServer() {
            return null;
        }
    }

    @Test
    @DisplayName("all /skin metrics subcommands dispatch through the real command handler")
    void metricsSubcommandsDispatchThroughRealCommandHandler() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerCommandManager commandManager = new ServerCommandManager(server);
        commandManager.registerCommand(new SkinCommand());
        FakeSender sender = new FakeSender();

        assertEquals(1, commandManager.executeCommand(sender, "/skin metrics"));
        assertTrue(sender.output.toString().contains("refreshes"),
            "human output should contain refresh counters");

        sender.output.setLength(0);
        assertEquals(1, commandManager.executeCommand(sender, "/skin metrics human"));
        assertTrue(sender.output.toString().contains("latencies (ms)"));

        sender.output.setLength(0);
        assertEquals(1, commandManager.executeCommand(sender, "/skin metrics json"));
        assertTrue(sender.output.toString().contains("\"uptimeMs\""),
            "json output should contain the uptime field");

        sender.output.setLength(0);
        assertEquals(1, commandManager.executeCommand(sender, "/skin metrics players"));
        assertTrue(sender.output.toString().contains("Top players by refresh count"));

        sender.output.setLength(0);
        assertEquals(1, commandManager.executeCommand(sender, "/skin metrics cleanup"));
        assertTrue(sender.output.toString().contains("pruned"));

        sender.output.setLength(0);
        assertEquals(1, commandManager.executeCommand(sender, "/skin metrics reset"));
        assertTrue(sender.output.toString().contains("Metrics reset"));
    }
}
