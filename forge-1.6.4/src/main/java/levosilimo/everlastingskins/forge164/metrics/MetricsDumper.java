/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge164.metrics;

import cpw.mods.fml.common.IScheduledTickHandler;
import cpw.mods.fml.common.TickType;
import levosilimo.everlastingskins.forge164.EverlastingSkins;
import levosilimo.everlastingskins.forge164.config.Config;
import levosilimo.everlastingskins.metrics.MetricsFormat;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;

/**
 * Periodically dumps the metrics snapshot to
 * {@code <gameDir>/dumps/everlastingskins/metrics.json} using an atomic
 * temp-file + rename write — ported from the forge-1.7.10 reference
 * (PR #477, itself off the mc1.12.2 MetricsDumper).
 *
 * <p>1.6.4-era surface: FML 6.x has NO {@code TickEvent} (the
 * {@code cpw.mods.fml.common.gameevent} package and the event-bus tick
 * events arrive with FML 7 / 1.7 — verified absent from the vendored
 * decompiled 9.11.1.1345 tree). The tick hook is
 * {@link IScheduledTickHandler} registered with
 * {@code TickRegistry.registerScheduledTickHandler(handler, Side.SERVER)};
 * {@link #nextTickSpacing()} IS the dump interval (no tick-counter modulo
 * needed, unlike the 1.7.10 event version), so a hung server simply stops
 * dumping instead of spinning wall-clock timers.
 */
public class MetricsDumper implements IScheduledTickHandler {

    private static final String RELATIVE_TARGET = "dumps/everlastingskins/metrics.json";

    @Override
    public void tickStart(EnumSet<TickType> type, Object... tickData) {
    }

    @Override
    public void tickEnd(EnumSet<TickType> type, Object... tickData) {
        if (!Config.metricsEnabled) return;
        if (Config.metricsDumpIntervalSeconds <= 0) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        writeMetricsJson(server);
    }

    @Override
    public EnumSet<TickType> ticks() {
        return EnumSet.of(TickType.SERVER);
    }

    @Override
    public String getLabel() {
        return "EverlastingSkinsMetricsDumper";
    }

    /** Server-tick spacing between dumps; the 1.6.4 scheduled-handler analogue of the interval modulo. */
    @Override
    public int nextTickSpacing() {
        return Math.max(Config.metricsDumpIntervalSeconds * 20, 1);
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
