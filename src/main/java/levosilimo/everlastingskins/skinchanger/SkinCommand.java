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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelData;
import net.minecraftforge.server.command.EnumArgument;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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


    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // Command: /skin source
        LiteralArgumentBuilder<CommandSourceStack> skinSourceLiteral = Commands.literal("source")
                .executes(context -> sourceAction(context, context.getSource().getPlayer()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> sourceAction(context, EntityArgument.getPlayer(context, "target")))
                );

        // Command: /skin clear
        LiteralArgumentBuilder<CommandSourceStack> skinClearLiteral = Commands.literal("clear")
                .executes(context -> skinAction(context,
                        Collections.singleton(context.getSource().getPlayer()),
                        SkinActionType.clear, context.getSource().hasPermission(3), SkinVariant.ALL, false, null
                ))
                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                        .executes(context -> skinAction(context,
                                EntityArgument.getPlayers(context, "targets"),
                                SkinActionType.clear, context.getSource().hasPermission(3), SkinVariant.ALL, false, null
                        ))
                );

        // Command: /skin set
        LiteralArgumentBuilder<CommandSourceStack> skinSetLiteral = Commands.literal("set")
                // Command: /skin set mojang
                .then(Commands.literal("mojang")
                        .then(Commands.argument("skin_name", StringArgumentType.word())
                                .executes(context -> skinAction(context,
                                        Collections.singleton(context.getSource().getPlayer()),
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
                                                Collections.singleton(context.getSource().getPlayer()),
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
                                                Collections.singleton(context.getSource().getPlayer()),
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
                                Collections.singleton(context.getSource().getPlayer()),
                                SkinActionType.random, context.getSource().hasPermission(3), SkinVariant.ALL, false, null
                        ))
                        .then(Commands.argument("cape", BoolArgumentType.bool())
                                .executes(context -> skinAction(context,
                                        Collections.singleton(context.getSource().getPlayer()),
                                        SkinActionType.random, context.getSource().hasPermission(3), SkinVariant.ALL, BoolArgumentType.getBool(context, "cape"), null
                                ))
                                .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                        .executes(context -> skinAction(context,
                                                Collections.singleton(context.getSource().getPlayer()),
                                                SkinActionType.random, context.getSource().hasPermission(3), context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                        ))
                                )
                        )
                        .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                .executes(context -> skinAction(context,
                                        Collections.singleton(context.getSource().getPlayer()),
                                        SkinActionType.random, context.getSource().hasPermission(3), context.getArgument("skin variant", SkinVariant.class), false, null
                                ))
                                .then(Commands.argument("cape", BoolArgumentType.bool())
                                        .executes(context -> skinAction(context,
                                                Collections.singleton(context.getSource().getPlayer()),
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

        LiteralArgumentBuilder<CommandSourceStack> skinCommand = Commands.literal("skin")
                .then(skinSetLiteral)
                .then(skinSourceLiteral)
                .then(skinClearLiteral);

        dispatcher.register(skinCommand);
    }

    private static int sourceAction(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        String source = SkinStorage.getInstance().getSource(target.getUUID());
        MutableComponent message;
        if(source == null) message = Component.literal(FEEDBACK_PREFIX + " " + getLocalizedString("no_source"));
        else message = Component.literal(FEEDBACK_PREFIX + " " + source);
        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static int skinAction(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets, SkinActionType type, boolean setByOperator, SkinVariant variant, boolean withCape, @Nullable String customSource) {
        targets.forEach(player -> {
                if(Config.TOGGLE.get()) {
                    if(player == context.getSource().getEntity()) context.getSource().sendSuccess(() -> Component.literal(FEEDBACK_PREFIX + " " + getLocalizedString("change")), false);
                    else player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + getLocalizedString("change")));
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
                for(ServerPlayer player: targets) player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + getLocalizedString("error")));
                return;
            }
            for (ServerPlayer player : targets) {
                SkinStorage.getInstance().setSkin(player, skinProperty);
                if(Config.TOGGLE.get()) {
                    if(player == context.getSource().getEntity()) context.getSource().sendSuccess(() -> Component.literal(FEEDBACK_PREFIX + " " + getLocalizedString("fulfilled")), false);
                    else player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + getLocalizedString("fulfilled_force")));
                }
            }
            for (ServerPlayer player : targets) {
                SkinRestorer.server.execute(() -> task(player));
            }
        });

        skinCommandExecutor.schedule(() -> {
            if(skinPropertyCompletableFuture.completeExceptionally(new TimeoutException("Skin fetch timeout occurred"))) {
                EverlastingSkins.logger.error(getLocalizedString("timeout"));
                for(ServerPlayer player: targets) player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + getLocalizedString("timeout")));
            }
        }, 10000, TimeUnit.MILLISECONDS);

        return targets.size();
    }

    private static void task(ServerPlayer player) {
        //Position and rotation packet info
        double x = player.position().x;
        double y = player.position().y;
        double z = player.position().z;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        ServerLevel serverLevel = player.serverLevel();
        LevelData levelData = serverLevel.getLevelData();
        PlayerList playerlist = player.server.getPlayerList();
        //Skin change
        SkinStorage.getInstance().saveSkin(player);
        player.getGameProfile().getProperties().removeAll("textures");
        player.getGameProfile().getProperties().put("textures", SkinStorage.getInstance().getSkin(player).getOriginalProperty());

        //Reconnect emulation

        SkinRestorer.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
        SkinRestorer.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, player));
        player.connection.send(new ClientboundRespawnPacket(player.createCommonSpawnInfo(serverLevel), (byte)3));
        player.absMoveTo(x,y,z,yaw,pitch);
        player.connection.send(new ClientboundPlayerPositionPacket(x,y,z,yaw,pitch,Collections.emptySet(), 0));
        playerlist.sendLevelInfo(player, serverLevel);
        playerlist.sendPlayerPermissionLevel(player);
        playerlist.sendAllPlayerInfo(player);
        playerlist.sendActivePlayerEffects(player);
        player.connection.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
        player.connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
        SkinRestorer.server.getPlayerList().sendPlayerPermissionLevel(player);

        serverLevel.getChunkSource().chunkMap.removeEntity(player);
        serverLevel.getChunkSource().chunkMap.addEntity(player);
    }
}
