/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge1710.metrics;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import levosilimo.everlastingskins.forge1710.EverlastingSkins;
import levosilimo.everlastingskins.forge1710.config.Config;
import levosilimo.everlastingskins.metrics.MetricsFormat;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Periodically dumps the metrics snapshot to
 * {@code <gameDir>/dumps/everlastingskins/metrics.json} using an atomic
 * temp-file + rename write. Scheduled by server tick count (FML 7
 * {@link TickEvent.ServerTickEvent} on the Forge bus) so a hung server
 * simply stops dumping instead of spinning wall-clock timers — ported from
 * the mc1.12.2 MetricsDumper.
 */
public class MetricsDumper {

    private static final String RELATIVE_TARGET = "dumps/everlastingskins/metrics.json";

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!Config.metricsEnabled) return;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        int intervalTicks = Config.metricsDumpIntervalSeconds * 20;
        if (intervalTicks <= 0 || server.getTickCounter() % intervalTicks != 0) return;
        writeMetricsJson(server);
    }

    static void writeMetricsJson(MinecraftServer server) {
        try {
            Path target = server.getFile(RELATIVE_TARGET).toPath();
            Path dir = target.getParent();
            if (dir == null) return;
            Files.createDirectories(dir);
            Path temp = dir.resolve(target.getFileName() + ".tmp");
            Files.write(temp, MetricsFormat.json(SkinMetrics.INSTANCE.snapshot()).getBytes(StandardCharsets.UTF_8));
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            EverlastingSkins.logger.warn("Failed to write metrics dump", e);
        }
    }
}
