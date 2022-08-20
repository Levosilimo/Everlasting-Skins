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
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.TextComponent;
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


public class SkinCommand {
    private static String processing = "";
    private static String changeOP = "";
    private static String recon_needed = "";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("skin")
                .then(Commands.literal("set")
                        .then(Commands.literal("mojang")
                                .then(Commands.argument("skin_name", StringArgumentType.word())
                                        .executes(context ->
                                                skinAction(Collections.singleton(context.getSource().getPlayerOrException()),SkinActionType.nickname,false, SkinVariant.all,false,
                                                        StringArgumentType.getString(context, "skin_name"))))
                                        .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(3))
                                                .executes(context ->
                                                        skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.nickname, true,SkinVariant.all,false,
                                                                StringArgumentType.getString(context, "skin_name")))))
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
                                .executes(context -> {
                                    return skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.random,false,SkinVariant.all,false,null);
                                })
                                .then(Commands.argument("search by width", BoolArgumentType.bool())
                                        .then(Commands.argument("slim", BoolArgumentType.bool())
                                                .then(Commands.literal("cape")
                                                        .executes(context -> {
                                                            SkinVariant variant = SkinVariant.all;
                                                            if(BoolArgumentType.getBool(context,"search by width")){
                                                                if(BoolArgumentType.getBool(context, "slim")) variant=SkinVariant.slim;
                                                                else variant=SkinVariant.classic;
                                                            }
                                                            return skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.random,false,variant,true,null);
                                                        }))
                                                .then(Commands.literal("new")
                                                        .executes(context -> {
                                                            SkinVariant variant = SkinVariant.all;
                                                            if(BoolArgumentType.getBool(context,"search by width")){
                                                                if(BoolArgumentType.getBool(context, "slim")) variant=SkinVariant.slim;
                                                                else variant=SkinVariant.classic;
                                                            }
                                                            return skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.NEW,false,variant,false,null);
                                                        }))
                                                .executes(context -> {
                                                    SkinVariant variant = SkinVariant.all;
                                                    if(BoolArgumentType.getBool(context,"search by width")){
                                                        if(BoolArgumentType.getBool(context, "slim")) variant=SkinVariant.slim;
                                                        else variant=SkinVariant.classic;
                                                    }
                                                    return skinAction(Collections.singleton(context.getSource().getPlayerOrException()), SkinActionType.random,false,variant,false,null);
                                                })))))
                .then(Commands.literal("source")
                        .executes(context ->
                                SkinRestorer.getSkinIO().getSource(context.getSource().getPlayerOrException().getUUID()))
                        .then(Commands.argument("targets", EntityArgument.player()).executes(context ->
                                SkinRestorer.getSkinIO().getSource(EntityArgument.getPlayer(context, "targets").getUUID()))))
                .then(Commands.literal("clear")
                        .executes(context ->
                                skinAction(Collections.singleton(context.getSource().getPlayerOrException()),SkinActionType.clear, false,SkinVariant.all, false,null))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(context ->
                                skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.clear, true,SkinVariant.all, false,null))))
        );
    }

    private static int skinAction(Collection<ServerPlayer> targets, SkinActionType type, boolean setByOperator, SkinVariant variant, boolean withCape, @Nullable String customSource) {
        new Thread(() -> {
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
                targets.stream().findFirst().get().sendMessage(new TextComponent(processing), Mth.createInsecureUUID());

            Property skin = null;
            String source= "";
            if(customSource!=null) source = customSource;
            switch (type) {
                case clear:
                    targets.stream().findFirst().get().getGameProfile().getName();
                    break;
                case url:
                    skin = MineskinSkinProvider.getSkin(customSource, variant);
                    break;
                case nickname:
                    skin = MojangSkinProvider.getSkin(customSource);
                    break;
                case random:
                    source = RandomMojangSkin.randomNick(withCape, variant);
                    skin = MojangSkinProvider.getSkin(source);
                    break;
                case NEW:
                    source = RandomMojangSkin.newNick(variant);
                    skin = MojangSkinProvider.getSkin(source);
                    break;
            }

            for (ServerPlayer player : targets) {
                if(!source.isEmpty())SkinStorage.sourceMap.put(player.getUUID(),source);
                else SkinStorage.sourceMap.put(player.getUUID(),player.getGameProfile().getName());
                SkinRestorer.getSkinStorage().setSkin(player.getUUID(), skin);
                if (setByOperator)
                    player.sendMessage(new TextComponent(changeOP), Mth.createInsecureUUID());
                else
                    player.sendMessage(new TextComponent(recon_needed), Mth.createInsecureUUID());
            }
            for (ServerPlayer player:targets) {
                ServerLevel world = player.getLevel();
                if(!world.isHandlingTick()){
                    task(player,world);
                }
                else {
                    Timer timer = new Timer();
                    TimerTask timerTask = new TimerTask() {
                        @Override
                        public void run() {
                            task(player,world);
                        }
                    };
                    timer.schedule(timerTask,25L);
                }
            }
        }).start();
        return targets.size();
    }

    private static void task(ServerPlayer player, ServerLevel world){
        //Position and rotation packet info
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getXRot();
        float pitch = player.getYRot();
        float headYaw = player.getYHeadRot();
        int yawPacket = Mth.floor(player.getYHeadRot() * 256.0F / 360.0F);
        Set<ClientboundPlayerPositionPacket.RelativeArgument> flags = new HashSet<>();

        //Misc
        int HeldSlot = player.getInventory().selected;
        Abilities abilities = player.getAbilities();

        //Respawn packet info
        Holder<DimensionType> dimensionType=player.getLevel().getLevel().dimensionTypeRegistration();
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

        //Reconnect emulation
        if(!world.isHandlingTick()){
        SkinRestorer.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER, player));
        //world.removePlayer(player,false);
        SkinRestorer.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, player));
        //while (!world.getPlayers().contains(player)) world.addRespawnedPlayer(player);
        player.connection.send(new ClientboundRespawnPacket(dimensionType,registryKey,seedEncrypted,gameType,previousGameType,isDebug,isFlat,true));
        world.removePlayerImmediately(player, Entity.RemovalReason.CHANGED_DIMENSION);
        player.revive();
        player.setPos(x, y, z);
        player.setXRot(yaw);
        player.setYRot(pitch);
        player.setYHeadRot(headYaw);
        player.setLevel(world);
        world.addDuringCommandTeleport(player);
        player.connection.send(new ClientboundPlayerPositionPacket(x,y,z,pitch,yaw,flags,0,false));
        player.connection.send(new ClientboundSetCarriedItemPacket(HeldSlot));
        player.connection.send(new ClientboundPlayerAbilitiesPacket(abilities));
        SkinRestorer.server.getPlayerList().sendAllPlayerInfo(player);
        SkinRestorer.server.getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(player,(byte) yawPacket));
        SkinRestorer.server.getPlayerList().sendPlayerPermissionLevel(player);
        for(MobEffectInstance effectinstance : player.getActiveEffects()) {
            player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), effectinstance));
        }
        SkinRestorer.server.getPlayerList().sendLevelInfo(player,player.getLevel());
        }
        else {
            Timer timer = new Timer();
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    task(player,world);
                }
            };
            timer.schedule(timerTask,25L);
        }
    }
}
