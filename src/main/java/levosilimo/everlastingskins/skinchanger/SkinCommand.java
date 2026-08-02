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
import levosilimo.everlastingskins.util.I18nUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
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

    public record SkinActionParameters(
            Collection<ServerPlayer> targets,
            SkinActionType type,
            SkinVariant variant,
            boolean withCape,
            @Nullable String customSource
    ) {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> skinCommand = Commands.literal("skin")
                .then(buildSetSubcommand())
                .then(buildSourceSubcommand())
                .then(buildClearSubcommand())
                .then(SkinMetricsCommand.build());
        dispatcher.register(skinCommand);
    }

    private static boolean canTargetOthers(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return false;
        PermissionContext ctx = PermissionContext.of(player.getUUID(), player);
        return PermissionServiceManager.hasPermission(ctx, "everlastingskins.command.skin.other");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSourceSubcommand() {
        return Commands.literal("source")
                .executes(context -> sourceAction(context, context.getSource().getPlayer()))
                .then(Commands.argument("target", EntityArgument.player())
                        .requires(SkinCommand::canTargetOthers)
                        .executes(context -> sourceAction(context, EntityArgument.getPlayer(context, "target")))
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildClearSubcommand() {
        return Commands.literal("clear")
                .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                        Collections.singleton(context.getSource().getPlayer()),
                        SkinActionType.clear, SkinVariant.ALL, false, null
                )))
                .then(Commands.argument("targets", EntityArgument.players()).requires(SkinCommand::canTargetOthers)
                        .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                EntityArgument.getPlayers(context, "targets"),
                                SkinActionType.clear, SkinVariant.ALL, false, null
                        )))
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSetSubcommand() {
        return Commands.literal("set")
                .then(Commands.literal("mojang")
                        .then(Commands.argument("skin_name", StringArgumentType.word())
                                .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                        Collections.singleton(context.getSource().getPlayer()),
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
                                        .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                                Collections.singleton(context.getSource().getPlayer()),
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
                                        .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                                Collections.singleton(context.getSource().getPlayer()),
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
                                Collections.singleton(context.getSource().getPlayer()),
                                SkinActionType.random, SkinVariant.ALL, false, null
                        )))
                        .then(Commands.argument("cape", BoolArgumentType.bool())
                                .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                        Collections.singleton(context.getSource().getPlayer()),
                                        SkinActionType.random, SkinVariant.ALL, BoolArgumentType.getBool(context, "cape"), null
                                )))
                                .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                        .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                                Collections.singleton(context.getSource().getPlayer()),
                                                SkinActionType.random, context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                        )))
                                )
                        )
                        .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                        Collections.singleton(context.getSource().getPlayer()),
                                        SkinActionType.random, context.getArgument("skin variant", SkinVariant.class), false, null
                                )))
                                .then(Commands.argument("cape", BoolArgumentType.bool())
                                        .executes(context -> SkinActionCommand.execute(context, new SkinActionParameters(
                                                Collections.singleton(context.getSource().getPlayer()),
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

    private static int sourceAction(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        UUID uuid = target.getUUID();
        if (SkinRestorer.getSkinStorage().hasDefaultSkin(uuid)) {
            context.getSource().sendSuccess(() -> Component.literal(FEEDBACK_PREFIX + " " + target.getGameProfile().getName()), false);
            return 1;
        }
        String source = SkinRestorer.getSkinStorage().getSource(uuid);
        MutableComponent message = source != null
            ? Component.literal(FEEDBACK_PREFIX + " " + source)
            : Component.literal(FEEDBACK_PREFIX + " " + I18nUtils.get("no_source"));
        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }
}
