/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.GameProfile;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.forge1710.EverlastingSkins;
import levosilimo.everlastingskins.metrics.MetricsFormat;
import levosilimo.everlastingskins.metrics.PlayerSnapshot;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The 1.7.10 {@code /skin} command.
 *
 * <p>1.7.10 ICommand surface (MCP stable_12): {@code getCommandName} /
 * {@code getCommandAliases} / {@code getCommandUsage} / {@code processCommand}
 * / {@code canCommandSenderUseCommand} — there is no
 * {@code LiteralArgumentBuilder} (1.13+) and no {@code getName} (that is
 * 1.8+; 1.7.10's accessor is {@code getCommandName}). Sender chat goes
 * through {@link ICommandSender#addChatMessage}.
 *
 * <p>Command surface (parity with the 1.21 reference):
 * {@code /skin set mojang <name> [targets]},
 * {@code /skin set web <classic|slim> <url> [targets]},
 * {@code /skin set random [cape] [variant] [targets]},
 * {@code /skin clear [targets]}, {@code /skin source [target]},
 * {@code /skin metrics [json|players|cleanup|reset]}. Multi-target invocations
 * gate on {@code everlastingskins.command.skin.other} (op-2 in the vanilla
 * backend); the per-action nodes are
 * {@code everlastingskins.command.skin} / {@code .skin.clear} /
 * {@code .skin.url} / {@code .skin.source} and
 * {@code everlastingskins.command.metrics} / {@code .metrics.reset}.
 *
 * <p>Permission gating is delegated to {@link PermissionServiceManager} (the
 * Forge ops backend resolves the player by UUID — memory #1123: UUID-only
 * keying, never the player object — and maps the node's required op level
 * onto {@code canCommandSenderUseCommand}). The manager fails closed until a
 * backend is registered. Non-player senders (console) are implicitly trusted,
 * mirroring the sibling lanes.
 */
public class SkinCommand implements ICommand {

    private static final String NODE_PREFIX = "everlastingskins.command";
    private static final String NODE_SKIN = NODE_PREFIX + ".skin";
    private static final String NODE_SKIN_CLEAR = NODE_PREFIX + ".skin.clear";
    private static final String NODE_SKIN_SOURCE = NODE_PREFIX + ".skin.source";
    private static final String NODE_SKIN_URL = NODE_PREFIX + ".skin.url";
    private static final String NODE_SKIN_OTHER = NODE_PREFIX + ".skin.other";
    private static final String NODE_METRICS = NODE_PREFIX + ".metrics";
    private static final String NODE_METRICS_RESET = NODE_PREFIX + ".metrics.reset";

    private static final String[] PROVIDERS = {"mojang", "web", "random"};
    private static final String[] METRICS_SUBCOMMANDS = {"json", "players", "cleanup", "reset"};

    private static volatile MojangAPI mojangApi = new MojangApiHttpImpl();
    private static volatile MineSkinAPI mineSkinApi = new MineSkinApiHttpImpl();
    private static volatile RandomUsernameSource randomSource = new RandomUsernameSource() {
        @Override
        public String pick(boolean cape, SkinVariant variant) throws IOException {
            // Cape mode swaps the candidate source: RandomMojangSkin's
            // decoded-CAPE check only finds legacy cape holders, so
            // RandomCapeSource (mskins with_capes + Cosmetica) is used
            // instead (1.21 reference behavior).
            return cape
                ? new RandomCapeSource().pickRandomCapeUsername()
                : RandomMojangSkin.randomUsername(false, variant);
        }
    };
    private static volatile MinecraftServer serverOverride;

    /** Seen usernames for {@code /skin set mojang} completion, populated on successful resolves. */
    private static final MojangProfileCache seenProfiles = new MojangProfileCache();

    private final SkinStorageProvider provider;

    public SkinCommand(SkinStorageProvider provider) {
        this.provider = provider;
    }

    @Override
    public String getCommandName() {
        return "skin";
    }

    @Override
    public List getCommandAliases() {
        return Arrays.asList("skins", "setskin");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/skin <set <mojang|web|random>|clear|source|metrics> ...";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        // Dispatch-time gating happens in processCommand via the permission
        // manager (fail-closed); the 1.7.10 ICommand hook is a pre-filter
        // only, mirroring the sibling lanes.
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }
        String action = args[0];
        if (Boolean.getBoolean("everlastingskins.e2e")) {
            // E2E diagnostics (slice 2): the vanilla 1.7.10 server never logs
            // player commands, so the driver needs this entry marker to tell
            // "command never reached the server" apart from a fetch failure.
            EverlastingSkins.logger.info("ES_E2E_SKIN=cmd player={} action={} args={}",
                sender.getCommandSenderName(), action, java.util.Arrays.toString(args));
        }

        switch (action) {
            case "clear":
                doClear(sender, args);
                break;
            case "source":
                doSource(sender, args);
                break;
            case "set":
                doSet(sender, args);
                break;
            case "metrics":
                doMetrics(sender, args);
                break;
            default:
                sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
        }
    }

    private void doClear(ICommandSender sender, String[] args) {
        List<EntityPlayerMP> targets = parseTargets(sender, args, 1);
        if (targets == null) {
            sender.addChatMessage(new ChatComponentText("Player not found."));
            return;
        }
        if (!checkPermission(sender, permissionNodeFor(targets, sender, NODE_SKIN_CLEAR))) {
            return;
        }
        long t0 = System.nanoTime();
        for (EntityPlayerMP target : targets) {
            UUID uuid = target.getGameProfile().getId();
            SkinMetrics.INSTANCE.recordRefreshStarted(uuid);
            provider.clearSkin(target.getGameProfile(), uuid);
            SkinMetrics.INSTANCE.recordRefreshCompleted(uuid, t0, 0, 0, 0);
        }
        sender.addChatMessage(new ChatComponentText("Skin cleared."));
    }

    private void doSource(ICommandSender sender, String[] args) {
        List<EntityPlayerMP> targets = parseTargets(sender, args, 1);
        if (targets == null) {
            sender.addChatMessage(new ChatComponentText("Player not found."));
            return;
        }
        EntityPlayerMP target = targets.get(0);
        if (!checkPermission(sender, permissionNodeFor(targets, sender, NODE_SKIN_SOURCE))) {
            return;
        }
        String source = provider.getSource(target.getGameProfile().getId());
        sender.addChatMessage(new ChatComponentText(
            source != null ? "Skin source: " + source : "No custom skin stored."));
    }

    private void doSet(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }
        switch (args[1]) {
            case "mojang": {
                if (args.length < 3) {
                    sender.addChatMessage(new ChatComponentText("Usage: /skin set mojang <name> [targets]"));
                    return;
                }
                List<EntityPlayerMP> targets = parseTargets(sender, args, 3);
                if (targets == null) {
                    sender.addChatMessage(new ChatComponentText("Player not found."));
                    return;
                }
                if (!checkPermission(sender, permissionNodeFor(targets, sender, NODE_SKIN))) {
                    return;
                }
                applyMojang(sender, targets, args[2]);
                break;
            }
            case "web": {
                if (args.length < 4) {
                    sender.addChatMessage(new ChatComponentText("Usage: /skin set web <classic|slim> <url> [targets]"));
                    return;
                }
                SkinVariant variant = "slim".equalsIgnoreCase(args[2]) ? SkinVariant.SLIM : SkinVariant.CLASSIC;
                List<EntityPlayerMP> targets = parseTargets(sender, args, 4);
                if (targets == null) {
                    sender.addChatMessage(new ChatComponentText("Player not found."));
                    return;
                }
                if (!checkPermission(sender, permissionNodeFor(targets, sender, NODE_SKIN_URL))) {
                    return;
                }
                applyWeb(sender, targets, args[3], variant);
                break;
            }
            case "random": {
                // /skin set random [<cape> [<variant>]] [<targets...>]: the
                // cape flag and variant are optional and keyword-detected so
                // the flat 1.7.10 args mirror the mc1.12.2 tab-completion
                // cascade (bool, then variant, then targets).
                boolean cape = args.length >= 3 && "true".equalsIgnoreCase(args[2]);
                SkinVariant variant = parseRandomVariant(args);
                List<EntityPlayerMP> targets = parseTargets(sender, args, 4);
                if (targets == null) {
                    sender.addChatMessage(new ChatComponentText("Player not found."));
                    return;
                }
                if (!checkPermission(sender, permissionNodeFor(targets, sender, NODE_SKIN))) {
                    return;
                }
                applyRandom(sender, targets, cape, variant);
                break;
            }
            default:
                sender.addChatMessage(new ChatComponentText("Usage: /skin set <mojang|web|random>"));
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

    private void applyMojang(ICommandSender sender, List<EntityPlayerMP> targets, String username) {
        for (EntityPlayerMP target : targets) {
            SkinMetrics.INSTANCE.recordRefreshStarted(target.getGameProfile().getId());
        }
        applyMojangLookup(sender, targets, username);
    }

    private void applyMojangLookup(ICommandSender sender, List<EntityPlayerMP> targets, String username) {
        MojangAPI api = mojangApi;
        if (api == null) {
            recordRefreshFailed(targets);
            sender.addChatMessage(new ChatComponentText("Skin resolver unavailable."));
            return;
        }
        long t0 = System.nanoTime();
        long fetchStart = System.nanoTime();
        Optional<MojangSkinDataResult> result;
        try {
            result = api.getSkin(username);
        } catch (RuntimeException e) {
            recordRefreshFailed(targets);
            sender.addChatMessage(new ChatComponentText("Could not resolve a skin for '" + username + "'."));
            if (Boolean.getBoolean("everlastingskins.e2e")) {
                String player = targets.isEmpty() ? "unknown" : targets.get(0).getGameProfile().getName();
                EverlastingSkins.logger.info("ES_E2E_SKIN=fail player={} source={} reason=exception msg={}",
                    player, username, e.getMessage());
            }
            return;
        }
        long fetchNanos = System.nanoTime() - fetchStart;
        if (!result.isPresent()) {
            recordRefreshFailed(targets);
            sender.addChatMessage(new ChatComponentText("Could not resolve a skin for '" + username + "'."));
            if (Boolean.getBoolean("everlastingskins.e2e")) {
                String player = targets.isEmpty() ? "unknown" : targets.get(0).getGameProfile().getName();
                EverlastingSkins.logger.info("ES_E2E_SKIN=fail player={} source={} reason=no-skin", player, username);
            }
            return;
        }
        CustomSkinProperty skin = result.get().skinProperty();
        seenProfiles.put(username, skin);
        applyToTargets(sender, targets, skin, t0, fetchNanos, username);
    }

    private void applyWeb(ICommandSender sender, List<EntityPlayerMP> targets, String url, SkinVariant variant) {
        MineSkinAPI api = mineSkinApi;
        if (api == null) {
            recordRefreshFailed(targets);
            sender.addChatMessage(new ChatComponentText("Skin generator unavailable."));
            return;
        }
        long t0 = System.nanoTime();
        for (EntityPlayerMP target : targets) {
            SkinMetrics.INSTANCE.recordRefreshStarted(target.getGameProfile().getId());
        }
        long fetchStart = System.nanoTime();
        MineSkinResponse response;
        try {
            response = api.genSkin(url, variant);
        } catch (RuntimeException e) {
            recordRefreshFailed(targets);
            sender.addChatMessage(new ChatComponentText("Could not generate a skin from that URL."));
            return;
        }
        long fetchNanos = System.nanoTime() - fetchStart;
        if (response == null || response.property() == null || response.property().isEmpty()) {
            recordRefreshFailed(targets);
            sender.addChatMessage(new ChatComponentText("Could not generate a skin from that URL."));
            return;
        }
        applyToTargets(sender, targets, response.property(), t0, fetchNanos, url);
    }

    private void applyRandom(ICommandSender sender, List<EntityPlayerMP> targets, boolean cape, SkinVariant variant) {
        for (EntityPlayerMP target : targets) {
            SkinMetrics.INSTANCE.recordRefreshStarted(target.getGameProfile().getId());
        }
        String username;
        try {
            username = randomSource.pick(cape, variant);
        } catch (IOException e) {
            recordRefreshFailed(targets);
            sender.addChatMessage(new ChatComponentText("Could not fetch a random skin."));
            return;
        }
        if (username == null) {
            recordRefreshFailed(targets);
            sender.addChatMessage(new ChatComponentText("Could not fetch a random skin."));
            return;
        }
        applyMojangLookup(sender, targets, username);
    }

    private void applyToTargets(ICommandSender sender, List<EntityPlayerMP> targets,
                                CustomSkinProperty skin, long t0, long fetchNanos, String source) {
        for (EntityPlayerMP target : targets) {
            UUID uuid = target.getGameProfile().getId();
            provider.applySkin(target.getGameProfile(), uuid, skin);
            SkinMetrics.INSTANCE.recordRefreshCompleted(uuid, t0, fetchNanos, 0, 0);
        }
        sender.addChatMessage(new ChatComponentText("Skin applied."));
        if (Boolean.getBoolean("everlastingskins.e2e")) {
            // Sentinel for the real-client E2E (slice 2): the /skin reply is a
            // chat message only the client sees, so the E2E asserts this
            // server-log marker instead (the driver boots the server with
            // -Deverlastingskins.e2e=true).
            String player = targets.isEmpty() ? "unknown" : targets.get(0).getGameProfile().getName();
            EverlastingSkins.logger.info("ES_E2E_SKIN=ok player={} source={}", player, source);
        }
    }

    private void recordRefreshFailed(List<EntityPlayerMP> targets) {
        for (EntityPlayerMP target : targets) {
            SkinMetrics.INSTANCE.recordRefreshFailed(target.getGameProfile().getId());
        }
    }

    /**
     * /skin metrics [json|players|cleanup|reset]. View subcommands need
     * everlastingskins.command.metrics; cleanup/reset additionally need
     * everlastingskins.command.metrics.reset. Console senders are allowed.
     */
    private void doMetrics(ICommandSender sender, String[] args) {
        String sub = args.length < 2 ? "human" : args[1];
        if ("json".equals(sub)) {
            if (!checkPermission(sender, NODE_METRICS)) return;
            sender.addChatMessage(new ChatComponentText(MetricsFormat.json(SkinMetrics.INSTANCE.snapshot())));
        } else if ("players".equals(sub)) {
            if (!checkPermission(sender, NODE_METRICS)) return;
            StringBuilder sb = new StringBuilder();
            int rank = 0;
            for (Map.Entry<UUID, PlayerSnapshot> e : SkinMetrics.INSTANCE.topPlayers(10)) {
                sb.append("\n  ").append(++rank).append(". ")
                    .append(e.getKey()).append(" — ")
                    .append(e.getValue().refreshCount()).append(" refreshes");
            }
            if (rank == 0) {
                sb.append("\n  No refresh activity recorded.");
            }
            sender.addChatMessage(new ChatComponentText(sb.toString()));
        } else if ("cleanup".equals(sub)) {
            if (!checkPermission(sender, NODE_METRICS_RESET)) return;
            int removed = SkinMetrics.INSTANCE.cleanupStalePlayers(30L * 24 * 60 * 60 * 1000);
            sender.addChatMessage(new ChatComponentText("Removed " + removed + " stale player metric entries."));
        } else if ("reset".equals(sub)) {
            if (!checkPermission(sender, NODE_METRICS_RESET)) return;
            SkinMetrics.INSTANCE.reset();
            sender.addChatMessage(new ChatComponentText("Metrics reset."));
        } else {
            if (!checkPermission(sender, NODE_METRICS)) return;
            sender.addChatMessage(new ChatComponentText(MetricsFormat.human(SkinMetrics.INSTANCE.snapshot())));
        }
    }

    /**
     * Resolves the target list: explicit names from {@code targetIndex}
     * onward, or the sender themselves when no names are given. Returns null
     * when explicit targets were requested but none resolved (or the sender
     * is not a player and no target was named).
     */
    private List<EntityPlayerMP> parseTargets(ICommandSender sender, String[] args, int targetIndex) {
        if (args.length > targetIndex) {
            List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
            for (int i = targetIndex; i < args.length; i++) {
                EntityPlayerMP player = findPlayerByName(args[i]);
                if (player != null) {
                    out.add(player);
                }
            }
            return out.isEmpty() ? null : out;
        }
        return sender instanceof EntityPlayerMP
            ? Collections.singletonList((EntityPlayerMP) sender)
            : null;
    }

    private EntityPlayerMP findPlayerByName(String name) {
        MinecraftServer server = serverOverride != null ? serverOverride : MinecraftServer.getServer();
        if (server != null && server.getConfigurationManager() != null) {
            for (Object o : server.getConfigurationManager().playerEntityList) {
                if (o instanceof EntityPlayerMP) {
                    EntityPlayerMP player = (EntityPlayerMP) o;
                    if (player.getCommandSenderName().equalsIgnoreCase(name)) {
                        return player;
                    }
                }
            }
        }
        return null;
    }

    /**
     * The reference node model: targeting anyone but yourself (or a non-self
     * single target) requires {@link #NODE_SKIN_OTHER}; self-only actions use
     * the action's own node.
     */
    private static String permissionNodeFor(List<EntityPlayerMP> targets, ICommandSender sender, String selfNode) {
        boolean singleSelf = targets.size() == 1 && targets.get(0) == sender;
        return singleSelf ? selfNode : NODE_SKIN_OTHER;
    }

    private boolean checkPermission(ICommandSender sender, String node) {
        if (sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender;
            if (!PermissionServiceManager.hasPermission(player.getGameProfile().getId(), 4, node)) {
                sender.addChatMessage(new ChatComponentText("You do not have permission to use this command."));
                return false;
            }
        }
        // Non-player senders (console) are implicitly trusted.
        return true;
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<String>();
            if (canUse(sender, NODE_SKIN)) subcommands.add("set");
            if (canUse(sender, NODE_SKIN_CLEAR)) subcommands.add("clear");
            if (canUse(sender, NODE_SKIN_SOURCE)) subcommands.add("source");
            if (canUse(sender, NODE_METRICS)) subcommands.add("metrics");
            return CommandBase.getListOfStringsMatchingLastWord(
                args, subcommands.toArray(new String[subcommands.size()]));
        }
        if ("set".equals(args[0])) {
            return setTabCompletions(sender, args);
        }
        if (("clear".equals(args[0]) || "source".equals(args[0])) && args.length == 2) {
            return canUse(sender, NODE_SKIN_OTHER)
                ? CommandBase.getListOfStringsMatchingLastWord(args, onlinePlayerNames())
                : Collections.emptyList();
        }
        if ("metrics".equals(args[0]) && args.length == 2) {
            return CommandBase.getListOfStringsMatchingLastWord(args, METRICS_SUBCOMMANDS);
        }
        return null;
    }

    /** Per-position candidates under {@code /skin set ...}. */
    private List setTabCompletions(ICommandSender sender, String[] args) {
        if (args.length == 2) {
            return CommandBase.getListOfStringsMatchingLastWord(args, PROVIDERS);
        }
        if ("mojang".equals(args[1])) {
            if (args.length == 3) {
                return CommandBase.getListOfStringsMatchingLastWord(args, mojangNameCandidates());
            }
            if (args.length == 4) {
                return targetCompletions(sender, args);
            }
        }
        if ("web".equals(args[1])) {
            if (args.length == 3) {
                return CommandBase.getListOfStringsMatchingLastWord(args, "classic", "slim");
            }
            if (args.length == 4) {
                return CommandBase.getListOfStringsMatchingLastWord(args, "https://", "http://");
            }
            if (args.length == 5) {
                return targetCompletions(sender, args);
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
                return targetCompletions(sender, args);
            }
        }
        return null;
    }

    private List targetCompletions(ICommandSender sender, String[] args) {
        return canUse(sender, NODE_SKIN_OTHER)
            ? CommandBase.getListOfStringsMatchingLastWord(args, onlinePlayerNames())
            : Collections.emptyList();
    }

    private String[] mojangNameCandidates() {
        List<String> candidates = new ArrayList<String>();
        addOnlinePlayerNames(candidates);
        for (String seen : seenProfiles.snapshot()) {
            if (!candidates.contains(seen)) {
                candidates.add(seen);
            }
        }
        return candidates.toArray(new String[candidates.size()]);
    }

    /** Silent permission check for completion (no denial message, unlike {@link #checkPermission}). */
    private static boolean canUse(ICommandSender sender, String node) {
        if (!(sender instanceof EntityPlayerMP)) return true;
        EntityPlayerMP player = (EntityPlayerMP) sender;
        return PermissionServiceManager.hasPermission(
            player.getGameProfile().getId(), 4, node);
    }

    /** Online player names via the 1.7.10-era {@code getAllUsernames} (the later getOnlinePlayerNames). */
    private void addOnlinePlayerNames(List<String> candidates) {
        MinecraftServer server = serverOverride != null ? serverOverride : MinecraftServer.getServer();
        if (server != null && server.getConfigurationManager() != null) {
            String[] names = server.getConfigurationManager().getAllUsernames();
            if (names != null) {
                for (String name : names) {
                    if (!candidates.contains(name)) {
                        candidates.add(name);
                    }
                }
            }
        }
    }

    private String[] onlinePlayerNames() {
        List<String> candidates = new ArrayList<String>();
        addOnlinePlayerNames(candidates);
        return candidates.toArray(new String[candidates.size()]);
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        // Player-name argument positions: clear/source [target] (1),
        // set mojang <name> [targets] (3), set web <variant> <url> [targets] (4),
        // set random [cape] [variant] [targets] (4).
        return index == 1 || index == 3 || index == 4;
    }

    @Override
    public int compareTo(Object o) {
        return getCommandName().compareTo(((ICommand) o).getCommandName());
    }

    /** Test seam — deterministic fakes only (memory #1115; no live HTTP). */
    static void setMojangApiForTest(MojangAPI api) {
        mojangApi = api;
    }

    /** Test seam — deterministic MineSkin generator (memory #1115; no live HTTP). */
    static void setMineSkinApiForTest(MineSkinAPI api) {
        mineSkinApi = api;
    }

    /** Test seam — deterministic random-username source (memory #1115; no live HTTP). */
    static void setRandomSourceForTest(RandomUsernameSource source) {
        randomSource = source;
    }

    /** Test seam — mirrors the mc1.12.2 SkinRestorer.setServer pattern. */
    static void setServerOverrideForTest(MinecraftServer server) {
        serverOverride = server;
    }

    /** Test seam — deterministic completion tests (memory #1115). */
    static void clearSeenProfilesForTest() {
        seenProfiles.clear();
    }

    /** Random-username source behind {@code /skin set random} (seam-injectable). */
    interface RandomUsernameSource {
        /** @return a username whose skin is then resolved via Mojang. */
        String pick(boolean cape, SkinVariant variant) throws IOException;
    }
}
