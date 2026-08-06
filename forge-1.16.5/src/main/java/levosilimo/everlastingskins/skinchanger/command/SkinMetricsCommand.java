/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import levosilimo.everlastingskins.metrics.MetricsFormat;
import levosilimo.everlastingskins.metrics.PlayerSnapshot;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.metrics.Snapshot;
import levosilimo.everlastingskins.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.util.I18nUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

import java.util.Map;
import java.util.UUID;

/** The /skin metrics tree: human, json, players, cleanup, reset. */
public final class SkinMetricsCommand {

    private static final String FEEDBACK_PREFIX = "§6[EverlastingSkins]§f";
    private static final long CLEANUP_OLDER_THAN_MS = 30L * 24 * 60 * 60 * 1000;

    private SkinMetricsCommand() {
    }

    /** Source player, or null when the command came from the console. 1.16.5's CommandSource has no getPlayer() (1.21 has it); getPlayerOrException() throws instead. */
    private static ServerPlayerEntity sourcePlayer(CommandSource source) {
        try {
            return source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    public static LiteralArgumentBuilder<CommandSource> build() {
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
                        .requires(source -> source.hasPermission(2))
                        .executes(SkinMetricsCommand::cleanup))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .executes(SkinMetricsCommand::reset));
    }

    private static int metrics(CommandContext<CommandSource> context, boolean asJson) {
        ServerPlayerEntity player = sourcePlayer(context.getSource());
        if (player == null) {
            context.getSource().sendFailure(I18nUtils.getLocalizedComponent("player_only", player));
            return 0;
        }
        if (!hasPermission(context, "everlastingskins.command.metrics")) return 0;
        Snapshot snapshot = SkinMetrics.INSTANCE.snapshot();
        String output = asJson ? MetricsFormat.json(snapshot) : MetricsFormat.human(snapshot);
        context.getSource().sendSuccess(new StringTextComponent(output), false);
        return 1;
    }

    private static int players(CommandContext<CommandSource> context) {
        ServerPlayerEntity player = sourcePlayer(context.getSource());
        if (player == null || !hasPermission(context, "everlastingskins.command.metrics")) return 0;
        StringBuilder sb = new StringBuilder(FEEDBACK_PREFIX + " " + I18nUtils.formatMessage("metrics_top_players", player));
        int rank = 0;
        for (Map.Entry<UUID, PlayerSnapshot> e : SkinMetrics.INSTANCE.topPlayers(10)) {
            sb.append("\n  ").append(++rank).append(". ")
                    .append(e.getKey()).append(" — ")
                    .append(e.getValue().refreshCount()).append(I18nUtils.formatMessage("metrics_refreshes", player));
        }
        if (rank == 0) sb.append("\n  ").append(I18nUtils.formatMessage("metrics_no_refreshes", player));
        context.getSource().sendSuccess(new StringTextComponent(sb.toString()), false);
        return 1;
    }

    private static int cleanup(CommandContext<CommandSource> context) {
        ServerPlayerEntity player = sourcePlayer(context.getSource());
        if (!hasPermission(context, "everlastingskins.command.metrics.reset")) return 0;
        int removed = SkinMetrics.INSTANCE.cleanupStalePlayers(CLEANUP_OLDER_THAN_MS);
        context.getSource().sendSuccess(new StringTextComponent(
                FEEDBACK_PREFIX + " " + I18nUtils.formatMessage("metrics_cleanup", player, removed)), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSource> context) {
        ServerPlayerEntity player = sourcePlayer(context.getSource());
        if (!hasPermission(context, "everlastingskins.command.metrics.reset")) return 0;
        SkinMetrics.INSTANCE.reset();
        context.getSource().sendSuccess(new StringTextComponent(FEEDBACK_PREFIX + " " + I18nUtils.formatMessage("metrics_reset", player)), false);
        return 1;
    }

    private static boolean hasPermission(CommandContext<CommandSource> context, String node) {
        ServerPlayerEntity player = sourcePlayer(context.getSource());
        if (player == null) return false;
        PermissionContext ctx = PermissionContext.of(player.getUUID(), player);
        boolean allowed = PermissionServiceManager.hasPermission(ctx.uuid(), ctx.opLevel(), node);
        if (!allowed) {
            context.getSource().sendFailure(new StringTextComponent(FEEDBACK_PREFIX + " " + I18nUtils.formatMessage("permission_denied", player)));
        }
        return allowed;
    }
}
