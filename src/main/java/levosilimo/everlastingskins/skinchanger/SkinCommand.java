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
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.I18nUtils;
import levosilimo.everlastingskins.util.SRHelpers;
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
import java.util.UUID;
import java.util.concurrent.*;


public class SkinCommand {

    private static final ScheduledExecutorService skinCommandExecutor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    private static final String FEEDBACK_PREFIX = "§6[EverlastingSkins]§f";

    private static I18nUtils i18nUtils;

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

    private record SkinActionParameters(
            Collection<ServerPlayer> targets,
            SkinActionType type,
            boolean setByOperator,
            SkinVariant variant,
            boolean withCape,
            @Nullable String customSource
    ) {}

    private static String getLocalizedString(String key) {
        if(i18nUtils == null) i18nUtils = new I18nUtils();
        return i18nUtils.getLocalizedString(key, Config.LANGUAGE.get());
    }


    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> skinCommand = Commands.literal("skin")
                .then(buildSetSubcommand())
                .then(buildSourceSubcommand())
                .then(buildClearSubcommand());
        dispatcher.register(skinCommand);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSourceSubcommand() {
        return Commands.literal("source")
                .executes(context -> sourceAction(context, context.getSource().getPlayer()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> sourceAction(context, EntityArgument.getPlayer(context, "target")))
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildClearSubcommand() {
        return Commands.literal("clear")
                .executes(context -> skinAction(context, new SkinActionParameters(
                        Collections.singleton(context.getSource().getPlayer()),
                        SkinActionType.clear, context.getSource().hasPermission(3), SkinVariant.ALL, false, null
                )))
                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                        .executes(context -> skinAction(context, new SkinActionParameters(
                                EntityArgument.getPlayers(context, "targets"),
                                SkinActionType.clear, context.getSource().hasPermission(3), SkinVariant.ALL, false, null
                        )))
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSetSubcommand() {
        return Commands.literal("set")
                // /skin set mojang
                .then(Commands.literal("mojang")
                        .then(Commands.argument("skin_name", StringArgumentType.word())
                                .executes(context -> skinAction(context, new SkinActionParameters(
                                        Collections.singleton(context.getSource().getPlayer()),
                                        SkinActionType.username, context.getSource().hasPermission(3), SkinVariant.ALL, false,
                                        StringArgumentType.getString(context, "skin_name")
                                )))
                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                        .executes(context -> skinAction(context, new SkinActionParameters(
                                                EntityArgument.getPlayers(context, "targets"),
                                                SkinActionType.username, context.getSource().hasPermission(3), SkinVariant.ALL, false,
                                                StringArgumentType.getString(context, "skin_name")
                                        )))
                                )
                        )
                )
                // /skin set web
                .then(Commands.literal("web")
                        .then(Commands.literal("classic")
                                .then(Commands.argument("url", StringArgumentType.string())
                                        .executes(context -> skinAction(context, new SkinActionParameters(
                                                Collections.singleton(context.getSource().getPlayer()),
                                                SkinActionType.url, context.getSource().hasPermission(3), SkinVariant.CLASSIC, false,
                                                StringArgumentType.getString(context, "url")
                                        )))
                                        .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                .executes(context -> skinAction(context, new SkinActionParameters(
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.url, context.getSource().hasPermission(3), SkinVariant.CLASSIC, true,
                                                        StringArgumentType.getString(context, "url")
                                                )))
                                        )
                                )
                        )
                        .then(Commands.literal("slim")
                                .then(Commands.argument("url", StringArgumentType.string())
                                        .executes(context -> skinAction(context, new SkinActionParameters(
                                                Collections.singleton(context.getSource().getPlayer()),
                                                SkinActionType.url, context.getSource().hasPermission(3), SkinVariant.SLIM, false,
                                                StringArgumentType.getString(context, "url")
                                        )))
                                        .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                .executes(context -> skinAction(context, new SkinActionParameters(
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.url, context.getSource().hasPermission(3), SkinVariant.SLIM, true,
                                                        StringArgumentType.getString(context, "url")
                                                )))
                                        )
                                )

                        )
                )
                // /skin set random
                .then(Commands.literal("random")
                        .executes(context -> skinAction(context, new SkinActionParameters(
                                Collections.singleton(context.getSource().getPlayer()),
                                SkinActionType.random, context.getSource().hasPermission(3), SkinVariant.ALL, false, null
                        )))
                        .then(Commands.argument("cape", BoolArgumentType.bool())
                                .executes(context -> skinAction(context, new SkinActionParameters(
                                        Collections.singleton(context.getSource().getPlayer()),
                                        SkinActionType.random, context.getSource().hasPermission(3), SkinVariant.ALL, BoolArgumentType.getBool(context, "cape"), null
                                )))
                                .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                        .executes(context -> skinAction(context, new SkinActionParameters(
                                                Collections.singleton(context.getSource().getPlayer()),
                                                SkinActionType.random, context.getSource().hasPermission(3), context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                        )))
                                )
                        )
                        .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                .executes(context -> skinAction(context, new SkinActionParameters(
                                        Collections.singleton(context.getSource().getPlayer()),
                                        SkinActionType.random, context.getSource().hasPermission(3), context.getArgument("skin variant", SkinVariant.class), false, null
                                )))
                                .then(Commands.argument("cape", BoolArgumentType.bool())
                                        .executes(context -> skinAction(context, new SkinActionParameters(
                                                Collections.singleton(context.getSource().getPlayer()),
                                                SkinActionType.random, context.getSource().hasPermission(3), context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                        )))
                                )
                        )
                        .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                .executes(context -> skinAction(context, new SkinActionParameters(
                                        EntityArgument.getPlayers(context, "targets"),
                                        SkinActionType.random, context.getSource().hasPermission(3), SkinVariant.ALL, false, null
                                )))
                                .then(Commands.argument("cape", BoolArgumentType.bool())
                                        .executes(context -> skinAction(context, new SkinActionParameters(
                                                EntityArgument.getPlayers(context, "targets"),
                                                SkinActionType.random, context.getSource().hasPermission(3), SkinVariant.ALL, BoolArgumentType.getBool(context, "cape"), null
                                        )))
                                        .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                                .executes(context -> skinAction(context, new SkinActionParameters(
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.random, context.getSource().hasPermission(3), context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                                )))
                                        )
                                )
                                .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                        .executes(context -> skinAction(context, new SkinActionParameters(
                                                EntityArgument.getPlayers(context, "targets"),
                                                SkinActionType.random, context.getSource().hasPermission(3), context.getArgument("skin variant", SkinVariant.class), false, null
                                        )))
                                        .then(Commands.argument("cape", BoolArgumentType.bool())
                                                .executes(context -> skinAction(context, new SkinActionParameters(
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.random, context.getSource().hasPermission(3), context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
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
            : Component.literal(FEEDBACK_PREFIX + " No source available");
        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static int skinAction(CommandContext<CommandSourceStack> context, SkinActionParameters params) {
        Collection<ServerPlayer> targets = params.targets();
        SkinActionType type = params.type();
        SkinVariant variant = params.variant();
        boolean withCape = params.withCape();
        String customSource = params.customSource();

        targets.forEach(player -> {
                if(Config.TOGGLE.get()) {
                    if(player == context.getSource().getEntity()) context.getSource().sendSuccess(() -> Component.literal(FEEDBACK_PREFIX + " " + getLocalizedString("change")), false);
                    else player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + getLocalizedString("change")));
                }
        });

        CompletableFuture<CustomSkinProperty> skinPropertyCompletableFuture = CompletableFuture.supplyAsync(() -> {
            CustomSkinProperty skinProperty = null;
            switch (type) {
                case clear: {
                    ServerPlayer first = targets.stream().findFirst().get();
                    String storedSrc = SkinRestorer.getSkinStorage().getSource(first.getUUID());
                    String pName = first.getGameProfile().getName();
                    MojangRestoreResult restore = tryRestoreFromMojang(getMojangAPI(), storedSrc, pName);
                    skinProperty = restore != null ? restore.skin : null;
                    break;
                }
                case url: {
                    String sanitized = SRHelpers.sanitizeSkinInput(customSource);
                    if (!sanitized.equals(customSource)) {
                        /* NameMC profile URL resolved to username — route to Mojang API */
                        skinProperty = getMojangAPI().getSkin(sanitized)
                                .map(MojangSkinDataResult::skinProperty).orElse(null);
                    } else {
                        skinProperty = getMineSkinAPI().genSkin(customSource, variant).property();
                    }
                    break;
                }
                case username:
                    skinProperty = getMojangAPI().getSkin(customSource)
                            .map(MojangSkinDataResult::skinProperty).orElse(null);
                    break;
                case random:
                    try {
                        skinProperty = getMojangAPI().getSkin(Objects.requireNonNull(RandomMojangSkin.randomUsername(withCape, variant)))
                                .map(MojangSkinDataResult::skinProperty).orElse(null);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                    break;
                case NEW:
                    try {
                        skinProperty = getMojangAPI().getSkin(Objects.requireNonNull(RandomMojangSkin.newUsername(variant)))
                                .map(MojangSkinDataResult::skinProperty).orElse(null);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                    break;
            }
            return skinProperty;
        }, skinCommandExecutor).whenComplete((skinProperty, throwable) -> {
            if (throwable != null) {
                EverlastingSkins.logger.error("Skin process error occurred");
                for (ServerPlayer player : targets) player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + getLocalizedString("error")));
                return;
            }
            boolean isClear = type == SkinActionType.clear;
            if (skinProperty == null && !isClear) {
                String reason = deriveReason(type, customSource);
                EverlastingSkins.logger.warn("Skin provider returned no result: {}", reason);
                for (ServerPlayer player : targets) player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + reason));
                return;
            }
            if (isClear && skinProperty == null) {
                EverlastingSkins.logger.info("Skin cleared for player(s) — no Mojang profile found");
                for (ServerPlayer player : targets) {
                    SkinRestorer.getSkinStorage().setSkin(player.getUUID(), null);
                    if (Config.TOGGLE.get()) {
                        player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " Skin cleared (no Mojang profile found)"));
                    }
                }
                for (ServerPlayer player : targets) {
                    SkinRestorer.server.execute(() -> task(player));
                }
                return;
            }
            boolean isRestore = isClear && skinProperty != null;
            for (ServerPlayer player : targets) {
                SkinRestorer.getSkinStorage().setSkin(player.getUUID(), skinProperty);
                if (Config.TOGGLE.get()) {
                    String msg = isRestore
                        ? "Skin restored from " + skinProperty.getSource()
                        : getLocalizedString("fulfilled");
                    if (player == context.getSource().getEntity()) {
                        context.getSource().sendSuccess(() -> Component.literal(FEEDBACK_PREFIX + " " + msg), false);
                    } else {
                        player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + msg));
                    }
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
        SkinRestorer.getSkinStorage().saveSkin(player.getUUID());
        player.getGameProfile().getProperties().removeAll("textures");
        player.getGameProfile().getProperties().put("textures", SkinRestorer.getSkinStorage().getSkin(player.getUUID()).getOriginalProperty());

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

    @Nullable
    static MojangRestoreResult tryRestoreFromMojang(MojangAPI mojangAPI, @Nullable String storedSource, String playerName) {
        String licensedUsername = (storedSource != null && !storedSource.trim().isEmpty())
            ? storedSource : playerName;
        CustomSkinProperty skin = mojangAPI.getSkin(licensedUsername)
            .map(MojangSkinDataResult::skinProperty)
            .filter(s -> !s.isEmpty())
            .orElse(null);
        if (skin == null) return null;
        return new MojangRestoreResult(skin, licensedUsername);
    }

    static class MojangRestoreResult {
        final CustomSkinProperty skin;
        final String licensedUsername;

        MojangRestoreResult(CustomSkinProperty skin, String licensedUsername) {
            this.skin = skin;
            this.licensedUsername = licensedUsername;
        }
    }

    private static String deriveReason(SkinActionType type, @Nullable String customSource) {
        switch (type) {
            case username:
                return customSource != null
                    ? "No skin found for \"" + customSource + "\""
                    : "No skin found";
            case url: {
                if (customSource != null) {
                    String sanitized = SRHelpers.sanitizeSkinInput(customSource);
                    if (!sanitized.equals(customSource)) {
                        return "No skin found for \"" + sanitized + "\"";
                    }
                }
                return "MineSkin rejected the URL";
            }
            case random:
            case NEW:
                return "No random username available";
            default:
                return "Provider returned no result";
        }
    }
}
