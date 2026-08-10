/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

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
import levosilimo.everlastingskins.util.CompletionSources;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.HttpsUrlConnectionHttpClient;
import levosilimo.everlastingskins.util.I18nUtils;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SkinCommand extends CommandBase {
    static final String PREFIX = "§6[" + EverlastingSkins.MOD_NAME + "]§f ";

    private static MineSkinAPI mineSkinAPI = new MineSkinApiHttpImpl();
    private static MojangAPI mojangAPI = createMojangAPI();

    /**
     * Builds the Mojang API with a caller-owned profile cache shared with
     * {@link CompletionSources}, so recently fetched profiles show up in
     * tab completion.
     */
    private static MojangAPI createMojangAPI() {
        MojangProfileCache sharedCache = new MojangProfileCache();
        CompletionSources.setMojangProfileCache(sharedCache);
        return new MojangApiHttpImpl(MojangEndpoints.DEFAULT, new HttpsUrlConnectionHttpClient(), true, sharedCache);
    }

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
        mojangAPI = createMojangAPI();
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
        if (args.length == 1) {
            return subcommandCompletions(sender, args);
        }
        if ("set".equals(args[0])) {
            return setTabCompletions(server, sender, args);
        }
        if (("clear".equals(args[0]) || "source".equals(args[0])) && args.length == 2) {
            return canTargetOthers(sender)
                ? getListOfStringsMatchingLastWord(args, CompletionSources.onlinePlayerNames(server))
                : Collections.emptyList();
        }
        if ("metrics".equals(args[0]) && args.length == 2) {
            return getListOfStringsMatchingLastWord(args, CompletionSources.metricsSubcommands(sender));
        }
        return Collections.emptyList();
    }

    /** Subcommand names the sender may actually use, in /skin usage order. */
    private List<String> subcommandCompletions(ICommandSender sender, String[] args) {
        List<String> subcommands = new ArrayList<>();
        if (CompletionSources.hasPermission(sender, "everlastingskins.command.skin")) {
            subcommands.add("set");
        }
        if (CompletionSources.hasPermission(sender, "everlastingskins.command.skin.clear")) {
            subcommands.add("clear");
        }
        if (CompletionSources.hasPermission(sender, "everlastingskins.command.skin")) {
            subcommands.add("source");
        }
        if (CompletionSources.hasPermission(sender, "everlastingskins.command.metrics")) {
            subcommands.add("metrics");
        }
        return getListOfStringsMatchingLastWord(args, subcommands);
    }

    /** Per-position candidates under {@code /skin set ...}. */
    private List<String> setTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, CompletionSources.providerNames());
        }
        if ("mojang".equals(args[1]) && args.length == 3) {
            return getListOfStringsMatchingLastWord(args, CompletionSources.recentUsernames());
        }
        if ("web".equals(args[1])) {
            if (args.length == 3) return getListOfStringsMatchingLastWord(args, "classic", "slim");
            if (args.length == 4) return getListOfStringsMatchingLastWord(args, CompletionSources.urlCandidates());
        }
        if ("random".equals(args[1]) && args.length >= 3 && args.length <= 5) {
            if (args.length == 3) return getListOfStringsMatchingLastWord(args, "true", "false");
            if (args.length == 4) return getListOfStringsMatchingLastWord(args, "classic", "slim");
            return canTargetOthers(sender)
                ? getListOfStringsMatchingLastWord(args, CompletionSources.onlinePlayerNames(server))
                : Collections.emptyList();
        }
        return Collections.emptyList();
    }

    private boolean canTargetOthers(ICommandSender sender) {
        return CompletionSources.hasPermission(sender, "everlastingskins.command.skin.other");
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
        sender.sendMessage(new TextComponentString(PREFIX
            + (source != null ? source : I18nUtils.getLocalizedString("no_source", playerOrNull(sender)))));
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
            case "random": {
                // /skin set random [<cape> [<variant>]] [<targets...>]: the
                // cape flag and variant are optional and parsed to match the
                // tab-completion cascade (bool, then variant, then targets).
                boolean cape = args.length >= 3 && "true".equalsIgnoreCase(args[2]);
                SkinVariant variant = parseRandomVariant(args);
                Collection<EntityPlayerMP> targets = parseTargets(server, sender, args, false, 4);
                String node = targets.size() == 1 && targets.iterator().next() == sender
                    ? "everlastingskins.command.skin"
                    : "everlastingskins.command.skin.other";
                if (!checkPermission(sender, node)) return;
                SkinAction.apply(targets, sender, SkinActionType.random, variant, cape, null);
                break;
            }
            default:
                sender.sendMessage(new TextComponentString(PREFIX + "Usage: /skin set <mojang|web|random>"));
        }
    }

    /**
     * Variant argument of {@code /skin set random}: optional, defaults to ALL.
     */
    private static SkinVariant parseRandomVariant(String[] args) {
        if (args.length >= 4 && "slim".equalsIgnoreCase(args[3])) {
            return SkinVariant.SLIM;
        }
        if (args.length >= 4 && "classic".equalsIgnoreCase(args[3])) {
            return SkinVariant.CLASSIC;
        }
        return SkinVariant.ALL;
    }

    private boolean checkPermission(ICommandSender sender, String node) {
        if (sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender;
            PermissionContext ctx = PermissionContext.of(player.getUniqueID(), player);
            if (!PermissionServiceManager.hasPermission(ctx.uuid(), ctx.opLevel(), node)) {
                sender.sendMessage(new TextComponentString(PREFIX
                    + I18nUtils.getLocalizedString("permission_denied", player)));
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
                StringBuilder sb = new StringBuilder(PREFIX
                    + I18nUtils.getLocalizedString("metrics_top_players", playerOrNull(sender)));
                int rank = 0;
                for (Map.Entry<UUID, PlayerSnapshot> e : SkinMetrics.INSTANCE.topPlayers(10)) {
                    sb.append("\n  ").append(++rank).append(". ")
                        .append(e.getKey()).append(" — ")
                        .append(e.getValue().refreshCount())
                        .append(I18nUtils.getLocalizedString("metrics_refreshes", playerOrNull(sender)));
                }
                if (rank == 0) {
                    sb.append("\n  ").append(I18nUtils.getLocalizedString("metrics_no_refreshes", playerOrNull(sender)));
                }
                sender.sendMessage(new TextComponentString(sb.toString()));
                break;
            case "cleanup":
                if (!checkMetricsResetPermission(sender)) return;
                int removed = SkinMetrics.INSTANCE.cleanupStalePlayers(30L * 24 * 60 * 60 * 1000);
                sender.sendMessage(new TextComponentString(PREFIX
                    + I18nUtils.formatMessage("metrics_cleanup", playerOrNull(sender), removed)));
                break;
            case "reset":
                if (!checkMetricsResetPermission(sender)) return;
                SkinMetrics.INSTANCE.reset();
                sender.sendMessage(new TextComponentString(PREFIX
                    + I18nUtils.getLocalizedString("metrics_reset", playerOrNull(sender))));
                break;
            default:
                if (!checkMetricsPermission(sender)) return;
                sender.sendMessage(new TextComponentString(PREFIX + MetricsFormat.human(SkinMetrics.INSTANCE.snapshot())));
        }
    }

    private boolean checkMetricsPermission(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) return true; // console
        EntityPlayerMP player = (EntityPlayerMP) sender;
        PermissionContext ctx = PermissionContext.of(player.getUniqueID(), player);
        if (!PermissionServiceManager.hasPermission(ctx.uuid(), ctx.opLevel(), "everlastingskins.command.metrics")) {
            sender.sendMessage(new TextComponentString(PREFIX
                + I18nUtils.getLocalizedString("permission_denied", player)));
            return false;
        }
        return true;
    }

    private boolean checkMetricsResetPermission(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) return true; // console
        EntityPlayerMP player = (EntityPlayerMP) sender;
        PermissionContext ctx = PermissionContext.of(player.getUniqueID(), player);
        if (!PermissionServiceManager.hasPermission(ctx.uuid(), ctx.opLevel(), "everlastingskins.command.metrics.reset")) {
            sender.sendMessage(new TextComponentString(PREFIX
                + I18nUtils.getLocalizedString("permission_denied", player)));
            return false;
        }
        return true;
    }

    /** The player behind a console-safe sender, or null (fallback to Config.LANGUAGE). */
    @Nullable
    private static EntityPlayerMP playerOrNull(ICommandSender sender) {
        return sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
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
