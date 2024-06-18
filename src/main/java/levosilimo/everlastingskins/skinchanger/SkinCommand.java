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
import org.apache.commons.lang3.ObjectUtils;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.*;

import static net.minecraft.command.Commands.argument;
import static net.minecraft.command.Commands.literal;

public class SkinCommand {

    private static final ScheduledExecutorService skinCommandExecutor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    private static final String FEEDBACK_PREFIX = "§6[EverlastingSkins]§f";

    private static I18nUtils i18nUtils;

    private static String getLocalizedString(String key) {
        if(i18nUtils == null) i18nUtils = new I18nUtils();
        return i18nUtils.getLocalizedString(key, Config.LANGUAGE.get());
    }


    public static void register(CommandDispatcher<CommandSource> dispatcher) {

        // Command: /skin source
        LiteralArgumentBuilder<CommandSource> skinSourceLiteral = literal("source")
                .executes(context -> sourceAction(context, context.getSource().asPlayer()))
                .then(argument("target", EntityArgument.player())
                        .executes(context -> sourceAction(context, EntityArgument.getPlayer(context, "target")))
                );

        // Command: /skin clear
        LiteralArgumentBuilder<CommandSource> skinClearLiteral = literal("clear")
                .executes(context -> skinAction(context,
                        Collections.singleton(context.getSource().asPlayer()),
                        SkinActionType.clear, context.getSource().hasPermissionLevel(3), SkinVariant.all, false, null
                ))
                .then(argument("targets", EntityArgument.players()).requires(source -> source.hasPermissionLevel(3))
                        .executes(context -> skinAction(context,
                                EntityArgument.getPlayers(context, "targets"),
                                SkinActionType.clear, context.getSource().hasPermissionLevel(3), SkinVariant.all, false, null
                        ))
                );

        // Command: /skin set
        LiteralArgumentBuilder<CommandSource> skinSetLiteral = literal("set")
                // Command: /skin set mojang
                .then(literal("mojang")
                        .then(argument("skin_name", StringArgumentType.word())
                                .executes(context -> skinAction(context,
                                        Collections.singleton(context.getSource().asPlayer()),
                                        SkinActionType.username, context.getSource().hasPermissionLevel(3), SkinVariant.all, false,
                                        StringArgumentType.getString(context, "skin_name")
                                ))
                                .then(argument("targets", EntityArgument.players()).requires(source -> source.hasPermissionLevel(3))
                                        .executes(context -> skinAction(context,
                                                EntityArgument.getPlayers(context, "targets"),
                                                SkinActionType.username, context.getSource().hasPermissionLevel(3), SkinVariant.all, false,
                                                StringArgumentType.getString(context, "skin_name")
                                        ))
                                )
                        )
                )
                // Command: /skin set web
                .then(literal("web")
                        .then(literal("classic")
                                .then(argument("url", StringArgumentType.string())
                                        .executes(context -> skinAction(context,
                                                Collections.singleton(context.getSource().asPlayer()),
                                                SkinActionType.url, context.getSource().hasPermissionLevel(3), SkinVariant.classic, false,
                                                StringArgumentType.getString(context, "url")
                                        ))
                                        .then(argument("targets", EntityArgument.players()).requires(source -> source.hasPermissionLevel(3))
                                                .executes(context -> skinAction(context,
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.url, context.getSource().hasPermissionLevel(3), SkinVariant.classic, true,
                                                        StringArgumentType.getString(context, "url")
                                                ))
                                        )
                                )
                        )
                        .then(literal("slim")
                                .then(argument("url", StringArgumentType.string())
                                        .executes(context -> skinAction(context,
                                                Collections.singleton(context.getSource().asPlayer()),
                                                SkinActionType.url, context.getSource().hasPermissionLevel(3), SkinVariant.slim, false,
                                                StringArgumentType.getString(context, "url")
                                        ))
                                        .then(argument("targets", EntityArgument.players()).requires(source -> source.hasPermissionLevel(3))
                                                .executes(context -> skinAction(context,
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.url, context.getSource().hasPermissionLevel(3), SkinVariant.slim, true,
                                                        StringArgumentType.getString(context, "url")
                                                ))
                                        )
                                )

                        )
                )
                // Command: /skin set random
                .then(literal("random")
                        .executes(context -> skinAction(context,
                                Collections.singleton(context.getSource().asPlayer()),
                                SkinActionType.random, context.getSource().hasPermissionLevel(3), SkinVariant.all, false, null
                        ))
                        .then(argument("cape", BoolArgumentType.bool())
                                .executes(context -> skinAction(context,
                                        Collections.singleton(context.getSource().asPlayer()),
                                        SkinActionType.random, context.getSource().hasPermissionLevel(3), SkinVariant.all, BoolArgumentType.getBool(context, "cape"), null
                                ))
                                .then(argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                        .executes(context -> skinAction(context,
                                                Collections.singleton(context.getSource().asPlayer()),
                                                SkinActionType.random, context.getSource().hasPermissionLevel(3), context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                        ))
                                )
                        )
                        .then(argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                .executes(context -> skinAction(context,
                                        Collections.singleton(context.getSource().asPlayer()),
                                        SkinActionType.random, context.getSource().hasPermissionLevel(3), context.getArgument("skin variant", SkinVariant.class), false, null
                                ))
                                .then(argument("cape", BoolArgumentType.bool())
                                        .executes(context -> skinAction(context,
                                                Collections.singleton(context.getSource().asPlayer()),
                                                SkinActionType.random, context.getSource().hasPermissionLevel(3), context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                        ))
                                )
                        )
                        .then(argument("targets", EntityArgument.players()).requires(source -> source.hasPermissionLevel(3))
                                .executes(context -> skinAction(context,
                                        EntityArgument.getPlayers(context, "targets"),
                                        SkinActionType.random, context.getSource().hasPermissionLevel(3), SkinVariant.all, false, null
                                ))
                                .then(argument("cape", BoolArgumentType.bool())
                                        .executes(context -> skinAction(context,
                                                EntityArgument.getPlayers(context, "targets"),
                                                SkinActionType.random, context.getSource().hasPermissionLevel(3), SkinVariant.all, BoolArgumentType.getBool(context, "cape"), null
                                        ))
                                        .then(argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                                .executes(context -> skinAction(context,
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.random, context.getSource().hasPermissionLevel(3), context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                                ))
                                        )
                                )
                                .then(argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                        .executes(context -> skinAction(context,
                                                EntityArgument.getPlayers(context, "targets"),
                                                SkinActionType.random, context.getSource().hasPermissionLevel(3), context.getArgument("skin variant", SkinVariant.class), false, null
                                        ))
                                        .then(argument("cape", BoolArgumentType.bool())
                                                .executes(context -> skinAction(context,
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.random, context.getSource().hasPermissionLevel(3), context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                                ))
                                        )
                                )
                        )
                );

        LiteralArgumentBuilder<CommandSource> skinCommand = literal("skin")
                .then(skinSetLiteral)
                .then(skinSourceLiteral)
                .then(skinClearLiteral);

        dispatcher.register(skinCommand);
    }

