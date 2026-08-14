/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.metrics.MetricsFormat;
import levosilimo.everlastingskins.metrics.PlayerSnapshot;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.forge.ForgePermissionService;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.HttpsUrlConnectionHttpClient;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1.8.9 {@code /skin} command over the legacy {@link ICommand} surface
 * (getCommandName/processCommand — the 1.8-era MCP names; the getName/
 * execute rename landed in 1.11). Full parity surface with the mc1.12.2
 * SkinCommand: {@code set mojang|web|random}, {@code source}, {@code clear},
 * {@code metrics} with multi-target support.
 *
 * <p>Permission model: the vanilla {@link ICommand} op gate is left open
 * (parity with the 1.12.2 {@code checkPermission}) and every subcommand
 * gates through {@link PermissionServiceManager} with the lane's node
 * naming (see {@link ForgePermissionService}). Console senders pass all
 * node checks. Target-other, URL and metrics-cleanup operations require
 * op level 2.
 */
public class SkinCommand implements ICommand {

    static final String PREFIX = "§6[Everlasting Skins]§f ";

    private static final String NAME = "skin";
    private static final List<String> ALIASES = Collections.singletonList("eskin");

    /**
     * Default URL allowlist domains — mirrors the 1.21 Config
     * {@code urlAllowlistDomains} defaults (and common's
     * MineSkinApiHttpImpl.DEFAULT_ALLOWLIST_DOMAINS). This lane has no Config
     * surface, so the allowlist is always ON with these domains (audit fix:
     * the no-arg constructor defaults to allowlist OFF).
     */
    private static final List<String> ALLOWLIST_DOMAINS = Arrays.asList(
            "imgur.com", "storage.googleapis.com", "cdn.discordapp.com",
            "textures.minecraft.net", "namemc.com", "crafatar.com",
            "mc-heads.net", "githubusercontent.com", "minecraftskins.com");

    private static MineSkinAPI mineSkinAPI = new MineSkinApiHttpImpl(
            new HttpsUrlConnectionHttpClient(), "", true, ALLOWLIST_DOMAINS);
    private static MojangAPI mojangAPI = new MojangApiHttpImpl(MojangEndpoints.DEFAULT,
        new HttpsUrlConnectionHttpClient(), true, new MojangProfileCache());

    /* Package-private for tests: inject fakes without reflection. */
    static MojangAPI getMojangAPI() {
        return mojangAPI;
    }

    static MineSkinAPI getMineSkinAPI() {
        return mineSkinAPI;
    }

    static void setMojangAPI(MojangAPI api) {
        mojangAPI = api;
    }

    static void setMineSkinAPI(MineSkinAPI api) {
        mineSkinAPI = api;
    }

    static void resetAPIs() {
        mojangAPI = new MojangApiHttpImpl(MojangEndpoints.DEFAULT,
            new HttpsUrlConnectionHttpClient(), true, new MojangProfileCache());
        mineSkinAPI = new MineSkinApiHttpImpl(
                new HttpsUrlConnectionHttpClient(), "", true, ALLOWLIST_DOMAINS);
    }

    @Override
    public String getCommandName() {
        return NAME;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/skin <set|clear|source|metrics> ...";
    }

