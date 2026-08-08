/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.forge26_1.metrics;

import levosilimo.everlastingskins.metrics.MetricsFormat;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Periodically dumps the metrics snapshot to
 * {@code <gameDir>/dumps/everlastingskins/metrics.json} using an atomic
 * temp-file + rename write. Scheduled by server tick count so a hung server
 * simply stops dumping instead of spinning wall-clock timers.
 *
 * <p>26.2 note: {@code TickEvent.ServerTickEvent} is split into
 * {@code Pre}/{@code Post} records, each with its own typed {@code EventBus}
 * and a {@code server()} accessor; this listener is wired to the Post bus
 * (the phase check is no longer needed).
 */
public class MetricsDumper {

    private static final Path RELATIVE_TARGET = Path.of("dumps", "everlastingskins", "metrics.json");

    public void onServerTick(TickEvent.ServerTickEvent.Post event) {
        if (!Config.METRICS_ENABLED.get()) return;
        MinecraftServer server = event.server();
        if (server == null) return;
        int intervalTicks = Config.METRICS_DUMP_INTERVAL_SECONDS.get() * 20;
        if (intervalTicks <= 0 || server.getTickCount() % intervalTicks != 0) return;
        writeMetricsJson(server);
    }

    static void writeMetricsJson(MinecraftServer server) {
        try {
            Path target = server.getFile(RELATIVE_TARGET.toString());
            Path dir = target.getParent();
            if (dir == null) return;
            Files.createDirectories(dir);
            Path temp = dir.resolve(target.getFileName() + ".tmp");
            Files.writeString(temp, MetricsFormat.json(SkinMetrics.INSTANCE.snapshot()),
                    StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            EverlastingSkins.logger.warn("Failed to write metrics dump", e);
        }
    }
}
