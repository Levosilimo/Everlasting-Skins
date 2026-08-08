/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import levosilimo.everlastingskins.metrics.MetricsFormat;
import levosilimo.everlastingskins.metrics.PlayerSnapshot;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.metrics.Snapshot;
import levosilimo.everlastingskins.forge26_1.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.forge26_1.util.I18nUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

/** The /skin metrics tree: human, json, players, cleanup, reset. */
public final class SkinMetricsCommand {

    private static final String FEEDBACK_PREFIX = "§6[EverlastingSkins]§f";
    private static final long CLEANUP_OLDER_THAN_MS = 30L * 24 * 60 * 60 * 1000;

    private SkinMetricsCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("metrics")
                .executes(context -> metrics(context, false))
                .then(Commands.literal("json")
                        .executes(context -> metrics(context, true)))
                .then(Commands.literal("players")
                        .executes(SkinMetricsCommand::players))
                /**
                 * cleanup is destructive like reset: gate it behind the same
                 * op level (everlastingskins.command.metrics.reset) so the
                 * client tree and tab completion do not offer it to senders
                 * holding only everlastingskins.command.metrics. The executor
                 * still re-checks the reset permission (parity with 1.12.2's
                 * metricsSubcommands filter).
                 */
                .then(Commands.literal("cleanup")
                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                        .executes(SkinMetricsCommand::cleanup))
                .then(Commands.literal("reset")
                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                        .executes(SkinMetricsCommand::reset));
    }

    private static int metrics(CommandContext<CommandSourceStack> context, boolean asJson) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(I18nUtils.getLocalizedComponent("player_only", player));
            return 0;
        }
        if (!hasPermission(context, "everlastingskins.command.metrics")) return 0;
        Snapshot snapshot = SkinMetrics.INSTANCE.snapshot();
        String output = asJson ? MetricsFormat.json(snapshot) : MetricsFormat.human(snapshot);
        context.getSource().sendSuccess(() -> Component.literal(output), false);
        return 1;
    }

    private static int players(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || !hasPermission(context, "everlastingskins.command.metrics")) return 0;
        StringBuilder sb = new StringBuilder(FEEDBACK_PREFIX + " " + I18nUtils.formatMessage("metrics_top_players", player));
        int rank = 0;
        for (Map.Entry<UUID, PlayerSnapshot> e : SkinMetrics.INSTANCE.topPlayers(10)) {
            sb.append("\n  ").append(++rank).append(". ")
                    .append(e.getKey()).append(" — ")
                    .append(e.getValue().refreshCount()).append(I18nUtils.formatMessage("metrics_refreshes", player));
        }
        if (rank == 0) sb.append("\n  ").append(I18nUtils.formatMessage("metrics_no_refreshes", player));
        context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int cleanup(CommandContext<CommandSourceStack> context) {
        if (!hasPermission(context, "everlastingskins.command.metrics.reset")) return 0;
        int removed = SkinMetrics.INSTANCE.cleanupStalePlayers(CLEANUP_OLDER_THAN_MS);
        context.getSource().sendSuccess(() -> Component.literal(
                FEEDBACK_PREFIX + " " + I18nUtils.formatMessage("metrics_cleanup", context.getSource().getPlayer(), removed)), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        if (!hasPermission(context, "everlastingskins.command.metrics.reset")) return 0;
        SkinMetrics.INSTANCE.reset();
        context.getSource().sendSuccess(() -> Component.literal(FEEDBACK_PREFIX + " " + I18nUtils.formatMessage("metrics_reset", context.getSource().getPlayer())), false);
        return 1;
    }

    private static boolean hasPermission(CommandContext<CommandSourceStack> context, String node) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return false;
        PermissionContext ctx = PermissionContext.of(player.getUUID(), player);
        boolean allowed = PermissionServiceManager.hasPermission(ctx.uuid(), ctx.opLevel(), node);
        if (!allowed) {
            context.getSource().sendFailure(Component.literal(FEEDBACK_PREFIX + " " + I18nUtils.formatMessage("permission_denied", player)));
        }
        return allowed;
    }
}
