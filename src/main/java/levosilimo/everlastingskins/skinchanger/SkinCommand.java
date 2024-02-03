package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.enums.*;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static levosilimo.everlastingskins.EverlastingSkins.skinCommandExecutor;


public class SkinCommand extends CommandBase {
    private static String processing = "";
    private static String changeOP = "";
    private static String recon_needed = "";

    private static int skinAction(Collection<EntityPlayerMP> targets, SkinActionType actionType, SkinActionSetType actionSetType, boolean setByOperator, SkinVariant variant, CapeVariant capeVariant, @Nullable String customSource) {
        CompletableFuture<ArrayList<EmulateReconnectHandler>> future = CompletableFuture.supplyAsync(() -> {
            LanguageEnum a = LanguageEnum.fromName(EverlastingSkins.language);
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
                targets.stream().findFirst().get().sendMessage(new TextComponentString(processing));

            Property skin = null;
            String source = "";
            ArrayList<EmulateReconnectHandler> handlers = new ArrayList<>();
            if (customSource != null) source = customSource;
            switch (actionType) {
                case clear:
                    skin = MojangSkinProvider.getSkin(targets.stream().findFirst().get().getGameProfile().getName());
                    break;
                case set:
                    switch (actionSetType) {
                        case web:
                            skin = MineskinSkinProvider.getSkin(customSource, variant);
                            break;
                        case mojang:
                            skin = MojangSkinProvider.getSkin(customSource);
                            break;
                        case random:
                            source = RandomMojangSkin.randomNickname(capeVariant, variant, false);
                            skin = MojangSkinProvider.getSkin(source);
                            break;
                    }
                    break;

            }

            for (EntityPlayerMP player : targets) {
                if (!source.isEmpty()) SkinStorage.sourceMap.put(player.getUniqueID(), source);
                else SkinStorage.sourceMap.put(player.getUniqueID(), player.getGameProfile().getName());
                SkinRestorer.getSkinStorage().setSkin(player.getUniqueID(), skin);
                if (setByOperator)
                    player.sendMessage(new TextComponentString(changeOP));
                else
                    player.sendMessage(new TextComponentString(recon_needed));
            }
            for (EntityPlayerMP player : targets) handlers.add(new EmulateReconnectHandler(player));
            return handlers;
        }, skinCommandExecutor);
        try {
            ArrayList<EmulateReconnectHandler> result = future.get(10, TimeUnit.SECONDS);
            result.forEach((handler) -> SkinRestorer.server.addScheduledTask(handler::emulateReconnect));
        } catch (TimeoutException e) {
            targets.forEach((target) -> target.sendMessage(new TextComponentString("§6[EverlastingSkins]§f Skin was not set.")));
        } catch (Exception e) {
            targets.forEach((target) -> target.sendMessage(new TextComponentString("§6[EverlastingSkins]§f Unexpected error.")));
            e.printStackTrace();
        }

        return targets.size();
    }

    @Override
    public String getName() {
        return "skin";
    }

    @Override
    public String getUsage(ICommandSender iCommandSender) {
        return "commands.everlastingskins.skin.usage";
    }

    @Override
    public void execute(MinecraftServer minecraftServer, ICommandSender iCommandSender, String[] commandArgs) throws CommandException {
        int argsPointer = 0;
        SkinActionType actionType;
        SkinActionSetType actionSetType = null;
        SkinVariant skinVariant = SkinVariant.ANY;
        CapeVariant capeVariant = CapeVariant.ANY;
        String customSource = null;

        try {
            actionType = SkinActionType.valueOf(commandArgs[argsPointer++]);
        } catch (Exception e) {
            throw new WrongUsageException("commands.everlastingskins.skin.usage");
        }

        if (actionType == SkinActionType.set) {
            try {
                actionSetType = SkinActionSetType.valueOf(commandArgs[argsPointer++]);
                if (commandArgs.length > argsPointer) {
                    String nextArg = commandArgs[argsPointer++];

                    if (actionSetType.equals(SkinActionSetType.random)) {
                        if (SkinVariant.contains(nextArg)) {
                            skinVariant = SkinVariant.fromName(nextArg);
                        } else if (CapeVariant.contains(nextArg)) {
                            capeVariant = CapeVariant.fromName(nextArg);
                        } else {
                            customSource = nextArg;
                        }
                    } else if (actionSetType.equals(SkinActionSetType.web)) {
                        if(SkinVariant.contains(nextArg)) {
                            skinVariant = SkinVariant.fromName(nextArg);
                        }
                        else {
                            customSource = nextArg;
                        }
                    } else {
                        customSource = nextArg;
                    }

                    if (commandArgs.length > argsPointer) {
                        nextArg = commandArgs[argsPointer++];
                        if (actionSetType.equals(SkinActionSetType.random) && CapeVariant.contains(nextArg)) {
                            capeVariant = CapeVariant.fromName(nextArg);
                        } else {
                            customSource = nextArg;
                        }
                    }

                    if (commandArgs.length > argsPointer) {
                        customSource = commandArgs[argsPointer++];
                    }
                }
            } catch (Exception e) {
                throw new WrongUsageException("commands.everlastingskins.skin.usage");
            }
        }

        List<EntityPlayerMP> players = commandArgs.length <= argsPointer ?
                Collections.singletonList((EntityPlayerMP) iCommandSender.getCommandSenderEntity()) :
                getPlayers(minecraftServer, iCommandSender, commandArgs[argsPointer++]);

        if (actionType == SkinActionType.source) {
            SkinRestorer.getSkinIO().getSource(players.stream().findFirst().get().getUniqueID());
            return;
        }

        skinAction(players, actionType, actionSetType, iCommandSender.canUseCommand(3, "skin"), skinVariant, capeVariant, customSource);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SkinActionType.getStringValues());
        } else if (args.length == 2) {
            String actionType = args[0];
            switch (actionType) {
                case "clear":
                    return sender.canUseCommand(3, "skin") ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) : Collections.emptyList();
                case "set":
                    return getListOfStringsMatchingLastWord(args, SkinActionSetType.getStringValues());
                case "source":
                    return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
            }
        } else if (args.length == 3 && args[0].equals("set")) {
            String setSubType = args[1];
            switch (setSubType) {
                case "mojang":
                    return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
                case "random":
                    return getListOfStringsMatchingLastWord(args, SkinVariant.getStringValues());
                case "web":
                    return getListOfStringsMatchingLastWord(args, "slim", "classic");
                default:
                    return getListOfStringsMatchingLastWord(args, SkinActionSetType.getStringValues());
            }
        } else if (args.length >= 4 && args[0].equals("set") && args[1].equals("random") &&
                Arrays.asList(SkinVariant.getStringValues()).contains(args[2])) {
            if (args.length == 4) {
                return getListOfStringsMatchingLastWord(args, CapeVariant.getStringValues());
            } else if (args.length == 5 && CapeVariant.contains(args[3]))
                return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }
}
