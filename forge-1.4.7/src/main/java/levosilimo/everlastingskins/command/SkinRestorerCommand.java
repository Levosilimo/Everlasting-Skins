/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.command;

import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.metrics.MetricsFormat;
import levosilimo.everlastingskins.metrics.PlayerSnapshot;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.metrics.Snapshot;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.MojangAPI;
import levosilimo.everlastingskins.skinchanger.MojangApiHttpImpl;
import levosilimo.everlastingskins.skinchanger.MojangProfileCache;
import levosilimo.everlastingskins.skinchanger.RandomCapeSource;
import levosilimo.everlastingskins.skinchanger.RandomMojangSkin;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.server.MinecraftServer;
import net.minecraft.src.EntityPlayerMP;
import net.minecraft.src.ICommand;
import net.minecraft.src.ICommandSender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The 1.4.7 {@code /skin} command over the legacy {@link ICommand} surface.
 *
 * <p>1.4.7 ICommand surface (MCP 7.26a): {@code getCommandName} /
 * {@code getCommandAliases} / {@code getCommandUsage} / {@code processCommand}
 * / {@code canCommandSenderUseCommand} / {@code addTabCompletionOptions} /
 * {@code isUsernameIndex} — no {@code LiteralArgumentBuilder} (1.13+) and no
 * {@code getName} (1.8+). Sender chat goes through {@link ICommandSender#sendChatToPlayer(String)} (the plain-string form — {@code ChatMessageComponent} does not exist until 1.5).
 *
 * <p>Permission gating is delegated to {@link PermissionServiceManager} (the
 * Forge ops backend resolves the player by username-derived UUID — memory
 * #1123: UUID-only keying, never the player object). The manager fails closed
 * until a backend is registered. The gate is sender-based (mirroring
 * mc1.12.2): the console is the trusted operator, players are checked by
 * their own UUID, and targeting anyone other than the sender requires
 * {@code everlastingskins.command.skin.other} (degraded to the lane's op
 * model — op level 2 — by {@code ForgePermissionService.requiredOpLevel}).
 *
 * <p>No GameProfile on this line: targets resolve by username
 * ({@code getCommandSenderName()}) and storage keys are derived via
 * {@link SkinRestorer#uuidOf(String)}.
 *
 * <p>Command/storage parity with the modern surface (per the pre-1.8 parity
 * investigation): {@code set random [cape] [variant]} (pick + resolve +
 * store), {@code metrics [json|players|cleanup|reset]}, multi-target loops
 * with per-target permission gating, and extended tab completion. Rendering
 * parity stays era-limited — no GameProfile textures to inject and the
 * legacy skins.minecraft.net username-keyed path is dead, so {@code set web}
 * is honestly rejected with an era-limitation message.
 */
public class SkinRestorerCommand implements ICommand {

    private static final String NODE_PREFIX = "everlastingskins.command";
    private static final String NODE_SKIN = NODE_PREFIX + ".skin";
    private static final String NODE_SKIN_CLEAR = NODE_PREFIX + ".skin.clear";
    private static final String NODE_SKIN_SOURCE = NODE_PREFIX + ".skin.source";
    private static final String NODE_SKIN_RANDOM = NODE_PREFIX + ".skin.random";
    private static final String NODE_SKIN_OTHER = NODE_PREFIX + ".skin.other";
    private static final String NODE_METRICS = NODE_PREFIX + ".metrics";
    private static final String NODE_METRICS_RESET = NODE_PREFIX + ".metrics.reset";

    /** Honest rejection for {@code set web}: no MineSkin/URL pipeline exists pre-GameProfile. */
    private static final String WEB_UNSUPPORTED = "web skins are not supported on this version";

    /** Stale-window for {@code metrics cleanup}, mirroring the modern lanes (30 days). */
    private static final long CLEANUP_OLDER_THAN_MS = 30L * 24 * 60 * 60 * 1000;

    private static volatile MojangAPI mojangApi = new MojangApiHttpImpl();
    private static volatile MinecraftServer serverOverride;

    /** Seen usernames for {@code /skin set} completion, populated on successful resolves. */
    private static final MojangProfileCache seenProfiles = new MojangProfileCache();

    /**
     * Random-pick indirection: cape mode swaps the candidate source for
     * {@link RandomCapeSource} (mskins with_capes) — mirroring the modern
     * lanes' random branch. Test seam: deterministic fakes only (memory
     * #1115; no live HTTP).
     */
    interface RandomPickSource {
        String pick(boolean cape, SkinVariant variant) throws IOException;
    }

    private static final RandomPickSource DEFAULT_RANDOM_PICK_SOURCE = new RandomPickSource() {
        @Override
        public String pick(boolean cape, SkinVariant variant) throws IOException {
            return cape
                ? new RandomCapeSource().pickRandomCapeUsername()
                : RandomMojangSkin.randomUsername(false, variant);
        }
    };

    private static volatile RandomPickSource randomPickSource = DEFAULT_RANDOM_PICK_SOURCE;

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
        return "/skin <set <username|random [cape] [variant]>|clear|source|metrics [json|players|cleanup|reset]> [players...]";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        // Dispatch-time gating happens in processCommand via the permission
        // manager (fail-closed); the ICommand hook is a pre-filter only,
        // mirroring the sibling lanes.
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendChatToPlayer(getCommandUsage(sender));
            return;
        }
        String action = args[0];
        if ("metrics".equals(action)) {
            doMetrics(sender, args);
            return;
        }
        if ("clear".equals(action)) {
            doClear(sender, args);
            return;
        }
        if ("source".equals(action)) {
            doSource(sender, args);
            return;
        }
        if ("set".equals(action)) {
            doSet(sender, args);
            return;
        }
        sender.sendChatToPlayer(getCommandUsage(sender));
    }

    private void doSet(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendChatToPlayer(getCommandUsage(sender));
            return;
        }
        if ("web".equalsIgnoreCase(args[1])) {
            // No MineSkin/URL pipeline exists on this line: pre-GameProfile
            // has no client-side mechanism to render custom textures.
            sender.sendChatToPlayer(WEB_UNSUPPORTED);
            return;
        }
        if ("random".equalsIgnoreCase(args[1])) {
            setRandom(sender, args);
            return;
        }
        // /skin set <username> [players...]: apply the named skin to each target.
        List<EntityPlayerMP> targets = resolveTargets(sender, args, 2);
        if (targets == null) {
            sender.sendChatToPlayer("Player not found.");
            return;
        }
        if (!checkPermission(sender, targetsAreSelfOnly(sender, targets) ? NODE_SKIN : NODE_SKIN_OTHER)) {
            return;
        }
        for (EntityPlayerMP target : targets) {
            setSkin(sender, SkinRestorer.uuidOf(target.getCommandSenderName()), args[1]);
        }
    }

    /**
     * /skin set random [<cape> [<variant>]] [players...]: the cape flag and
     * variant are optional and parsed positionally to match the tab-completion
     * cascade (bool, then variant, then targets) — the mc1.12.2 semantics.
     */
    private void setRandom(ICommandSender sender, String[] args) {
        boolean cape = args.length >= 3 && "true".equalsIgnoreCase(args[2]);
        SkinVariant variant = parseRandomVariant(args);
        List<EntityPlayerMP> targets = resolveTargets(sender, args, 4);
        if (targets == null) {
            sender.sendChatToPlayer("Player not found.");
            return;
        }
        if (!checkPermission(sender, targetsAreSelfOnly(sender, targets) ? NODE_SKIN_RANDOM : NODE_SKIN_OTHER)) {
            return;
        }
        String username;
        try {
            username = randomPickSource.pick(cape, variant);
        } catch (IOException e) {
            sender.sendChatToPlayer("Could not pick a random skin (network error).");
            return;
        }
        if (username == null) {
            sender.sendChatToPlayer("Could not pick a random skin.");
            return;
        }
        for (EntityPlayerMP target : targets) {
            setSkin(sender, SkinRestorer.uuidOf(target.getCommandSenderName()), username);
        }
    }

    private void doClear(ICommandSender sender, String[] args) {
        List<EntityPlayerMP> targets = resolveTargets(sender, args, 1);
        if (targets == null) {
            sender.sendChatToPlayer("Player not found.");
            return;
        }
        if (!checkPermission(sender, targetsAreSelfOnly(sender, targets) ? NODE_SKIN_CLEAR : NODE_SKIN_OTHER)) {
            return;
        }
        for (EntityPlayerMP target : targets) {
            UUID uuid = SkinRestorer.uuidOf(target.getCommandSenderName());
            long startNanos = System.nanoTime();
            SkinMetrics.INSTANCE.recordRefreshStarted(uuid);
            SkinRestorer.clearSkin(uuid);
            SkinMetrics.INSTANCE.recordRefreshCompleted(uuid, startNanos, 0, 0, 0);
        }
        sender.sendChatToPlayer("Skin cleared.");
    }

    private void doSource(ICommandSender sender, String[] args) {
        List<EntityPlayerMP> targets = resolveTargets(sender, args, 1);
        if (targets == null) {
            sender.sendChatToPlayer("Player not found.");
            return;
        }
        if (!checkPermission(sender, targetsAreSelfOnly(sender, targets) ? NODE_SKIN_SOURCE : NODE_SKIN_OTHER)) {
            return;
        }
        if (targets.size() == 1) {
            String source = SkinRestorer.getSource(SkinRestorer.uuidOf(targets.get(0).getCommandSenderName()));
            sender.sendChatToPlayer(
                source != null ? "Skin source: " + source : "No custom skin stored.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (EntityPlayerMP target : targets) {
            String name = target.getCommandSenderName();
            String source = SkinRestorer.getSource(SkinRestorer.uuidOf(name));
            if (sb.length() > 0) sb.append('\n');
            sb.append(name).append(": ").append(source != null ? source : "No custom skin stored.");
        }
        sender.sendChatToPlayer(sb.toString());
    }

    /**
     * /skin metrics [json|players|cleanup|reset]. View subcommands need
     * {@code everlastingskins.command.metrics}; cleanup/reset additionally
     * need {@code everlastingskins.command.metrics.reset}. Console senders
     * are allowed (trusted operator) — mirrors the mc1.12.2 metrics surface.
     */
    private void doMetrics(ICommandSender sender, String[] args) {
        String sub = args.length < 2 ? "human" : args[1];
        if ("json".equals(sub)) {
            if (!checkMetricsPermission(sender)) return;
            sender.sendChatToPlayer(
                MetricsFormat.json(SkinMetrics.INSTANCE.snapshot()));
            return;
        }
        if ("players".equals(sub)) {
            if (!checkMetricsPermission(sender)) return;
            StringBuilder sb = new StringBuilder("EverlastingSkins top players:");
            int rank = 0;
            for (Map.Entry<UUID, PlayerSnapshot> e : SkinMetrics.INSTANCE.topPlayers(10)) {
                sb.append('\n').append("  ").append(++rank).append(". ")
                    .append(e.getKey()).append(" — ")
                    .append(e.getValue().refreshCount()).append(" refreshes");
            }
            if (rank == 0) {
                sb.append('\n').append("  No refreshes recorded.");
            }
            sender.sendChatToPlayer(sb.toString());
            return;
        }
        if ("cleanup".equals(sub)) {
            if (!checkMetricsResetPermission(sender)) return;
            int removed = SkinMetrics.INSTANCE.cleanupStalePlayers(CLEANUP_OLDER_THAN_MS);
            sender.sendChatToPlayer(
                "Removed " + removed + " stale player(s) from metrics.");
            return;
        }
        if ("reset".equals(sub)) {
            if (!checkMetricsResetPermission(sender)) return;
            SkinMetrics.INSTANCE.reset();
            sender.sendChatToPlayer("Metrics reset.");
            return;
        }
        if (!checkMetricsPermission(sender)) return;
        sender.sendChatToPlayer(
            MetricsFormat.human(SkinMetrics.INSTANCE.snapshot()));
    }

    private void setSkin(ICommandSender sender, UUID uuid, String username) {
        // 1.4.7 has no per-player permission nodes beyond the op model; the
        // vanilla Mojang lookup is the only authoritative source (no
        // MineSkin/URL generation on this legacy surface).
        MojangAPI api = mojangApi;
        if (api == null) {
            sender.sendChatToPlayer("Skin resolver unavailable.");
            return;
        }
        long startNanos = System.nanoTime();
        SkinMetrics.INSTANCE.recordRefreshStarted(uuid);
        Optional<MojangSkinDataResult> result = api.getSkin(username);
        if (!result.isPresent()) {
            SkinMetrics.INSTANCE.recordRefreshFailed(uuid);
            sender.sendChatToPlayer("Could not resolve a skin for '" + username + "'.");
            return;
        }
        CustomSkinProperty skin = result.get().skinProperty();
        seenProfiles.put(username, skin);
        SkinRestorer.applySkin(uuid, skin);
        SkinMetrics.INSTANCE.recordRefreshCompleted(uuid, startNanos, System.nanoTime() - startNanos, 0, 0);
        sender.sendChatToPlayer("Skin applied.");
    }

    /**
     * Sender-based gate (mirrors mc1.12.2): the console is the trusted
     * operator; players are checked by their own username-derived UUID
     * (memory #1123 — never the target object).
     */
    private boolean checkPermission(ICommandSender sender, String node) {
        if (!(sender instanceof EntityPlayerMP)) return true; // console
        EntityPlayerMP player = (EntityPlayerMP) sender;
        if (!PermissionServiceManager.hasPermission(
                SkinRestorer.uuidOf(player.getCommandSenderName()), 4, node)) {
            sender.sendChatToPlayer("You do not have permission to use this command.");
            return false;
        }
        return true;
    }

    private boolean checkMetricsPermission(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) return true; // console
        return checkPermission(sender, NODE_METRICS);
    }

    private boolean checkMetricsResetPermission(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) return true; // console
        return checkPermission(sender, NODE_METRICS_RESET);
    }

    /**
     * Resolves the target set: explicit names (args from {@code targetIndex})
     * are matched against the online list, skipping unresolvable names
     * (mirrors mc1.12.2's parseTargets); no explicit names resolve to the
     * sender. Returns null when nothing resolved.
     */
    private List<EntityPlayerMP> resolveTargets(ICommandSender sender, String[] args, int targetIndex) {
        MinecraftServer server = serverOverride != null ? serverOverride : MinecraftServer.getServer();
        if (server != null && server.getConfigurationManager() != null) {
            if (args.length > targetIndex) {
                List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
                for (int i = targetIndex; i < args.length; i++) {
                    for (Object o : server.getConfigurationManager().playerEntityList) {
                        if (o instanceof EntityPlayerMP) {
                            EntityPlayerMP p = (EntityPlayerMP) o;
                            if (p.getCommandSenderName().equalsIgnoreCase(args[i])) {
                                out.add(p);
                                break;
                            }
                        }
                    }
                }
                return out.isEmpty() ? null : out;
            }
            for (Object o : server.getConfigurationManager().playerEntityList) {
                if (o instanceof EntityPlayerMP && o == sender) {
                    return Collections.singletonList((EntityPlayerMP) o);
                }
            }
        }
        if (sender instanceof EntityPlayerMP) {
            return Collections.singletonList((EntityPlayerMP) sender);
        }
        return null;
    }

    private boolean targetsAreSelfOnly(ICommandSender sender, List<EntityPlayerMP> targets) {
        return targets.size() == 1
            && sender instanceof EntityPlayerMP
            && targets.get(0) == sender;
    }

    /** Variant argument of {@code /skin set random}: optional, defaults to ALL. */
    private static SkinVariant parseRandomVariant(String[] args) {
        if (args.length >= 4 && "slim".equalsIgnoreCase(args[3])) {
            return SkinVariant.SLIM;
        }
        if (args.length >= 4 && "classic".equalsIgnoreCase(args[3])) {
            return SkinVariant.CLASSIC;
        }
        return SkinVariant.ALL;
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<String>();
            if (canUse(sender, NODE_SKIN)) subcommands.add("set");
            if (canUse(sender, NODE_SKIN_CLEAR)) subcommands.add("clear");
            if (canUse(sender, NODE_SKIN_SOURCE)) subcommands.add("source");
            if (canUse(sender, NODE_METRICS)) subcommands.add("metrics");
            return filterCompletions(args[0], subcommands);
        }
        if (args.length == 2) {
            if ("metrics".equals(args[0])) {
                List<String> metricsSubs = new ArrayList<String>();
                metricsSubs.add("json");
                metricsSubs.add("players");
                if (canUse(sender, NODE_METRICS_RESET)) {
                    metricsSubs.add("cleanup");
                    metricsSubs.add("reset");
                }
                return filterCompletions(args[1], metricsSubs);
            }
            List<String> candidates = new ArrayList<String>();
            if ("set".equals(args[0])) candidates.add("random");
            addOnlinePlayerNames(candidates);
            for (String seen : seenProfiles.snapshot()) {
                if (!candidates.contains(seen)) candidates.add(seen);
            }
            return filterCompletions(args[1], candidates);
        }
        if ("set".equals(args[0]) && "random".equals(args[1])) {
            // Positional cascade mirroring mc1.12.2: cape bool, then variant,
            // then target names.
            if (args.length == 3) {
                return filterCompletions(args[2], Arrays.asList("true", "false"));
            }
            if (args.length == 4) {
                return filterCompletions(args[3], Arrays.asList("classic", "slim"));
            }
            return targetCompletions(args[args.length - 1]);
        }
        if ("set".equals(args[0]) || "clear".equals(args[0]) || "source".equals(args[0])) {
            return targetCompletions(args[args.length - 1]);
        }
        return null;
    }

    /** Online + seen-profile names for the target positions. */
    private List targetCompletions(String prefix) {
        List<String> candidates = new ArrayList<String>();
        addOnlinePlayerNames(candidates);
        for (String seen : seenProfiles.snapshot()) {
            if (!candidates.contains(seen)) candidates.add(seen);
        }
        return filterCompletions(prefix, candidates);
    }

    /** Silent permission check for completion (no denial message, unlike {@link #checkPermission}). */
    private static boolean canUse(ICommandSender sender, String node) {
        if (!(sender instanceof EntityPlayerMP)) return true;
        EntityPlayerMP player = (EntityPlayerMP) sender;
        return PermissionServiceManager.hasPermission(
            SkinRestorer.uuidOf(player.getCommandSenderName()), 4, node);
    }

    /** Online player names via the era {@code playerEntityList} walk (no getAllUsernames pre-1.7). */
    private void addOnlinePlayerNames(List<String> candidates) {
        MinecraftServer server = serverOverride != null ? serverOverride : MinecraftServer.getServer();
        if (server != null && server.getConfigurationManager() != null) {
            for (Object o : server.getConfigurationManager().playerEntityList) {
                if (o instanceof EntityPlayerMP) {
                    candidates.add(((EntityPlayerMP) o).getCommandSenderName());
                }
            }
        }
    }

    /** Case-insensitive prefix filter (pre-1.7 lanes have no CommandBase helper). */
    private static List filterCompletions(String prefix, List<String> candidates) {
        List<String> matches = new ArrayList<String>();
        for (String candidate : candidates) {
            if (candidate.regionMatches(true, 0, prefix, 0, prefix.length())) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    @Override
    public boolean isUsernameIndex(int index) {
        return index == 1;
    }

    @Override
    public int compareTo(Object o) {
        return getCommandName().compareTo(((ICommand) o).getCommandName());
    }

    /** Test seam — deterministic fakes only (memory #1115; no live HTTP). */
    static void setMojangApiForTest(MojangAPI api) {
        mojangApi = api;
    }

    /** Test seam — mirrors the mc1.12.2 SkinRestorer.setServer pattern. */
    static void setServerOverrideForTest(MinecraftServer server) {
        serverOverride = server;
    }

    /** Test seam — deterministic completion tests (memory #1115). */
    static void clearSeenProfilesForTest() {
        seenProfiles.clear();
    }

    /** Test seam — deterministic random-pick injection (null restores the live source). */
    static void setRandomPickSourceForTest(RandomPickSource source) {
        randomPickSource = source != null ? source : DEFAULT_RANDOM_PICK_SOURCE;
    }
}
