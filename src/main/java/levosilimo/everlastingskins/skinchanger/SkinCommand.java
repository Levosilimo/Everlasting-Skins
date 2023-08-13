package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.enums.LanguageEnum;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.enums.SkinVariant;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static levosilimo.everlastingskins.EverlastingSkins.skinCommandExecutor;


public class SkinCommand {
    private static String processing = "";
    private static String changeOP = "";
    private static String recon_needed = "";

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("skin")
                .then(Commands.literal("set")
                        .then(Commands.literal("mojang")
                                .then(Commands.argument("nickname", StringArgumentType.word())
                                        .executes(context ->
                                                skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.nickname, false, SkinVariant.all, false,
                                                        StringArgumentType.getString(context, "nickname"))))
                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                        .then(Commands.argument("nickname", StringArgumentType.word())
                                                .executes(context ->
                                                        skinAction(EntityArgument.getPlayers(context, "targets"), SkinActionType.nickname, true, SkinVariant.all, false,
                                                                StringArgumentType.getString(context, "nickname"))))))
                        .then(Commands.literal("web")
                                .then(Commands.literal("classic")
                                        .then(Commands.argument("url", StringArgumentType.string())
                                                .executes(context ->
                                                        skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.url, false, SkinVariant.classic, false,
                                                                StringArgumentType.getString(context, "url")))
                                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                        .executes(context ->
                                                                skinAction(EntityArgument.getPlayers(context, "targets"), SkinActionType.url, true, SkinVariant.classic, true,
                                                                        StringArgumentType.getString(context, "url"))))))
                                .then(Commands.literal("slim")
                                        .then(Commands.argument("url", StringArgumentType.string())
                                                .executes(context ->
                                                        skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.url, false, SkinVariant.slim, false,
                                                                StringArgumentType.getString(context, "url")))
                                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                        .executes(context ->
                                                                skinAction(EntityArgument.getPlayers(context, "targets"), SkinActionType.url, true, SkinVariant.slim, true,
                                                                        StringArgumentType.getString(context, "url")))))))
                        .then(Commands.literal("random")
                                .executes(context -> skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.random, false, SkinVariant.all, false, null))
                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                        .executes(context ->
                                                skinAction(EntityArgument.getPlayers(context, "targets"), SkinActionType.random, true, SkinVariant.all, false,
                                                        null)))
                                .then(Commands.literal("classic")
                                        .then(Commands.literal("cape")
                                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                        .executes(context ->
                                                                skinAction(EntityArgument.getPlayers(context, "targets"), SkinActionType.random, true, SkinVariant.classic, true,
                                                                        null)))
                                                .executes(context -> skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.random, false, SkinVariant.classic, true, null)))
                                        .then(Commands.literal("new")
                                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                        .executes(context ->
                                                                skinAction(EntityArgument.getPlayers(context, "targets"), SkinActionType.NEW, true, SkinVariant.classic, false,
                                                                        null)))
                                                .executes(context -> skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.NEW, false, SkinVariant.classic, false, null)))
                                        .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                .executes(context ->
                                                        skinAction(EntityArgument.getPlayers(context, "targets"), SkinActionType.random, true, SkinVariant.classic, false,
                                                                null)))
                                        .executes(context -> skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.random, false, SkinVariant.classic, false, null)))
                                .then(Commands.literal("slim")
                                        .then(Commands.literal("cape")
                                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                        .executes(context ->
                                                                skinAction(EntityArgument.getPlayers(context, "targets"), SkinActionType.random, true, SkinVariant.slim, true,
                                                                        null)))
                                                .executes(context -> skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.random, false, SkinVariant.slim, true, null)))
                                        .then(Commands.literal("new")
                                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                        .executes(context ->
                                                                skinAction(EntityArgument.getPlayers(context, "targets"), SkinActionType.NEW, true, SkinVariant.slim, false,
                                                                        null)))
                                                .executes(context -> skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.NEW, false, SkinVariant.slim, false, null)))
                                        .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                .executes(context ->
                                                        skinAction(EntityArgument.getPlayers(context, "targets"), SkinActionType.random, true, SkinVariant.slim, false,
                                                                null)))
                                        .executes(context -> skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.random, false, SkinVariant.slim, false, null)))
                        ))
                .then(Commands.literal("source")
                        .then(Commands.argument("target", EntityArgument.player()).executes(context ->
                                SkinRestorer.getSkinIO().getSource(EntityArgument.getPlayer(context, "target").getUUID())))
                        .executes(context ->
                                SkinRestorer.getSkinIO().getSource(context.getSource().getPlayerOrException().getUUID())))
                .then(Commands.literal("clear")
                        .then(Commands.argument("targets", EntityArgument.players()).executes(context ->
                                skinAction(EntityArgument.getPlayers(context, "targets"), SkinActionType.clear, true, SkinVariant.all, false, null)))
                        .executes(context ->
                                skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.clear, false, SkinVariant.all, false, null))
                )
        );
    }

    private static int skinAction(Collection<ServerPlayerEntity> targets, SkinActionType type, boolean setByOperator, SkinVariant variant, boolean withCape, @Nullable String customSource) {
        CompletableFuture<ArrayList<EmulateReconnectHandler>> future = CompletableFuture.supplyAsync(() -> {
            LanguageEnum a = Config.LANGUAGE.get();
            switch (a) {
                case Russian:
                    processing = "§6[EverlastingSkins]§f Обрабатываем...";
                    changeOP = "§6[EverlastingSkins]§f Оператор изменил ваш скин.";
                    recon_needed = "§6[EverlastingSkins]§f Скин применён.";
                    break;
                case Ukrainian:
                    processing = "§6[EverlastingSkins]§f Опрацьовуємо...";
                    changeOP = "§6[EverlastingSkins]§f Оператор змінив ваш скін.";
                    recon_needed = "§6[EverlastingSkins]§f Скін застосовано.";
                    break;
                default:
                    processing = "§6[EverlastingSkins]§f Processing...";
                    changeOP = "§6[EverlastingSkins]§f Operator changed your skin.";
                    recon_needed = "§6[EverlastingSkins]§f Skin has been applied.";
                    break;
            }
            if (!setByOperator)
                targets.stream().findFirst().get().sendMessage(new StringTextComponent(processing));

            Property skin = null;
            String source = "";
            ArrayList<EmulateReconnectHandler> handlers = new ArrayList<>();
            if (customSource != null) source = customSource;
            switch (type) {
                case clear:
                    skin = MojangSkinProvider.getSkin(targets.stream().findFirst().get().getGameProfile().getName());
                    break;
                case url:
                    skin = MineskinSkinProvider.getSkin(customSource, variant);
                    break;
                case nickname:
                    skin = MojangSkinProvider.getSkin(customSource);
                    break;
                case random:
                    source = RandomMojangSkin.randomNickname(withCape, variant, false);
                    skin = MojangSkinProvider.getSkin(source);
                    break;
                case NEW:
                    source = RandomMojangSkin.randomNickname(false, variant, true);
                    skin = MojangSkinProvider.getSkin(source);
                    break;
            }

            for (ServerPlayerEntity player : targets) {
                if (!source.isEmpty()) SkinStorage.sourceMap.put(player.getUUID(), source);
                else SkinStorage.sourceMap.put(player.getUUID(), player.getGameProfile().getName());
                SkinRestorer.getSkinStorage().setSkin(player.getUUID(), skin);
                if (setByOperator)
                    player.sendMessage(new StringTextComponent(changeOP));
                else
                    player.sendMessage(new StringTextComponent(recon_needed));
            }
            for (ServerPlayerEntity player : targets) handlers.add(new EmulateReconnectHandler(player));
            return handlers;
        }, skinCommandExecutor);
        try {
            ArrayList<EmulateReconnectHandler> result = future.get(10, TimeUnit.SECONDS);
            result.forEach((handler) -> SkinRestorer.server.execute(handler::emulateReconnect));
        } catch (TimeoutException e) {
            targets.forEach((target) -> target.sendMessage(new StringTextComponent("§6[EverlastingSkins]§f Skin was not set.")));
        } catch (Exception e) {
            targets.forEach((target) -> target.sendMessage(new StringTextComponent("§6[EverlastingSkins]§f Unexpected error.")));
            e.printStackTrace();
        }

        return targets.size();
    }
}
