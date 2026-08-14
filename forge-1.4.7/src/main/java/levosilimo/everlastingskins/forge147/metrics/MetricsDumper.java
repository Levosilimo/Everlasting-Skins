/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge147.metrics;

import cpw.mods.fml.common.IScheduledTickHandler;
import cpw.mods.fml.common.TickType;
import levosilimo.everlastingskins.forge147.EverlastingSkins;
import levosilimo.everlastingskins.forge147.config.Config;
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
 * temp-file + rename write — ported from the forge-1.5.2 reference
 * (PR #479), itself the forge-1.6.4 (PR #478) / forge-1.7.10 (PR #477,
 * off the mc1.12.2 MetricsDumper) ports.
 *
 * <p>1.4.7-era surface: FML 4.7 has NO {@code TickEvent} (the
 * {@code cpw.mods.fml.common.gameevent} package and the event-bus tick
 * events arrive with FML 7 / 1.7 — verified absent from the vendored
 * decompiled tree), but the {@link IScheduledTickHandler} interface set
 * and {@code TickRegistry.registerScheduledTickHandler(handler,
 * Side.SERVER)} EXIST on FML 4.7 (verified — same shape as the 1.5.2 /
 * 1.6.4 siblings), so the same scheduled-handler pattern applies.
 * {@link #nextTickSpacing()} IS the dump interval, so a hung server
 * simply stops dumping instead of spinning wall-clock timers.
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

    /** Server-tick spacing between dumps; the scheduled-handler analogue of the 1.7.10 interval modulo. */
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
