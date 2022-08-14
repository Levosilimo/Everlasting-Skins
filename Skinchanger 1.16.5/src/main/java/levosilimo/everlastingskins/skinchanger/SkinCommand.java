package levosilimo.everlastingskins.skinchanger;

import com.google.common.hash.Hashing;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.enums.LanguageEnum;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.mixin.server.data.MixinTickingEntities;
import net.minecraft.command.CommandSource;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.*;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.DimensionType;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

import static net.minecraft.command.Commands.argument;
import static net.minecraft.command.Commands.literal;

public class SkinCommand {
    private static String processing = "";
    private static String changeOP = "";
    private static String recon_needed = "";

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(literal("skin")
                .then(literal("set")
                        .then(literal("mojang")
                                .then(argument("skin_name", StringArgumentType.word())
                                        .executes(context ->
                                                skinAction(Collections.singleton(context.getSource().asPlayer()),SkinActionType.nickname,false, SkinVariant.all,false,
                                                        StringArgumentType.getString(context, "skin_name"))))
                                        .then(argument("targets", EntityArgument.players()).requires(source -> source.hasPermissionLevel(3))
                                                .executes(context ->
                                                        skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.nickname, true,SkinVariant.all,false,
                                                                StringArgumentType.getString(context, "skin_name")))))
                        .then(literal("web")
                                .then(literal("classic")
                                        .then(argument("url", StringArgumentType.string())
                                                .executes(context ->
                                                        skinAction(Collections.singleton(context.getSource().asPlayer()),SkinActionType.url, false, SkinVariant.classic, false,
                                                                StringArgumentType.getString(context, "url")))
                                                .then(argument("targets", EntityArgument.players()).requires(source -> source.hasPermissionLevel(3))
                                                        .executes(context ->
                                                                skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.url,true,SkinVariant.classic, true,
                                                                        StringArgumentType.getString(context, "url"))))))
                                .then(literal("slim")
                                        .then(argument("url", StringArgumentType.string())
                                                .executes(context ->
                                                        skinAction(Collections.singleton(context.getSource().asPlayer()),SkinActionType.url, false, SkinVariant.slim, false,
                                                                StringArgumentType.getString(context, "url")))
                                                .then(argument("targets", EntityArgument.players()).requires(source -> source.hasPermissionLevel(3))
                                                        .executes(context ->
                                                                skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.url,true,SkinVariant.slim, true,
                                                                        StringArgumentType.getString(context, "url")))))))
                        .then(literal("random")
                                .executes(context -> {
                                    return skinAction(Collections.singleton(context.getSource().asPlayer()), SkinActionType.random,false,SkinVariant.all,false,null);
                                })
                                .then(argument("search by width", BoolArgumentType.bool())
                                        .then(argument("slim", BoolArgumentType.bool())
                                                .then(literal("cape")
                                                        .executes(context -> {
                                                            SkinVariant variant = SkinVariant.all;
                                                            if(BoolArgumentType.getBool(context,"search by width")){
                                                                if(BoolArgumentType.getBool(context, "slim")) variant=SkinVariant.slim;
                                                                else variant=SkinVariant.classic;
                                                            }
                                                            return skinAction(Collections.singleton(context.getSource().asPlayer()), SkinActionType.random,false,variant,true,null);
                                                        }))
                                                .then(literal("new")
                                                        .executes(context -> {
                                                            SkinVariant variant = SkinVariant.all;
                                                            if(BoolArgumentType.getBool(context,"search by width")){
                                                                if(BoolArgumentType.getBool(context, "slim")) variant=SkinVariant.slim;
                                                                else variant=SkinVariant.classic;
                                                            }
                                                            return skinAction(Collections.singleton(context.getSource().asPlayer()), SkinActionType.NEW,false,variant,false,null);
                                                        }))
                                                .executes(context -> {
                                                    SkinVariant variant = SkinVariant.all;
                                                    if(BoolArgumentType.getBool(context,"search by width")){
                                                        if(BoolArgumentType.getBool(context, "slim")) variant=SkinVariant.slim;
                                                        else variant=SkinVariant.classic;
                                                    }
                                                    return skinAction(Collections.singleton(context.getSource().asPlayer()), SkinActionType.random,false,variant,false,null);
                                                })))))
                .then(literal("source")
                        .executes(context ->
                                SkinRestorer.getSkinIO().getSource(context.getSource().asPlayer().getUniqueID()))
                        .then(argument("targets", EntityArgument.player()).executes(context ->
                                SkinRestorer.getSkinIO().getSource(EntityArgument.getPlayer(context, "targets").getUniqueID()))))
                .then(literal("clear")
                        .executes(context ->
                                skinAction(Collections.singleton(context.getSource().asPlayer()),SkinActionType.clear, false,SkinVariant.all, false,null))
                        .then(argument("targets", EntityArgument.players()).executes(context ->
                                skinAction(EntityArgument.getPlayers(context, "targets"),SkinActionType.clear, true,SkinVariant.all, false,null))))
        );
    }

    private static int skinAction(Collection<ServerPlayerEntity> targets, SkinActionType type, boolean setByOperator, SkinVariant variant, boolean withCape, @Nullable String customSource) {
        new Thread(() -> {
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
            if (!setByOperator&&Config.TOGGLE.get()) targets.stream().findFirst().get().sendMessage(new StringTextComponent(processing), MathHelper.getRandomUUID());

            Property skin = null;
            String source= "";
            if(customSource!=null) source = customSource;
            switch (type) {
                case clear:
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

            for (ServerPlayerEntity player : targets) {
                if(!source.isEmpty())SkinStorage.sourceMap.put(player.getUniqueID(),source);
                else SkinStorage.sourceMap.put(player.getUniqueID(),player.getGameProfile().getName());
                SkinRestorer.getSkinStorage().setSkin(player.getUniqueID(), skin);
                if(Config.TOGGLE.get()) {
                    if (setByOperator)
                        player.sendMessage(new StringTextComponent(changeOP), MathHelper.getRandomUUID());
                    else
                        player.sendMessage(new StringTextComponent(recon_needed), MathHelper.getRandomUUID());
                }
            }
            for (ServerPlayerEntity player:targets) {
                ServerWorld world = player.getServerWorld();
                if(!world.isInsideTick()&&!((MixinTickingEntities)world).isTickingEntity()){
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

    private static void task(ServerPlayerEntity player, ServerWorld world){
        //Position and rotation packet info
        double x = player.getPosX();
        double y = player.getPosY();
        double z = player.getPosZ();
        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;
        float headYaw = player.rotationYawHead;
        int yawPacket = MathHelper.floor(player.getRotationYawHead() * 256.0F / 360.0F);
        Set<SPlayerPositionLookPacket.Flags> flags = new HashSet<>();

        //Misc
        int HeldSlot = player.inventory.currentItem;
        PlayerAbilities abilities = player.abilities;

        //Respawn packet info
        DimensionType dimensionType=player.getServerWorld().getDimensionType();
        RegistryKey<World> registryKey = player.getServerWorld().getDimensionKey();
        long seedEncrypted = Hashing.sha256().hashString(String.valueOf(player.getServerWorld().getSeed()), StandardCharsets.UTF_8).asLong();
        GameType gameType = player.interactionManager.getGameType();
        GameType previousGameType = player.interactionManager.func_241815_c_();
        boolean isDebug = player.getServerWorld().isDebug();
        boolean isFlat = player.getServerWorld().func_241109_A_();
        //Skin change
        SkinRestorer.getSkinStorage().removeSkin(player.getUniqueID());
        player.getGameProfile().getProperties().removeAll("textures");
        player.getGameProfile().getProperties().put("textures", SkinRestorer.getSkinStorage().getSkin(player.getUniqueID()));

        //Reconnect emulation
        boolean getTicking = ((MixinTickingEntities)world).isTickingEntity();
        if(!world.isInsideTick()&&!getTicking){
        SkinRestorer.server.getPlayerList().sendPacketToAllPlayers(new SPlayerListItemPacket(SPlayerListItemPacket.Action.REMOVE_PLAYER, player));
        //world.removePlayer(player,false);
        SkinRestorer.server.getPlayerList().sendPacketToAllPlayers(new SPlayerListItemPacket(SPlayerListItemPacket.Action.ADD_PLAYER, player));
        //while (!world.getPlayers().contains(player)) world.addRespawnedPlayer(player);
        player.connection.sendPacket(new SRespawnPacket(dimensionType,registryKey,seedEncrypted,gameType,previousGameType,isDebug,isFlat,true));
        world.removePlayer(player, true);
        player.revive();
        player.setLocationAndAngles(x, y, z, yaw, pitch);
        player.setRotationYawHead(headYaw);
        player.setWorld(world);
        world.addDuringCommandTeleport(player);
        player.connection.sendPacket(new SPlayerPositionLookPacket(x,y,z,yaw,pitch,flags,0));
        player.connection.sendPacket(new SHeldItemChangePacket(HeldSlot));
        player.connection.sendPacket(new SPlayerAbilitiesPacket(abilities));
        SkinRestorer.server.getPlayerList().sendInventory(player);
        SkinRestorer.server.getPlayerList().sendPacketToAllPlayers(new SEntityHeadLookPacket(player,(byte) yawPacket));
        SkinRestorer.server.getPlayerList().updatePermissionLevel(player);
        for(EffectInstance effectinstance : player.getActivePotionEffects()) {
            player.connection.sendPacket(new SPlayEntityEffectPacket(player.getEntityId(), effectinstance));
        }
        SkinRestorer.server.getPlayerList().sendWorldInfo(player,player.getServerWorld());
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
