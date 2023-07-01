package levosilimo.everlastingskins.skinchanger;

import com.google.common.hash.Hashing;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.enums.LanguageEnum;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.enums.SkinVariant;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static levosilimo.everlastingskins.EverlastingSkins.skinCommandExecutor;


public class SkinCommand {
    private static String processing = "";
    private static String changeOP = "";
    private static String recon_needed = "";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("skin")
                .then(Commands.literal("set")
                        .then(Commands.literal("mojang")
                                .then(Commands.argument("nickname", StringArgumentType.word())
                                        .executes(context ->
                                                skinAction(Collections.singleton(context.getSource().getPlayerOrException()),SkinActionType.nickname,false, SkinVariant.all,false,
                                                        StringArgumentType.getString(context, "nickname"))))
                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                        .then(Commands.argument("nickname", StringArgumentType.word())
                                                .executes(context ->
                                                        skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.nickname, true,SkinVariant.all,false,
                                                                StringArgumentType.getString(context, "nickname"))))))
                        .then(Commands.literal("web")
                                .then(Commands.literal("classic")
                                        .then(Commands.argument("url", StringArgumentType.string())
                                                .executes(context ->
                                                        skinAction(Collections.singleton(context.getSource().getPlayerOrException()),SkinActionType.url, false, SkinVariant.classic, false,
                                                                StringArgumentType.getString(context, "url")))
                                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                        .executes(context ->
                                                                skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.url,true,SkinVariant.classic, true,
                                                                        StringArgumentType.getString(context, "url"))))))
                                .then(Commands.literal("slim")
                                        .then(Commands.argument("url", StringArgumentType.string())
                                                .executes(context ->
                                                        skinAction(Collections.singleton(context.getSource().getPlayerOrException()),SkinActionType.url, false, SkinVariant.slim, false,
                                                                StringArgumentType.getString(context, "url")))
                                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                        .executes(context ->
                                                                skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.url,true,SkinVariant.slim, true,
                                                                        StringArgumentType.getString(context, "url")))))))
                        .then(Commands.literal("random")
                                .executes(context -> skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.random,false,SkinVariant.all,false,null))
                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                        .executes(context ->
                                                skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.random,true,SkinVariant.all, false,
                                                        null)))
                                .then(Commands.literal("classic")
                                        .then(Commands.literal("cape")
                                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                        .executes(context ->
                                                                skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.random,true,SkinVariant.classic, true,
                                                                        null)))
                                                .executes(context -> skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.random,false,SkinVariant.classic,true,null)))
                                        .then(Commands.literal("new")
                                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                        .executes(context ->
                                                                skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.NEW,true,SkinVariant.classic, false,
                                                                        null)))
                                                .executes(context -> skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.NEW,false,SkinVariant.classic,false,null)))
                                        .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                .executes(context ->
                                                        skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.random,true,SkinVariant.classic, false,
                                                                null)))
                                        .executes(context -> skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.random,false,SkinVariant.classic,false,null)))
                                .then(Commands.literal("slim")
                                        .then(Commands.literal("cape")
                                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                        .executes(context ->
                                                                skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.random,true,SkinVariant.slim, true,
                                                                        null)))
                                                .executes(context -> skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.random,false,SkinVariant.slim,true,null)))
                                        .then(Commands.literal("new")
                                                .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                        .executes(context ->
                                                                skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.NEW,true,SkinVariant.slim, false,
                                                                        null)))
                                                .executes(context -> skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.NEW,false,SkinVariant.slim,false,null)))
                                        .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                .executes(context ->
                                                        skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.random,true,SkinVariant.slim, false,
                                                                null)))
                                        .executes(context -> skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.random,false,SkinVariant.slim,false,null)))
                        ))
                .then(Commands.literal("source")
                        .then(Commands.argument("target", EntityArgument.player()).executes(context ->
                                SkinRestorer.getSkinIO().getSource(EntityArgument.getPlayer(context, "target").getUUID())))
                        .executes(context ->
                                SkinRestorer.getSkinIO().getSource(context.getSource().getPlayerOrException().getUUID())))
                .then(Commands.literal("clear")
                        .then(Commands.argument("targets", EntityArgument.players()).executes(context ->
                                skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.clear, true,SkinVariant.all, false,null)))
                        .executes(context ->
                                skinAction(Collections.singleton(context.getSource().getPlayerOrException()),SkinActionType.clear, false,SkinVariant.all, false,null))
                )
        );
    }

    private static int skinAction(Collection<ServerPlayer> targets, SkinActionType type, boolean setByOperator, SkinVariant variant, boolean withCape, @Nullable String customSource) {
        CompletableFuture<ArrayList<EmulateReconnectPacket>> future = CompletableFuture.supplyAsync(() -> {
            LanguageEnum a = Config.LANGUAGE.get();
            switch (a) {
                case Russian -> {
                    processing = "§6[EverlastingSkins]§f Обрабатываем...";
                    changeOP = "§6[EverlastingSkins]§f Оператор изменил ваш скин.";
                    recon_needed = "§6[EverlastingSkins]§f Скин применён.";
                }
                case Ukrainian -> {
                    processing = "§6[EverlastingSkins]§f Опрацьовуємо...";
                    changeOP = "§6[EverlastingSkins]§f Оператор змінив ваш скін.";
                    recon_needed = "§6[EverlastingSkins]§f Скін застосовано.";
                }
                default -> {
                    processing = "§6[EverlastingSkins]§f Processing...";
                    changeOP = "§6[EverlastingSkins]§f Operator changed your skin.";
                    recon_needed = "§6[EverlastingSkins]§f Skin has been applied.";
                }
            }
            if (!setByOperator)
                targets.stream().findFirst().get().sendSystemMessage(Component.literal(processing));

            Property skin = null;
            String source= "";
            ArrayList<EmulateReconnectPacket> packets = new ArrayList<>();
            if(customSource!=null) source = customSource;
            switch (type) {
                case clear ->
                        skin = MojangSkinProvider.getSkin(targets.stream().findFirst().get().getGameProfile().getName());
                case url -> skin = MineskinSkinProvider.getSkin(customSource, variant);
                case nickname -> skin = MojangSkinProvider.getSkin(customSource);
                case random -> {
                    source = RandomMojangSkin.randomNickname(withCape, variant, false);
                    skin = MojangSkinProvider.getSkin(source);
                }
                case NEW -> {
                    source = RandomMojangSkin.randomNickname(false, variant, true);
                    skin = MojangSkinProvider.getSkin(source);
                }
            }

            for (ServerPlayer player : targets) {
                if(!source.isEmpty())SkinStorage.sourceMap.put(player.getUUID(),source);
                else SkinStorage.sourceMap.put(player.getUUID(),player.getGameProfile().getName());
                SkinRestorer.getSkinStorage().setSkin(player.getUUID(), skin);
                if (setByOperator)
                    player.sendSystemMessage(Component.literal(changeOP));
                else
                    player.sendSystemMessage(Component.literal(recon_needed));
            }
            for (ServerPlayer player:targets) {
                ServerLevel world = player.getLevel();
                packets.add(generatePacket(player, world));
            }
            return packets;
        }, skinCommandExecutor).orTimeout(10, TimeUnit.SECONDS).whenComplete((result, exception) -> {
            if(SkinRestorer.server != null) SkinRestorer.server.execute(() -> result.forEach(EmulateReconnectPacket::emulateReconnect));
        });
        return targets.size();
    }

    private static EmulateReconnectPacket generatePacket(ServerPlayer player, ServerLevel world){

        //Respawn packet info
        ResourceKey<DimensionType> dimensionType=player.getLevel().getLevel().dimensionTypeId();
        ResourceKey<Level> registryKey = player.getLevel().dimension();
        long seedEncrypted = Hashing.sha256().hashString(String.valueOf(player.getLevel().getSeed()), StandardCharsets.UTF_8).asLong();
        GameType gameType = player.gameMode.getGameModeForPlayer();
        GameType previousGameType = player.gameMode.getPreviousGameModeForPlayer();
        boolean isDebug = player.getLevel().isDebug();
        boolean isFlat = player.getLevel().isFlat();
        //Skin change
        SkinRestorer.getSkinStorage().removeSkin(player.getUUID());
        player.getGameProfile().getProperties().removeAll("textures");
        player.getGameProfile().getProperties().put("textures", SkinRestorer.getSkinStorage().getSkin(player.getUUID()));

        //Misc
        int HeldSlot = player.getInventory().selected;
        Abilities abilities = player.getAbilities();

        //Position and rotation packet info
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getXRot();
        float pitch = player.getYRot();
        float headYaw = player.getYHeadRot();
        int yawPacket = Mth.floor(player.getYHeadRot() * 256.0F / 360.0F);
        Set<ClientboundPlayerPositionPacket.RelativeArgument> flags = new HashSet<>();
        return new EmulateReconnectPacket(player, world, x, y, z, yaw, pitch, headYaw, (byte) yawPacket, flags, HeldSlot, abilities, dimensionType, registryKey, seedEncrypted, gameType, previousGameType, isDebug, isFlat);
    }
}
