package levosilimo.everlastingskins.skinchanger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.I18nUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.*;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.Util;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.GameType;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.IWorldInfo;
import net.minecraftforge.server.command.EnumArgument;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.*;


public class SkinCommand {

    private static final ScheduledExecutorService skinCommandExecutor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    private static final String FEEDBACK_PREFIX = "§6[EverlastingSkins]§f";

    private static I18nUtils i18nUtils;

    public static final MineSkinAPIImpl mineSkinAPI = new MineSkinAPIImpl();
    public static final MojangAPIImpl mojangAPI = new MojangAPIImpl();

    private static String getLocalizedString(String key) {
        if(i18nUtils == null) i18nUtils = new I18nUtils();
        return i18nUtils.getLocalizedString(key, Config.LANGUAGE.get());
    }


    public static void register(CommandDispatcher<CommandSource> dispatcher) {

        // Command: /skin source
        LiteralArgumentBuilder<CommandSource> skinSourceLiteral = Commands.literal("source")
                .executes(context -> sourceAction(context, context.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> sourceAction(context, EntityArgument.getPlayer(context, "target")))
                );

        // Command: /skin clear
        LiteralArgumentBuilder<CommandSource> skinClearLiteral = Commands.literal("clear")
                .executes(context -> skinAction(context,
                        Collections.singleton(context.getSource().getPlayerOrException()),
                        SkinActionType.clear, context.getSource().hasPermission(3), SkinVariant.ALL, false, null
                ))
                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                        .executes(context -> skinAction(context,
                                EntityArgument.getPlayers(context, "targets"),
                                SkinActionType.clear, context.getSource().hasPermission(3), SkinVariant.ALL, false, null
                        ))
                );

