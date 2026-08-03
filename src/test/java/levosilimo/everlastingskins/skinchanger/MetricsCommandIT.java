/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.metrics.SkinMetrics;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
        assertTrue(sender.output.toString().contains("metrics_top_players"));

        sender.output.setLength(0);
        assertEquals(1, commandManager.executeCommand(sender, "/skin metrics cleanup"));
        assertTrue(sender.output.toString().contains("metrics_cleanup"));

        sender.output.setLength(0);
        assertEquals(1, commandManager.executeCommand(sender, "/skin metrics reset"));
        assertTrue(sender.output.toString().contains("metrics_reset"));
    }

    @Test
    @DisplayName("/skin metrics json surfaces seeded counter values")
    void metricsJson_surfacesSeededCounters() {
        SkinMetrics.INSTANCE.reset();
        UUID seeded = UUID.randomUUID();
        SkinMetrics.INSTANCE.recordTimedOut(seeded);
        SkinMetrics.INSTANCE.recordProviderStatus(429);
        SkinMetrics.INSTANCE.recordCacheHit();
        SkinMetrics.INSTANCE.recordTickSpike(TimeUnit.MILLISECONDS.toNanos(120));

        MinecraftServer server = mock(MinecraftServer.class);
        ServerCommandManager commandManager = new ServerCommandManager(server);
        commandManager.registerCommand(new SkinCommand());
        FakeSender sender = new FakeSender();

        assertEquals(1, commandManager.executeCommand(sender, "/skin metrics json"));
        String output = sender.output.toString();
        assertTrue(output.contains("\"timedOut\":1"), "json should report the seeded timeout; got: " + output);
        assertTrue(output.contains("\"http429\":1"), "json should report the seeded 429; got: " + output);
        assertTrue(output.contains("\"hits\":1"), "json should report the seeded cache hit; got: " + output);
        assertTrue(output.contains("\"tickSpikes\":{\"total\":1"),
            "json should report the seeded tick spike; got: " + output);
    }

    @Test
    @DisplayName("/skin metrics players lists the most active player UUID")
    void metricsPlayers_listsSeededTopPlayer() {
        SkinMetrics.INSTANCE.reset();
        UUID seeded = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            SkinMetrics.INSTANCE.recordRefreshStarted(seeded);
            SkinMetrics.INSTANCE.recordRefreshCompleted(seeded, System.nanoTime(), 0L, 0L, 0L);
        }

        MinecraftServer server = mock(MinecraftServer.class);
        ServerCommandManager commandManager = new ServerCommandManager(server);
        commandManager.registerCommand(new SkinCommand());
        FakeSender sender = new FakeSender();

        assertEquals(1, commandManager.executeCommand(sender, "/skin metrics players"));
        String output = sender.output.toString();
        assertTrue(output.contains(seeded.toString()),
            "players list must include the seeded top player " + seeded + "; got: " + output);
    }
}
