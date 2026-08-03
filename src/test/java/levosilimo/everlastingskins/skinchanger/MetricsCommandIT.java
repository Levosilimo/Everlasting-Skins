/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.JsonUtils;
import com.google.gson.JsonObject;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        // Parse the structured JSON (Gson, already shipped via JsonUtils) and
        // assert exact numeric equality: the previous prefix-insensitive
        // contains("\"timedOut\":1") would also match 11, 12, ... The
        // 1-vs-11 boundary is pinned by the negative control below.
        JsonObject json = JsonUtils.parseJson(jsonPayload(output));
        assertEquals(1, json.get("refreshes").getAsJsonObject().get("timedOut").getAsLong(),
            "json should report the seeded timeout; got: " + output);
        assertEquals(1, json.get("provider").getAsJsonObject().get("http429").getAsLong(),
            "json should report the seeded 429; got: " + output);
        assertEquals(1, json.get("cache").getAsJsonObject().get("hits").getAsLong(),
            "json should report the seeded cache hit; got: " + output);
        assertEquals(1, json.get("tickSpikes").getAsJsonObject().get("total").getAsLong(),
            "json should report the seeded tick spike; got: " + output);
    }

    @Test
    @DisplayName("/skin metrics json reads counters as exact numbers, not prefix matches")
    void metricsJson_counterBoundaryDistinguishesOneFromEleven() {
        SkinMetrics.INSTANCE.reset();
        UUID seeded = UUID.randomUUID();
        for (int i = 0; i < 11; i++) {
            SkinMetrics.INSTANCE.recordTimedOut(seeded);
        }

        MinecraftServer server = mock(MinecraftServer.class);
        ServerCommandManager commandManager = new ServerCommandManager(server);
        commandManager.registerCommand(new SkinCommand());
        FakeSender sender = new FakeSender();

        assertEquals(1, commandManager.executeCommand(sender, "/skin metrics json"));
        String output = sender.output.toString();
        assertEquals(11, JsonUtils.parseJson(jsonPayload(output)).get("refreshes")
                .getAsJsonObject().get("timedOut").getAsLong(),
            "timedOut must be read as the exact number 11; got: " + output);
        // Negative control: the prefix-insensitive "timedOut":1 pattern
        // the old assertion used would also pass for 11; the exact field
        // boundary must not match, and the 11 boundary must.
        assertFalse(output.contains("\"timedOut\":1,"),
            "the 1-boundary pattern must not match a counter of 11; got: " + output);
        assertTrue(output.contains("\"timedOut\":11,"),
            "the 11-boundary pattern must match exactly; got: " + output);
    }

    /** The chat message is PREFIX + JSON + newline; hand back the JSON object. */
    private static String jsonPayload(String output) {
        return output.substring(output.indexOf('{'));
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
