/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.command.SkinActionCommand;
import levosilimo.everlastingskins.skinchanger.command.SkinMetricsCommand;
import levosilimo.everlastingskins.util.CompletionSources;
import levosilimo.everlastingskins.util.I18nUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.server.command.EnumArgument;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/** Registers the /skin command tree; execution logic lives in the command package. */
public class SkinCommand {

    static final String FEEDBACK_PREFIX = "§6[EverlastingSkins]§f";

    private static MineSkinAPI mineSkinAPIInstance;
    private static MojangAPI mojangAPIInstance;

    public static MineSkinAPI getMineSkinAPI() {
        if (mineSkinAPIInstance == null) {
            mineSkinAPIInstance = new MineSkinApiHttpImpl();
        }
        return mineSkinAPIInstance;
    }

    public static MojangAPI getMojangAPI() {
        if (mojangAPIInstance == null) {
            mojangAPIInstance = new MojangApiHttpImpl();
        }
        return mojangAPIInstance;
    }

    /* package-private for tests */
    static void resetAPIs() {
        mineSkinAPIInstance = null;
        mojangAPIInstance = null;
    }

    public static final class SkinActionParameters {
        private final Collection<ServerPlayerEntity> targets;
        private final SkinActionType type;
        private final SkinVariant variant;
        private final boolean withCape;
        private final String customSource;

        public SkinActionParameters(Collection<ServerPlayerEntity> targets, SkinActionType type,
                                    SkinVariant variant, boolean withCape, @Nullable String customSource) {
            this.targets = targets;
            this.type = type;
            this.variant = variant;
            this.withCape = withCape;
            this.customSource = customSource;
        }

        public Collection<ServerPlayerEntity> targets() { return targets; }

        public SkinActionType type() { return type; }

        public SkinVariant variant() { return variant; }

        public boolean withCape() { return withCape; }