    private static int sourceAction(CommandContext<CommandSource> context, ServerPlayerEntity target) {
        String source = SkinStorage.getInstance().getSource(target.getUniqueID());
        StringTextComponent message;
        if(source == null) message = new StringTextComponent(FEEDBACK_PREFIX + " " + getLocalizedString("no_source"));
        else message = new StringTextComponent(FEEDBACK_PREFIX + " " + source);
        context.getSource().sendFeedback(message, false);
        return 1;
    }

    private static int skinAction(CommandContext<CommandSource> context, Collection<ServerPlayerEntity> targets, SkinActionType type, boolean setByOperator, SkinVariant variant, boolean withCape, @Nullable String customSource) {
        targets.forEach(player -> {
                if(Config.TOGGLE.get()) {
                    if(player == context.getSource().getEntity()) context.getSource().sendFeedback(new StringTextComponent(FEEDBACK_PREFIX + " " + getLocalizedString("change")), false);
                    else player.sendMessage(new StringTextComponent(FEEDBACK_PREFIX + " " + getLocalizedString("change")), Util.DUMMY_UUID);
                }
        });

        CompletableFuture<CustomSkinProperty> skinPropertyCompletableFuture = CompletableFuture.supplyAsync(() -> {
            CustomSkinProperty skinProperty = null;
            switch (type) {
                case clear:
                    skinProperty = MojangSkinProvider.getSkin(targets.stream().findFirst().get().getGameProfile().getName());
                    break;
                case url:
                    skinProperty = MineskinSkinProvider.getSkin(customSource, variant);
                    break;
                case username:
                    skinProperty = MojangSkinProvider.getSkin(customSource);
                    break;
                case random:
                    try {
                        skinProperty = MojangSkinProvider.getSkin(Objects.requireNonNull(RandomMojangSkin.randomUsername(withCape, variant)));
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                    break;
                case NEW:
                    try {
                        skinProperty = MojangSkinProvider.getSkin(Objects.requireNonNull(RandomMojangSkin.newUsername(variant)));
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                    break;
            }
            return skinProperty;
        }, skinCommandExecutor).whenComplete((skinProperty, throwable) -> {
            if(throwable != null || skinProperty == null) {
                EverlastingSkins.logger.error("Skin process error occurred");
                for(ServerPlayerEntity player: targets) player.sendMessage(new StringTextComponent(FEEDBACK_PREFIX + " " + getLocalizedString("error")), Util.DUMMY_UUID);
                return;
            }
            for (ServerPlayerEntity player : targets) {
                SkinStorage.getInstance().setSkin(player, skinProperty);
                if(Config.TOGGLE.get()) {
                    if(player == context.getSource().getEntity()) context.getSource().sendFeedback(new StringTextComponent(FEEDBACK_PREFIX + " " + getLocalizedString("fulfilled")), false);
                    else player.sendMessage(new StringTextComponent(FEEDBACK_PREFIX + " " + getLocalizedString("fulfilled_force")), Util.DUMMY_UUID);
                }
            }
            for (ServerPlayerEntity player : targets) {
                SkinRestorer.server.execute(() -> task(player));
            }
        });

        skinCommandExecutor.schedule(() -> {
            if(skinPropertyCompletableFuture.completeExceptionally(new TimeoutException("Skin fetch timeout occurred"))) {
                EverlastingSkins.logger.error(getLocalizedString("timeout"));
                for(ServerPlayerEntity player: targets) player.sendMessage(new StringTextComponent(FEEDBACK_PREFIX + " " + getLocalizedString("timeout")), Util.DUMMY_UUID);
            }
        }, 10000, TimeUnit.MILLISECONDS);

        return targets.size();
    }

    private static void task(ServerPlayerEntity player) {
        //Position and rotation packet info
        double x = player.getPosX();
        double y = player.getPosY();
        double z = player.getPosZ();
        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;

        GameType gameType = player.interactionManager.getGameType();
        GameType previousGameType = player.interactionManager.func_241815_c_();
        boolean isDebug = player.getServerWorld().isDebug();
        boolean isFlat = player.getServerWorld().func_241109_A_();
        //Skin change
        SkinStorage.getInstance().saveSkin(player);
        player.getGameProfile().getProperties().removeAll("textures");
        player.getGameProfile().getProperties().put("textures", SkinStorage.getInstance().getSkin(player));

        //Reconnect emulation
        ServerWorld serverworld = player.getServerWorld();
        IWorldInfo iworldinfo = serverworld.getWorldInfo();
        SkinRestorer.server.getPlayerList().sendPacketToAllPlayers(new SPlayerListItemPacket(SPlayerListItemPacket.Action.REMOVE_PLAYER, player));
        SkinRestorer.server.getPlayerList().sendPacketToAllPlayers(new SPlayerListItemPacket(SPlayerListItemPacket.Action.ADD_PLAYER, player));
        player.connection.sendPacket(new SRespawnPacket(serverworld.getDimensionType(), serverworld.getDimensionKey(), BiomeManager.getHashedSeed(serverworld.getSeed()), gameType, previousGameType, isDebug, isFlat, true));
        player.connection.setPlayerLocation(x,y,z,yaw,pitch);
        SkinRestorer.server.getPlayerList().sendWorldInfo(player, serverworld);
        SkinRestorer.server.getPlayerList().sendInventory(player);
        player.connection.sendPacket(new SPlayerAbilitiesPacket());
        player.connection.sendPacket(new SPlayerAbilitiesPacket(player.abilities));
        for(EffectInstance effectinstance : player.getActivePotionEffects()) {
            player.connection.sendPacket(new SPlayEntityEffectPacket(player.getEntityId(), effectinstance));
        }
        player.connection.sendPacket(new SServerDifficultyPacket(iworldinfo.getDifficulty(), iworldinfo.isDifficultyLocked()));
        SkinRestorer.server.getPlayerList().updatePermissionLevel(player);
        serverworld.getChunkProvider().untrack(player);
        serverworld.getChunkProvider().track(player);
    }
}
