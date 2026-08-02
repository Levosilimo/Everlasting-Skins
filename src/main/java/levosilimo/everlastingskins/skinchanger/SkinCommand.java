package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.metrics.MetricsFormat;
import levosilimo.everlastingskins.metrics.PlayerSnapshot;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SkinCommand extends CommandBase {
    static final String PREFIX = "§6[" + EverlastingSkins.MOD_NAME + "]§f ";

    private static MineSkinAPI mineSkinAPI = new MineSkinApiHttpImpl();
    private static MojangAPI mojangAPI = new MojangApiHttpImpl();

    public static MineSkinAPI getMineSkinAPI() {
        return mineSkinAPI;
    }

    public static MojangAPI getMojangAPI() {
        return mojangAPI;
    }

    /* Package-private for tests: inject fakes without reflection. */
    static void setMineSkinAPI(MineSkinAPI api) {
        mineSkinAPI = api;
    }

    static void setMojangAPI(MojangAPI api) {
        mojangAPI = api;
    }

    static void resetAPIs() {
        mojangAPI = new MojangApiHttpImpl();
        mineSkinAPI = new MineSkinApiHttpImpl();
    }

    @Override
    public String getName() {
        return "skin";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/skin <set|clear|source|metrics> ...";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("eskin");
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            sender.sendMessage(new TextComponentString(PREFIX + getUsage(sender)));
            return;
        }
        switch (args[0]) {
            case "clear":   doClear(server, sender, args); break;
            case "source":  doSource(server, sender, args); break;
            case "set":     doSet(server, sender, args); break;
            case "metrics": doMetrics(sender, args); break;
            default:
                sender.sendMessage(new TextComponentString(PREFIX + getUsage(sender)));
        }
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
            String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) return Arrays.asList("set", "clear", "source", "metrics");
        if (args.length == 2 && "metrics".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "human", "json", "players", "cleanup", "reset");
        }
        return Collections.emptyList();
    }

    public static void register(MinecraftServer server) {
        ((CommandHandler) server.getCommandManager()).registerCommand(new SkinCommand());
    }

    private void doClear(MinecraftServer server, ICommandSender sender, String[] args) {
        Collection<EntityPlayerMP> targets = parseTargets(server, sender, args, true, 2);
        String node = targets.size() == 1 && targets.iterator().next() == sender
            ? "everlastingskins.command.skin.clear"
            : "everlastingskins.command.skin.other";
        if (!checkPermission(sender, node)) return;
        SkinAction.apply(targets, sender, SkinActionType.clear, SkinVariant.ALL, false, null);
    }

    private void doSource(MinecraftServer server, ICommandSender sender, String[] args) {
        EntityPlayerMP target;
        try {
            target = args.length >= 2
                ? CommandBase.getPlayer(server, sender, args[1])
                : CommandBase.getCommandSenderAsPlayer(sender);
        } catch (CommandException e) {
            sender.sendMessage(new TextComponentString(PREFIX + e.getMessage()));
            return;
        }
        if (args.length >= 2) {
            if (!checkPermission(sender, "everlastingskins.command.skin.other")) return;
        }
        UUID uuid = target.getUniqueID();
        if (SkinRestorer.getSkinStorage().hasDefaultSkin(uuid)) {
            sender.sendMessage(new TextComponentString(PREFIX + target.getGameProfile().getName()));
            return;
        }
        String source = SkinRestorer.getSkinStorage().getSource(uuid);
        sender.sendMessage(new TextComponentString(PREFIX + (source != null ? source : "No source available")));
    }

    private void doSet(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString(PREFIX + getUsage(sender)));
            return;
        }
        switch (args[1]) {
            case "mojang": {
                if (args.length < 3) {
                    sender.sendMessage(new TextComponentString(PREFIX + "Usage: /skin set mojang <name>"));
                    return;
                }
                Collection<EntityPlayerMP> targets = parseTargets(server, sender, args, false, 4);
                String node = targets.size() == 1 && targets.iterator().next() == sender
                    ? "everlastingskins.command.skin"
                    : "everlastingskins.command.skin.other";
                if (!checkPermission(sender, node)) return;
                SkinAction.apply(targets, sender, SkinActionType.username, SkinVariant.ALL, false, args[2]);
                break;
            }
            case "web": {
                if (!Config.MINESKIN_ENABLED) {
                    sender.sendMessage(new TextComponentString(PREFIX + "MineSkin is disabled in config"));
                    return;
                }
                if (args.length < 4) {
                    sender.sendMessage(new TextComponentString(PREFIX + "Usage: /skin set web <classic|slim> <url>"));
                    return;
                }
                SkinVariant variant = "slim".equalsIgnoreCase(args[2]) ? SkinVariant.SLIM : SkinVariant.CLASSIC;
                Collection<EntityPlayerMP> targets = parseTargets(server, sender, args, false, 5);
                String node = targets.size() == 1 && targets.iterator().next() == sender
                    ? "everlastingskins.command.skin.url"
                    : "everlastingskins.command.skin.other";
                if (!checkPermission(sender, node)) return;
                SkinAction.apply(targets, sender, SkinActionType.url, variant, false, args[3]);
                break;
            }
            case "random":
                Collection<EntityPlayerMP> targets = parseTargets(server, sender, args, false, 3);
                String node = targets.size() == 1 && targets.iterator().next() == sender
                    ? "everlastingskins.command.skin"
                    : "everlastingskins.command.skin.other";
                if (!checkPermission(sender, node)) return;
                SkinAction.apply(targets, sender, SkinActionType.random, SkinVariant.ALL, false, null);
                break;
            default:
                sender.sendMessage(new TextComponentString(PREFIX + "Usage: /skin set <mojang|web|random>"));
        }
    }

    private boolean checkPermission(ICommandSender sender, String node) {
        if (sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender;
            PermissionContext ctx = PermissionContext.of(player.getUniqueID(), player.canUseCommand(2, "everlastingskins"));
            if (!PermissionServiceManager.hasPermission(ctx, node)) {
                sender.sendMessage(new TextComponentString(PREFIX + "Permission denied"));
                return false;
            }
        }
        return true;
    }

    /**
     * /skin metrics [human|json|players|cleanup|reset]. View commands need
     * everlastingskins.command.metrics; cleanup/reset additionally need
     * everlastingskins.command.metrics.reset. Console senders are allowed.
     */
    private void doMetrics(ICommandSender sender, String[] args) {
        String sub = args.length < 2 ? "human" : args[1];
        switch (sub) {
            case "json":
                if (!checkMetricsPermission(sender)) return;
                sender.sendMessage(new TextComponentString(PREFIX + MetricsFormat.json(SkinMetrics.INSTANCE.snapshot())));
                break;
            case "players":
                if (!checkMetricsPermission(sender)) return;
                StringBuilder sb = new StringBuilder(PREFIX + "Top players by refresh count:");
                int rank = 0;
                for (Map.Entry<UUID, PlayerSnapshot> e : SkinMetrics.INSTANCE.topPlayers(10)) {
                    sb.append("\n  ").append(++rank).append(". ")
                        .append(e.getKey()).append(" — ")
                        .append(e.getValue().refreshCount()).append(" refreshes");
                }
                if (rank == 0) sb.append("\n  (no refreshes recorded)");
                sender.sendMessage(new TextComponentString(sb.toString()));
                break;
            case "cleanup":
                if (!checkMetricsResetPermission(sender)) return;
                int removed = SkinMetrics.INSTANCE.cleanupStalePlayers(30L * 24 * 60 * 60 * 1000);
                sender.sendMessage(new TextComponentString(PREFIX + "Metrics cleanup: pruned " + removed + " stale player entries"));
                break;
            case "reset":
                if (!checkMetricsResetPermission(sender)) return;
                SkinMetrics.INSTANCE.reset();
                sender.sendMessage(new TextComponentString(PREFIX + "Metrics reset"));
                break;
            default:
                if (!checkMetricsPermission(sender)) return;
                sender.sendMessage(new TextComponentString(PREFIX + MetricsFormat.human(SkinMetrics.INSTANCE.snapshot())));
        }
    }

    private boolean checkMetricsPermission(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) return true; // console
        EntityPlayerMP player = (EntityPlayerMP) sender;
        PermissionContext ctx = PermissionContext.of(player.getUniqueID(), player.canUseCommand(2, "everlastingskins"));
        if (!PermissionServiceManager.hasPermission(ctx, "everlastingskins.command.metrics")) {
            sender.sendMessage(new TextComponentString(PREFIX + "Permission denied"));
            return false;
        }
        return true;
    }

    private boolean checkMetricsResetPermission(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) return true; // console
        EntityPlayerMP player = (EntityPlayerMP) sender;
        PermissionContext ctx = PermissionContext.of(player.getUniqueID(), player.canUseCommand(2, "everlastingskins"));
        if (!PermissionServiceManager.hasPermission(ctx, "everlastingskins.command.metrics.reset")) {
            sender.sendMessage(new TextComponentString(PREFIX + "Permission denied"));
            return false;
        }
        return true;
    }

    private Collection<EntityPlayerMP> parseTargets(MinecraftServer server, ICommandSender sender,
            String[] args, boolean requiresOpAtLeastOne, int targetIndex) {
        if (args.length > targetIndex) {
            if (!checkPermission(sender, "everlastingskins.command.skin.other")) {
                return Collections.emptyList();
            }
            List<EntityPlayerMP> out = new ArrayList<>();
            for (int i = targetIndex; i < args.length; i++) {
                try {
                    out.add(CommandBase.getPlayer(server, sender, args[i]));
                } catch (CommandException ignored) {
                }
            }
            return out;
        }
        try {
            return Collections.singletonList(CommandBase.getCommandSenderAsPlayer(sender));
        } catch (CommandException e) {
            sender.sendMessage(new TextComponentString(PREFIX + e.getMessage()));
            return Collections.emptyList();
        }
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
}