        // Command: /skin set
        LiteralArgumentBuilder<CommandSource> skinSetLiteral = Commands.literal("set")
                // Command: /skin set mojang
                .then(Commands.literal("mojang")
                        .then(Commands.argument("skin_name", StringArgumentType.word())
                                .executes(context -> skinAction(context,
                                        Collections.singleton(context.getSource().getPlayerOrException()),
                                        SkinActionType.username, context.getSource().hasPermission(3), SkinVariant.ALL, false,
                                        StringArgumentType.getString(context, "skin_name")
                                ))
                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                        .executes(context -> skinAction(context,
                                                EntityArgument.getPlayers(context, "targets"),
                                                SkinActionType.username, context.getSource().hasPermission(3), SkinVariant.ALL, false,
                                                StringArgumentType.getString(context, "skin_name")
                                        ))
                                )
                        )
                )
                // Command: /skin set web
                .then(Commands.literal("web")
                        .then(Commands.literal("classic")
                                .then(Commands.argument("url", StringArgumentType.string())
                                        .executes(context -> skinAction(context,
                                                Collections.singleton(context.getSource().getPlayerOrException()),
                                                SkinActionType.url, context.getSource().hasPermission(3), SkinVariant.CLASSIC, false,
                                                StringArgumentType.getString(context, "url")
                                        ))
                                        .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                .executes(context -> skinAction(context,
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.url, context.getSource().hasPermission(3), SkinVariant.CLASSIC, true,
                                                        StringArgumentType.getString(context, "url")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("slim")
                                .then(Commands.argument("url", StringArgumentType.string())
                                        .executes(context -> skinAction(context,
                                                Collections.singleton(context.getSource().getPlayerOrException()),
                                                SkinActionType.url, context.getSource().hasPermission(3), SkinVariant.SLIM, false,
                                                StringArgumentType.getString(context, "url")
                                        ))
                                        .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                .executes(context -> skinAction(context,
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.url, context.getSource().hasPermission(3), SkinVariant.SLIM, true,
                                                        StringArgumentType.getString(context, "url")
                                                ))
                                        )
                                )

                        )
                )
                // Command: /skin set random
                .then(Commands.literal("random")
                        .executes(context -> skinAction(context,
                                Collections.singleton(context.getSource().getPlayerOrException()),
                                SkinActionType.random, context.getSource().hasPermission(3), SkinVariant.ALL, false, null
                        ))
                        .then(Commands.argument("cape", BoolArgumentType.bool())
                                .executes(context -> skinAction(context,
                                        Collections.singleton(context.getSource().getPlayerOrException()),
                                        SkinActionType.random, context.getSource().hasPermission(3), SkinVariant.ALL, BoolArgumentType.getBool(context, "cape"), null
                                ))
                                .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                        .executes(context -> skinAction(context,
                                                Collections.singleton(context.getSource().getPlayerOrException()),
                                                SkinActionType.random, context.getSource().hasPermission(3), context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                        ))
                                )
                        )
                        .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                .executes(context -> skinAction(context,
                                        Collections.singleton(context.getSource().getPlayerOrException()),
                                        SkinActionType.random, context.getSource().hasPermission(3), context.getArgument("skin variant", SkinVariant.class), false, null
                                ))
                                .then(Commands.argument("cape", BoolArgumentType.bool())
                                        .executes(context -> skinAction(context,
                                                Collections.singleton(context.getSource().getPlayerOrException()),
                                                SkinActionType.random, context.getSource().hasPermission(3), context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                        ))
                                )
                        )
                        .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                .executes(context -> skinAction(context,
                                        EntityArgument.getPlayers(context, "targets"),
                                        SkinActionType.random, context.getSource().hasPermission(3), SkinVariant.ALL, false, null
                                ))
                                .then(Commands.argument("cape", BoolArgumentType.bool())
                                        .executes(context -> skinAction(context,
                                                EntityArgument.getPlayers(context, "targets"),
                                                SkinActionType.random, context.getSource().hasPermission(3), SkinVariant.ALL, BoolArgumentType.getBool(context, "cape"), null
                                        ))
                                        .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                                .executes(context -> skinAction(context,
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.random, context.getSource().hasPermission(3), context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                                ))
                                        )
                                )
                                .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                        .executes(context -> skinAction(context,
                                                EntityArgument.getPlayers(context, "targets"),
                                                SkinActionType.random, context.getSource().hasPermission(3), context.getArgument("skin variant", SkinVariant.class), false, null
                                        ))
                                        .then(Commands.argument("cape", BoolArgumentType.bool())
                                                .executes(context -> skinAction(context,
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.random, context.getSource().hasPermission(3), context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                                ))
                                        )
                                )
                        )
                );

        LiteralArgumentBuilder<CommandSource> skinCommand = Commands.literal("skin")
                .then(skinSetLiteral)
                .then(skinSourceLiteral)
                .then(skinClearLiteral);

        dispatcher.register(skinCommand);
    }

    private static int sourceAction(CommandContext<CommandSource> context, ServerPlayerEntity target) {
        String source = SkinStorage.getInstance().getSource(target.getUUID());
        StringTextComponent message;
        if(source == null) message = new StringTextComponent(FEEDBACK_PREFIX + " " + getLocalizedString("no_source"));
        else message = new StringTextComponent(FEEDBACK_PREFIX + " " + source);
        context.getSource().sendSuccess(message, false);
        return 1;
    }

    private static int skinAction(CommandContext<CommandSource> context, Collection<ServerPlayerEntity> targets, SkinActionType type, boolean setByOperator, SkinVariant variant, boolean withCape, @Nullable String customSource) {
        targets.forEach(player -> {
                if(Config.TOGGLE.get()) {
                    if(player == context.getSource().getEntity()) context.getSource().sendSuccess(new StringTextComponent(FEEDBACK_PREFIX + " " + getLocalizedString("change")), false);
                    else player.sendMessage(new StringTextComponent(FEEDBACK_PREFIX + " " + getLocalizedString("change")), Util.NIL_UUID);
                }
        });

        CompletableFuture<CustomSkinProperty> skinPropertyCompletableFuture = CompletableFuture.supplyAsync(() -> {
            CustomSkinProperty skinProperty = null;
            switch (type) {
                case clear:
                    skinProperty = mojangAPI.getSkin(targets.stream().findFirst().get().getGameProfile().getName()).skinProperty();
                    break;
                case url:
                    skinProperty = mineSkinAPI.genSkin(customSource, variant).property();
                    break;
                case username:
                    skinProperty = mojangAPI.getSkin(customSource).skinProperty();
                    break;
                case random:
                    try {
                        skinProperty = mojangAPI.getSkin(Objects.requireNonNull(RandomMojangSkin.randomUsername(withCape, variant))).skinProperty();
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                    break;
                case NEW:
                    try {
                        skinProperty = mojangAPI.getSkin(Objects.requireNonNull(RandomMojangSkin.newUsername(variant))).skinProperty();
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                    break;
            }
            return skinProperty;
        }, skinCommandExecutor).whenComplete((skinProperty, throwable) -> {
            if(throwable != null || skinProperty == null) {
                EverlastingSkins.logger.error("Skin process error occurred");
                for(ServerPlayerEntity player: targets) player.sendMessage(new StringTextComponent(FEEDBACK_PREFIX + " " + getLocalizedString("error")), Util.NIL_UUID);
                return;
            }
            for (ServerPlayerEntity player : targets) {
                SkinStorage.getInstance().setSkin(player, skinProperty);
                if(Config.TOGGLE.get()) {
                    if(player == context.getSource().getEntity()) context.getSource().sendSuccess(new StringTextComponent(FEEDBACK_PREFIX + " " + getLocalizedString("fulfilled")), false);
                    else player.sendMessage(new StringTextComponent(FEEDBACK_PREFIX + " " + getLocalizedString("fulfilled_force")), Util.NIL_UUID);
                }
            }
            for (ServerPlayerEntity player : targets) {
                SkinRestorer.server.execute(() -> task(player));
            }
        });

        skinCommandExecutor.schedule(() -> {
            if(skinPropertyCompletableFuture.completeExceptionally(new TimeoutException("Skin fetch timeout occurred"))) {
                EverlastingSkins.logger.error(getLocalizedString("timeout"));
                for(ServerPlayerEntity player: targets) player.sendMessage(new StringTextComponent(FEEDBACK_PREFIX + " " + getLocalizedString("timeout")), Util.NIL_UUID);
            }
        }, 10000, TimeUnit.MILLISECONDS);

        return targets.size();
    }

    private static void task(ServerPlayerEntity player) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.yRot;
        float pitch = player.xRot;
        GameType gameType = player.gameMode.getGameModeForPlayer();
        GameType previousGameType = player.gameMode.getPreviousGameModeForPlayer();
        boolean isDebug = player.getLevel().isDebug();
        boolean isFlat = player.getLevel().isFlat();
        SkinStorage.getInstance().saveSkin(player);
        player.getGameProfile().getProperties().removeAll("textures");
        player.getGameProfile().getProperties().put("textures", SkinStorage.getInstance().getSkin(player).getOriginalProperty());
        ServerWorld serverworld = player.getLevel();
        IWorldInfo iworldinfo = serverworld.getLevelData();
        SkinRestorer.server.getPlayerList().broadcastAll(new SPlayerListItemPacket(SPlayerListItemPacket.Action.REMOVE_PLAYER, player));
        SkinRestorer.server.getPlayerList().broadcastAll(new SPlayerListItemPacket(SPlayerListItemPacket.Action.ADD_PLAYER, player));
        player.connection.send(new SRespawnPacket(serverworld.dimensionType(), serverworld.dimension(), BiomeManager.obfuscateSeed(serverworld.getSeed()), gameType, previousGameType, isDebug, isFlat, true));
        player.connection.teleport(x, y, z, yaw, pitch);
        SkinRestorer.server.getPlayerList().sendLevelInfo(player, serverworld);
        SkinRestorer.server.getPlayerList().sendAllPlayerInfo(player);
        player.connection.send(new SPlayerAbilitiesPacket(player.abilities));
        for (EffectInstance effect:player.getActiveEffects()) {
            player.connection.send(new SPlayEntityEffectPacket(player.getId(), effect));
        }

        player.connection.send(new SServerDifficultyPacket(iworldinfo.getDifficulty(), iworldinfo.isDifficultyLocked()));
        SkinRestorer.server.getPlayerList().sendPlayerPermissionLevel(player);
        serverworld.getChunkSource().removeEntity(player);
        serverworld.getChunkSource().addEntity(player);
    }
}