    @Override
    public List<String> getCommandAliases() {
        return ALIASES;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            sender.addChatMessage(new ChatComponentText(PREFIX + getCommandUsage(sender)));
            return;
        }
        String sub = args[0];
        if ("clear".equals(sub)) {
            doClear(sender, args);
        } else if ("source".equals(sub)) {
            doSource(sender, args);
        } else if ("set".equals(sub)) {
            doSet(sender, args);
        } else if ("metrics".equals(sub)) {
            doMetrics(sender, args);
        } else {
            sender.addChatMessage(new ChatComponentText(PREFIX + getCommandUsage(sender)));
        }
    }

    /**
     * Node checks live inside the subcommands (parity with the 1.12.2
     * {@code checkPermission} returning true): the vanilla dispatcher never
     * pre-gates, so denial messages are under the command's control.
     */
    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return subcommandCompletions(sender, args);
        }
        if ("set".equals(args[0])) {
            return setTabCompletions(sender, args);
        }
        if (("clear".equals(args[0]) || "source".equals(args[0])) && args.length == 2) {
            return canTargetOthers(sender)
                ? CommandBase.getListOfStringsMatchingLastWord(args, onlinePlayerNames())
                : Collections.emptyList();
        }
        if ("metrics".equals(args[0]) && args.length == 2) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "json", "players", "cleanup", "reset");
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    @Override
    public int compareTo(ICommand other) {
        return getCommandName().compareTo(other.getCommandName());
    }

    /** Subcommand names the sender may actually use, in /skin usage order. */
    private List<String> subcommandCompletions(ICommandSender sender, String[] args) {
        List<String> subcommands = new ArrayList<>();
        if (hasPermission(sender, ForgePermissionService.SKIN_NODE, 2)) {
            subcommands.add("set");
        }
        if (hasPermission(sender, ForgePermissionService.SKIN_CLEAR_NODE, 2)) {
            subcommands.add("clear");
        }
        if (hasPermission(sender, ForgePermissionService.SKIN_SOURCE_NODE, 2)) {
            subcommands.add("source");
        }
        if (hasPermission(sender, ForgePermissionService.METRICS_NODE, 2)) {
            subcommands.add("metrics");
        }
        return CommandBase.getListOfStringsMatchingLastWord(args, subcommands);
    }

    /** Per-position candidates under {@code /skin set ...}. */
    private List<String> setTabCompletions(ICommandSender sender, String[] args) {
        if (args.length == 2) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "mojang", "web", "random");
        }
        if ("mojang".equals(args[1]) && args.length == 3) {
            return CommandBase.getListOfStringsMatchingLastWord(args, onlinePlayerNames());
        }
        if ("web".equals(args[1])) {
            if (args.length == 3) {
                return CommandBase.getListOfStringsMatchingLastWord(args, "classic", "slim");
            }
            if (args.length == 4) {
                return canTargetOthers(sender)
                    ? CommandBase.getListOfStringsMatchingLastWord(args, onlinePlayerNames())
                    : Collections.emptyList();
            }
        }
        if ("random".equals(args[1])) {
            if (args.length == 3) {
                return CommandBase.getListOfStringsMatchingLastWord(args, "true", "false");
            }
            if (args.length == 4) {
                return CommandBase.getListOfStringsMatchingLastWord(args, "classic", "slim");
            }
            if (args.length == 5) {
                return canTargetOthers(sender)
                    ? CommandBase.getListOfStringsMatchingLastWord(args, onlinePlayerNames())
                    : Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    private void doClear(ICommandSender sender, String[] args) throws CommandException {
        Collection<EntityPlayerMP> targets = parseTargets(sender, args, 2);
        String node = singleSelf(targets, sender)
            ? ForgePermissionService.SKIN_CLEAR_NODE
            : ForgePermissionService.SKIN_OTHER_NODE;
        if (!checkPermission(sender, node, 2)) {
            return;
        }
        SkinAction.apply(targets, sender, SkinActionType.clear, SkinVariant.ALL, false, null);
    }

    private void doSource(ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP target;
        try {
            target = args.length >= 2
                ? CommandBase.getPlayer(sender, args[1])
                : CommandBase.getCommandSenderAsPlayer(sender);
        } catch (CommandException e) {
            sender.addChatMessage(new ChatComponentText(PREFIX + e.getMessage()));
            return;
        }
        if (args.length >= 2 && !checkPermission(sender, ForgePermissionService.SKIN_OTHER_NODE, 2)) {
            return;
        }
        // SKIN_SOURCE_NODE grants unconditionally on the Forge backend, but
        // stays fail-closed when no backend is registered.
        if (!checkPermission(sender, ForgePermissionService.SKIN_SOURCE_NODE, 2)) {
            return;
        }
        UUID uuid = SkinRestorer.profileIdOf(target);
        SkinStorage storage = SkinRestorer.getSkinStorage();
        if (storage == null) {
            sender.addChatMessage(new ChatComponentText(PREFIX + "Skin storage not ready"));
            return;
        }
        if (storage.hasDefaultSkin(uuid)) {
            sender.addChatMessage(new ChatComponentText(PREFIX + target.getGameProfile().getName()));
            return;
        }
        String source = storage.getSource(uuid);
        sender.addChatMessage(new ChatComponentText(PREFIX
            + (source != null ? source : "No stored source for " + target.getGameProfile().getName())));
    }

    private void doSet(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            sender.addChatMessage(new ChatComponentText(PREFIX + getCommandUsage(sender)));
            return;
        }
        String provider = args[1];
        if ("mojang".equals(provider)) {
            if (args.length < 3) {
                sender.addChatMessage(new ChatComponentText(PREFIX + "Usage: /skin set mojang <name>"));
                return;
            }
            Collection<EntityPlayerMP> targets = parseTargets(sender, args, 4);
            String node = singleSelf(targets, sender)
                ? ForgePermissionService.SKIN_NODE
                : ForgePermissionService.SKIN_OTHER_NODE;
            if (!checkPermission(sender, node, 2)) {
                return;
            }
            SkinAction.apply(targets, sender, SkinActionType.username, SkinVariant.ALL, false, args[2]);
        } else if ("web".equals(provider)) {
            if (args.length < 4) {
                sender.addChatMessage(new ChatComponentText(PREFIX + "Usage: /skin set web <classic|slim> <url>"));
                return;
            }
            SkinVariant variant = "slim".equalsIgnoreCase(args[2]) ? SkinVariant.SLIM : SkinVariant.CLASSIC;
            Collection<EntityPlayerMP> targets = parseTargets(sender, args, 5);
            String node = singleSelf(targets, sender)
                ? ForgePermissionService.SKIN_URL_NODE
                : ForgePermissionService.SKIN_OTHER_NODE;
            if (!checkPermission(sender, node, 2)) {
                return;
            }
            SkinAction.apply(targets, sender, SkinActionType.url, variant, false, args[3]);
        } else if ("random".equals(provider)) {
            // /skin set random [<cape> [<variant>]] [<targets...>]: the cape
            // flag and variant are optional and parsed to match the tab
            // completion cascade (bool, then variant, then targets).
            boolean cape = args.length >= 3 && "true".equalsIgnoreCase(args[2]);
            SkinVariant variant = parseRandomVariant(args);
            Collection<EntityPlayerMP> targets = parseTargets(sender, args, 4);
            String node = singleSelf(targets, sender)
                ? ForgePermissionService.SKIN_NODE
                : ForgePermissionService.SKIN_OTHER_NODE;
            if (!checkPermission(sender, node, 2)) {
                return;
            }
            SkinAction.apply(targets, sender, SkinActionType.random, variant, cape, null);
        } else {
            sender.addChatMessage(new ChatComponentText(PREFIX + "Usage: /skin set <mojang|web|random>"));
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

    /**
     * /skin metrics [json|players|cleanup|reset]. View commands need
     * everlastingskins.command.metrics; cleanup/reset additionally need
     * everlastingskins.command.metrics.reset. Console senders are allowed.
     */
    private void doMetrics(ICommandSender sender, String[] args) {
        String sub = args.length < 2 ? "human" : args[1];
        if ("json".equals(sub)) {
            if (!checkMetricsPermission(sender)) {
                return;
            }
            sender.addChatMessage(new ChatComponentText(PREFIX + MetricsFormat.json(SkinMetrics.INSTANCE.snapshot())));
        } else if ("players".equals(sub)) {
            if (!checkMetricsPermission(sender)) {
                return;
            }
            StringBuilder sb = new StringBuilder(PREFIX + "Top players by skin refresh:");
            int rank = 0;
            for (Map.Entry<UUID, PlayerSnapshot> e : SkinMetrics.INSTANCE.topPlayers(10)) {
                sb.append("\n  ").append(++rank).append(". ")
                    .append(e.getKey()).append(" — ")
                    .append(e.getValue().refreshCount()).append(" refreshes");
            }
            if (rank == 0) {
                sb.append("\n  No refreshes recorded yet");
            }
            sender.addChatMessage(new ChatComponentText(sb.toString()));
        } else if ("cleanup".equals(sub)) {
            if (!checkMetricsResetPermission(sender)) {
                return;
            }
            int removed = SkinMetrics.INSTANCE.cleanupStalePlayers(30L * 24 * 60 * 60 * 1000);
            sender.addChatMessage(new ChatComponentText(PREFIX + "Removed " + removed + " stale player metric records"));
        } else if ("reset".equals(sub)) {
            if (!checkMetricsResetPermission(sender)) {
                return;
            }
            SkinMetrics.INSTANCE.reset();
            sender.addChatMessage(new ChatComponentText(PREFIX + "Metrics reset"));
        } else {
            if (!checkMetricsPermission(sender)) {
                return;
            }
            sender.addChatMessage(new ChatComponentText(PREFIX + MetricsFormat.human(SkinMetrics.INSTANCE.snapshot())));
        }
    }

    private boolean checkMetricsPermission(ICommandSender sender) {
        return checkPermission(sender, ForgePermissionService.METRICS_NODE, 2);
    }

    private boolean checkMetricsResetPermission(ICommandSender sender) {
        return checkPermission(sender, ForgePermissionService.METRICS_RESET_NODE, 2);
    }

    /**
     * Node-gated permission check: console senders pass, players are checked
     * through {@link PermissionServiceManager} against the lane's backend
     * with the given required op level.
     */
    private boolean checkPermission(ICommandSender sender, String node, int requiredLevel) {
        if (sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender;
            if (!PermissionServiceManager.hasPermission(SkinRestorer.profileIdOf(player), requiredLevel, node)) {
                sender.addChatMessage(new ChatComponentText(PREFIX + "You do not have permission to use this command"));
                return false;
            }
        }
        return true;
    }

    /** Permission probe for tab completion (console always completes). */
    private boolean hasPermission(ICommandSender sender, String node, int requiredLevel) {
        if (!(sender instanceof EntityPlayerMP)) {
            return true;
        }
        return PermissionServiceManager.hasPermission(SkinRestorer.profileIdOf((EntityPlayerMP) sender),
            requiredLevel, node);
    }

    private boolean canTargetOthers(ICommandSender sender) {
        return hasPermission(sender, ForgePermissionService.SKIN_OTHER_NODE, 2);
    }

    /**
     * Online player names from the 1.8.9 {@link ServerConfigurationManager}
     * surface (getAllUsernames — the getOnlinePlayerNames rename is 1.12).
     * Empty when no server context is available (unit tests, pre-boot).
     */
    private static List<String> onlinePlayerNames() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return Collections.emptyList();
        }
        String[] names = server.getConfigurationManager().getAllUsernames();
        return names != null ? Arrays.asList(names) : Collections.emptyList();
    }

    /**
     * Targets after the provider arguments, or the sender itself when no
     * targets are given. Multi-target use requires the .other node — the
     * permission check runs before any target is resolved, so a denied
     * sender never even looks players up.
     */
    private Collection<EntityPlayerMP> parseTargets(ICommandSender sender, String[] args, int targetIndex)
            throws CommandException {
        if (args.length > targetIndex) {
            if (!checkPermission(sender, ForgePermissionService.SKIN_OTHER_NODE, 2)) {
                return Collections.emptyList();
            }
            List<EntityPlayerMP> out = new ArrayList<>();
            for (int i = targetIndex; i < args.length; i++) {
                try {
                    out.add(CommandBase.getPlayer(sender, args[i]));
                } catch (CommandException ignored) {
                    // Unknown names are skipped, mirroring the 1.12.2 parser.
                }
            }
            return out;
        }
        try {
            return Collections.singletonList(CommandBase.getCommandSenderAsPlayer(sender));
        } catch (CommandException e) {
            sender.addChatMessage(new ChatComponentText(PREFIX + e.getMessage()));
            return Collections.emptyList();
        }
    }

    private static boolean singleSelf(Collection<EntityPlayerMP> targets, ICommandSender sender) {
        return targets.size() == 1 && targets.iterator().next() == sender;
    }

    /**
     * Restores a skin from Mojang for the stored source username (or the
     * player's own name when no source is stored) — the /skin clear
     * fallback, mirroring the 1.12.2 SkinCommand.
     */
    @Nullable
    static MojangRestoreResult tryRestoreFromMojang(MojangAPI mojangAPI, @Nullable String storedSource,
            String playerName) {
        String licensedUsername = (storedSource != null && !storedSource.trim().isEmpty())
            ? storedSource : playerName;
        CustomSkinProperty skin = mojangAPI.getSkin(licensedUsername)
            .map(MojangSkinDataResult::skinProperty)
            .filter(s -> !s.isEmpty())
            .orElse(null);
        if (skin == null) {
            return null;
        }
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