        public String customSource() { return customSource; }
    }

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralArgumentBuilder<CommandSource> skinCommand = Commands.literal("skin")
                .then(buildSetSubcommand())
                .then(buildSourceSubcommand())
                .then(buildClearSubcommand())
                .then(SkinMetricsCommand.build().requires(SkinCommand::canUseMetrics));
        dispatcher.register(skinCommand);
    }

    private static boolean canUseMetrics(CommandSource source) {
        ServerPlayerEntity player = playerOf(source);
        if (player == null) return false;
        PermissionContext ctx = PermissionContext.of(player.getUUID(), player);
        return PermissionServiceManager.hasPermission(ctx.uuid(), ctx.opLevel(), "everlastingskins.command.metrics");
    }

    private static boolean canTargetOthers(CommandSource source) {
        ServerPlayerEntity player = playerOf(source);
        if (player == null) return false;
        PermissionContext ctx = PermissionContext.of(player.getUUID(), player);
        return PermissionServiceManager.hasPermission(ctx.uuid(), ctx.opLevel(), "everlastingskins.command.skin.other");
    }

    /** Source player, or null when the command came from the console. 1.16.5's CommandSource has no getPlayer() (1.21 has it); getPlayerOrException() throws instead. */
    private static ServerPlayerEntity playerOf(CommandSource source) {
        try {
            return source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return null;
        }
    }

    private static LiteralArgumentBuilder<CommandSource> buildSourceSubcommand() {
        return Commands.literal("source")
                .executes(context -> sourceAction(context, playerOf(context.getSource())))
                .then(Commands.argument("target", EntityArgument.player())
                        .requires(SkinCommand::canTargetOthers)
                        .executes(context -> sourceAction(context, EntityArgument.getPlayer(context, "target")))
                );
    }

    private static LiteralArgumentBuilder<CommandSource> buildClearSubcommand() {
        return Commands.literal("clear")
                .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                        Collections.singleton(playerOf(context.getSource())),
                        SkinActionType.clear, SkinVariant.ALL, false, null
                )))
                .then(Commands.argument("targets", EntityArgument.players()).requires(SkinCommand::canTargetOthers)
                        .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                EntityArgument.getPlayers(context, "targets"),
                                SkinActionType.clear, SkinVariant.ALL, false, null
                        )))
                );
    }

    private static LiteralArgumentBuilder<CommandSource> buildSetSubcommand() {
        return Commands.literal("set")
                .then(Commands.literal("mojang")
                        .then(Commands.argument("skin_name", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        ISuggestionProvider.suggest(CompletionSources.recentUsernames(), builder))
                                .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                        Collections.singleton(playerOf(context.getSource())),
                                        SkinActionType.username, SkinVariant.ALL, false,
                                        StringArgumentType.getString(context, "skin_name")
                                )))
                                .then(Commands.argument("targets", EntityArgument.players()).requires(SkinCommand::canTargetOthers)
                                        .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                                EntityArgument.getPlayers(context, "targets"),
                                                SkinActionType.username, SkinVariant.ALL, false,
                                                StringArgumentType.getString(context, "skin_name")
                                        )))
                                )
                        )
                )
                .then(Commands.literal("web")
                        .then(Commands.literal("classic")
                                .then(Commands.argument("url", StringArgumentType.string())
                                        .suggests((context, builder) ->
                                                ISuggestionProvider.suggest(CompletionSources.urlCandidates(), builder))
                                        .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                                Collections.singleton(playerOf(context.getSource())),
                                                SkinActionType.url, SkinVariant.CLASSIC, false,
                                                StringArgumentType.getString(context, "url")
                                        )))
                                        .then(Commands.argument("targets", EntityArgument.players()).requires(SkinCommand::canTargetOthers)
                                                .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.url, SkinVariant.CLASSIC, true,
                                                        StringArgumentType.getString(context, "url")
                                                )))
                                        )
                                )
                        )
                        .then(Commands.literal("slim")
                                .then(Commands.argument("url", StringArgumentType.string())
                                        .suggests((context, builder) ->
                                                ISuggestionProvider.suggest(CompletionSources.urlCandidates(), builder))
                                        .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                                Collections.singleton(playerOf(context.getSource())),
                                                SkinActionType.url, SkinVariant.SLIM, false,
                                                StringArgumentType.getString(context, "url")
                                        )))
                                        .then(Commands.argument("targets", EntityArgument.players()).requires(SkinCommand::canTargetOthers)
                                                .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.url, SkinVariant.SLIM, true,
                                                        StringArgumentType.getString(context, "url")
                                                )))
                                        )
                                )

                        )
                )
                .then(Commands.literal("random")
                        .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                Collections.singleton(playerOf(context.getSource())),
                                SkinActionType.random, SkinVariant.ALL, false, null
                        )))
                        .then(Commands.argument("cape", BoolArgumentType.bool())
                                .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                        Collections.singleton(playerOf(context.getSource())),
                                        SkinActionType.random, SkinVariant.ALL, BoolArgumentType.getBool(context, "cape"), null
                                )))
                                .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                        .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                                Collections.singleton(playerOf(context.getSource())),
                                                SkinActionType.random, context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                        )))
                                )
                        )
                        .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                        Collections.singleton(playerOf(context.getSource())),
                                        SkinActionType.random, context.getArgument("skin variant", SkinVariant.class), false, null
                                )))
                                .then(Commands.argument("cape", BoolArgumentType.bool())
                                        .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                                Collections.singleton(playerOf(context.getSource())),
                                                SkinActionType.random, context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                        )))
                                )
                        )
                        .then(Commands.argument("targets", EntityArgument.players()).requires(SkinCommand::canTargetOthers)
                                .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                        EntityArgument.getPlayers(context, "targets"),
                                        SkinActionType.random, SkinVariant.ALL, false, null
                                )))
                                .then(Commands.argument("cape", BoolArgumentType.bool())
                                        .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                                EntityArgument.getPlayers(context, "targets"),
                                                SkinActionType.random, SkinVariant.ALL, BoolArgumentType.getBool(context, "cape"), null
                                        )))
                                        .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                                .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.random, context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                                )))
                                        )
                                )
                                .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                        .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                                EntityArgument.getPlayers(context, "targets"),
                                                SkinActionType.random, context.getArgument("skin variant", SkinVariant.class), false, null
                                        )))
                                        .then(Commands.argument("cape", BoolArgumentType.bool())
                                                .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.random, context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                                )))
                                        )
                                )
                        )
                );
    }

    private static int sourceAction(CommandContext<CommandSource> context, ServerPlayerEntity target) {
        UUID uuid = target.getUUID();
        if (SkinRestorer.getSkinStorage().hasDefaultSkin(uuid)) {
            context.getSource().sendSuccess(new StringTextComponent(FEEDBACK_PREFIX + " " + target.getGameProfile().getName()), false);
            return 1;
        }
        String source = SkinRestorer.getSkinStorage().getSource(uuid);
        ITextComponent message = source != null
            ? new StringTextComponent(FEEDBACK_PREFIX + " " + source)
            : new StringTextComponent(FEEDBACK_PREFIX + " " + I18nUtils.formatMessage("no_source", target));
        context.getSource().sendSuccess(message, false);
        return 1;
    }
}
