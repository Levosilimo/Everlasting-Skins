/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.metrics;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Periodically dumps the metrics snapshot to
 * {@code <gameDir>/dumps/everlastingskins/metrics.json} using an atomic
 * temp-file + rename write. Scheduled by server tick count so a hung server
 * simply stops dumping instead of spinning wall-clock timers.
 */
public class MetricsDumper {

    private static final Path RELATIVE_TARGET = Paths.get("dumps", "everlastingskins", "metrics.json");

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
            Path target = server.getFile(RELATIVE_TARGET.toString()).toPath();
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
