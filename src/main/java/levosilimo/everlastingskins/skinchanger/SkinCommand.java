package levosilimo.everlastingskins.skinchanger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.integration.discordsrv.DiscordSrvHook;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.EverlastingHelpers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.command.EnumArgument;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

public class SkinCommand {

    private static final ScheduledExecutorService skinCommandExecutor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    private static final String FEEDBACK_PREFIX = "§6[EverlastingSkins]§f";

    /** Per-UUID last refresh timestamps for the 100ms debounce (rank 1). */
    static final ConcurrentHashMap<UUID, Long> lastRefreshByPlayer = new ConcurrentHashMap<>();
    /** Test-tunable debounce window; package-private for gametests. */
    public static volatile long debounceMillis = 100;

    /** Per-UUID last command timestamps for the cooldown rate limit (rank 4). */
    static final ConcurrentHashMap<UUID, Long> lastCommandByPlayer = new ConcurrentHashMap<>();
    /** Per-UUID recent command timestamps for the per-minute window (rank 4). */
    static final ConcurrentHashMap<UUID, ArrayDeque<Long>> commandTimestampsByPlayer = new ConcurrentHashMap<>();

    /** Test-visible count of handleSkinCompletion invocations. */
    public static volatile long skinCompletionsProcessed = 0;

    public static void resetSkinCompletionsProcessed() {
        skinCompletionsProcessed = 0;
    }

    public static long getSkinCompletionsProcessed() {
        return skinCompletionsProcessed;
    }

    public static ConcurrentHashMap<UUID, Long> getLastRefreshByPlayer() {
        return lastRefreshByPlayer;
    }

    public static ConcurrentHashMap<UUID, Long> getLastCommandByPlayer() {
        return lastCommandByPlayer;
    }

    private static MineSkinAPI mineSkinAPIInstance;
    private static MojangAPI mojangAPIInstance;

    public static MineSkinAPI getMineSkinAPI() {
        if (mineSkinAPIInstance == null) {
            mineSkinAPIInstance = new MineSkinApiHttpImpl();
        }
        return mineSkinAPIInstance;
    }

    public static MojangAPI getMojangAPI() {
        if (mojangAPIInstance == null) {
            mojangAPIInstance = new MojangApiHttpImpl();
        }
        return mojangAPIInstance;
    }

    /* package-private for tests */
    static void resetAPIs() {
        mineSkinAPIInstance = null;
        mojangAPIInstance = null;
    }

    private record SkinActionParameters(
            Collection<ServerPlayer> targets,
            SkinActionType type,
            SkinVariant variant,
            boolean withCape,
            @Nullable String customSource
    ) {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> skinCommand = Commands.literal("skin")
                .then(buildSetSubcommand())
                .then(buildSourceSubcommand())
                .then(buildClearSubcommand())
                .then(buildMetricsSubcommand());
        dispatcher.register(skinCommand);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildMetricsSubcommand() {
        return Commands.literal("metrics")
                .executes(context -> metricsAction(context, false))
                .then(Commands.literal("json")
                        .executes(context -> metricsAction(context, true)))
                .then(Commands.literal("players")
                        .executes(MetricsActions::players))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .executes(MetricsActions::reset));
    }

    private static final class MetricsActions {
        private static int players(CommandContext<CommandSourceStack> context) {
            ServerPlayer player = context.getSource().getPlayer();
            if (player == null || !hasMetricsPermission(context)) return 0;
            StringBuilder sb = new StringBuilder(FEEDBACK_PREFIX + " Top players by refresh count:");
            int rank = 0;
            for (Map.Entry<UUID, SkinMetrics.PlayerSnapshot> e : SkinMetrics.INSTANCE.topPlayers(10)) {
                sb.append("\n  ").append(++rank).append(". ")
                        .append(e.getKey()).append(" — ")
                        .append(e.getValue().refreshCount()).append(" refreshes");
            }
            if (rank == 0) sb.append("\n  (no refreshes recorded)");
            context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
            return 1;
        }

        private static int reset(CommandContext<CommandSourceStack> context) {
            if (!hasMetricsResetPermission(context)) return 0;
            SkinMetrics.INSTANCE.reset();
            context.getSource().sendSuccess(() -> Component.literal(FEEDBACK_PREFIX + " Metrics reset"), false);
            return 1;
        }
    }

    private static boolean hasMetricsResetPermission(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return false;
        PermissionContext ctx = PermissionContext.of(player.getUUID(), player.hasPermissions(2));
        boolean allowed = PermissionServiceManager.hasPermission(ctx, "everlastingskins.command.metrics.reset");
        if (!allowed) {
            context.getSource().sendFailure(Component.literal(FEEDBACK_PREFIX + " Permission denied"));
        }
        return allowed;
    }

    private static boolean hasMetricsPermission(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return false;
        PermissionContext ctx = PermissionContext.of(player.getUUID(), player.hasPermissions(2));
        boolean allowed = PermissionServiceManager.hasPermission(ctx, "everlastingskins.command.metrics");
        if (!allowed) {
            context.getSource().sendFailure(Component.literal(FEEDBACK_PREFIX + " Permission denied"));
        }
        return allowed;
    }

    private static int metricsAction(CommandContext<CommandSourceStack> context, boolean asJson) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Player only command"));
            return 0;
        }
        if (!hasMetricsPermission(context)) return 0;
        SkinMetrics.Snapshot snapshot = SkinMetrics.INSTANCE.snapshot();
        String output = asJson ? SkinMetrics.INSTANCE.formatJson(snapshot) : SkinMetrics.INSTANCE.formatHuman(snapshot);
        context.getSource().sendSuccess(() -> Component.literal(output), false);
        return 1;
    }

    private static boolean canTargetOthers(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return false;
        PermissionContext ctx = PermissionContext.of(player.getUUID(), player.hasPermissions(2));
        return PermissionServiceManager.hasPermission(ctx, "everlastingskins.command.skin.other");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSourceSubcommand() {
        return Commands.literal("source")
                .executes(context -> sourceAction(context, context.getSource().getPlayer()))
                .then(Commands.argument("target", EntityArgument.player())
                        .requires(SkinCommand::canTargetOthers)
                        .executes(context -> sourceAction(context, EntityArgument.getPlayer(context, "target")))
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildClearSubcommand() {
        return Commands.literal("clear")
                .executes(context -> skinAction(context, new SkinActionParameters(
                        Collections.singleton(context.getSource().getPlayer()),
                        SkinActionType.clear, SkinVariant.ALL, false, null
                )))
                .then(Commands.argument("targets", EntityArgument.players()).requires(SkinCommand::canTargetOthers)
                        .executes(context -> skinAction(context, new SkinActionParameters(
                                EntityArgument.getPlayers(context, "targets"),
                                SkinActionType.clear, SkinVariant.ALL, false, null
                        )))
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSetSubcommand() {
        return Commands.literal("set")
                // /skin set mojang
                .then(Commands.literal("mojang")
                        .then(Commands.argument("skin_name", StringArgumentType.word())
                                .executes(context -> skinAction(context, new SkinActionParameters(
                                        Collections.singleton(context.getSource().getPlayer()),
                                        SkinActionType.username, SkinVariant.ALL, false,
                                        StringArgumentType.getString(context, "skin_name")
                                )))
                                .then(Commands.argument("targets", EntityArgument.players()).requires(SkinCommand::canTargetOthers)
                                        .executes(context -> skinAction(context, new SkinActionParameters(
                                                EntityArgument.getPlayers(context, "targets"),
                                                SkinActionType.username, SkinVariant.ALL, false,
                                                StringArgumentType.getString(context, "skin_name")
                                        )))
                                )
                        )
                )
                // /skin set web
                .then(Commands.literal("web")
                        .then(Commands.literal("classic")
                                .then(Commands.argument("url", StringArgumentType.string())
                                        .executes(context -> skinAction(context, new SkinActionParameters(
                                                Collections.singleton(context.getSource().getPlayer()),
                                                SkinActionType.url, SkinVariant.CLASSIC, false,
                                                StringArgumentType.getString(context, "url")
                                        )))
                                        .then(Commands.argument("targets", EntityArgument.players()).requires(SkinCommand::canTargetOthers)
                                                .executes(context -> skinAction(context, new SkinActionParameters(
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.url, SkinVariant.CLASSIC, true,
                                                        StringArgumentType.getString(context, "url")
                                                )))
                                        )
                                )
                        )
                        .then(Commands.literal("slim")
                                .then(Commands.argument("url", StringArgumentType.string())
                                        .executes(context -> skinAction(context, new SkinActionParameters(
                                                Collections.singleton(context.getSource().getPlayer()),
                                                SkinActionType.url, SkinVariant.SLIM, false,
                                                StringArgumentType.getString(context, "url")
                                        )))
                                        .then(Commands.argument("targets", EntityArgument.players()).requires(SkinCommand::canTargetOthers)
                                                .executes(context -> skinAction(context, new SkinActionParameters(
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.url, SkinVariant.SLIM, true,
                                                        StringArgumentType.getString(context, "url")
                                                )))
                                        )
                                )

                        )
                )
                // /skin set random
                .then(Commands.literal("random")
                        .executes(context -> skinAction(context, new SkinActionParameters(
                                Collections.singleton(context.getSource().getPlayer()),
                                SkinActionType.random, SkinVariant.ALL, false, null
                        )))
                        .then(Commands.argument("cape", BoolArgumentType.bool())
                                .executes(context -> skinAction(context, new SkinActionParameters(
                                        Collections.singleton(context.getSource().getPlayer()),
                                        SkinActionType.random, SkinVariant.ALL, BoolArgumentType.getBool(context, "cape"), null
                                )))
                                .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                        .executes(context -> skinAction(context, new SkinActionParameters(
                                                Collections.singleton(context.getSource().getPlayer()),
                                                SkinActionType.random, context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                        )))
                                )
                        )
                        .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                .executes(context -> skinAction(context, new SkinActionParameters(
                                        Collections.singleton(context.getSource().getPlayer()),
                                        SkinActionType.random, context.getArgument("skin variant", SkinVariant.class), false, null
                                )))
                                .then(Commands.argument("cape", BoolArgumentType.bool())
                                        .executes(context -> skinAction(context, new SkinActionParameters(
                                                Collections.singleton(context.getSource().getPlayer()),
                                                SkinActionType.random, context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                        )))
                                )
                        )
                        .then(Commands.argument("targets", EntityArgument.players()).requires(SkinCommand::canTargetOthers)
                                .executes(context -> skinAction(context, new SkinActionParameters(
                                        EntityArgument.getPlayers(context, "targets"),
                                        SkinActionType.random, SkinVariant.ALL, false, null
                                )))
                                .then(Commands.argument("cape", BoolArgumentType.bool())
                                        .executes(context -> skinAction(context, new SkinActionParameters(
                                                EntityArgument.getPlayers(context, "targets"),
                                                SkinActionType.random, SkinVariant.ALL, BoolArgumentType.getBool(context, "cape"), null
                                        )))
                                        .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                                .executes(context -> skinAction(context, new SkinActionParameters(
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.random, context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                                )))
                                        )
                                )
                                .then(Commands.argument("skin variant", EnumArgument.enumArgument(SkinVariant.class))
                                        .executes(context -> skinAction(context, new SkinActionParameters(
                                                EntityArgument.getPlayers(context, "targets"),
                                                SkinActionType.random, context.getArgument("skin variant", SkinVariant.class), false, null
                                        )))
                                        .then(Commands.argument("cape", BoolArgumentType.bool())
                                                .executes(context -> skinAction(context, new SkinActionParameters(
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        SkinActionType.random, context.getArgument("skin variant", SkinVariant.class), BoolArgumentType.getBool(context, "cape"), null
                                                )))
                                        )
                                )
                        )
                );
    }

    private static int sourceAction(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        UUID uuid = target.getUUID();
        if (SkinRestorer.getSkinStorage().hasDefaultSkin(uuid)) {
            context.getSource().sendSuccess(() -> Component.literal(FEEDBACK_PREFIX + " " + target.getGameProfile().getName()), false);
            return 1;
        }
        String source = SkinRestorer.getSkinStorage().getSource(uuid);
        MutableComponent message = source != null
            ? Component.literal(FEEDBACK_PREFIX + " " + source)
            : Component.literal(FEEDBACK_PREFIX + " No source available");
        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static int skinAction(CommandContext<CommandSourceStack> context, SkinActionParameters params) {
        ServerPlayer selfPlayer = context.getSource().getPlayer();
        if (selfPlayer == null) {
            context.getSource().sendFailure(Component.literal("Player only command"));
            return 0;
        }

        boolean targetingOthers = params.targets().stream().anyMatch(t -> !t.equals(selfPlayer));
        String requiredNode = resolvePermissionNode(params.type(), targetingOthers);
        PermissionContext ctx = PermissionContext.of(selfPlayer.getUUID(), selfPlayer.hasPermissions(2));
        if (!PermissionServiceManager.hasPermission(ctx, requiredNode)) {
            context.getSource().sendFailure(Component.literal(FEEDBACK_PREFIX + " Permission denied"));
            return 0;
        }

        if (Config.RATE_LIMIT_ENABLED.get()) {
            if (isRateLimited(selfPlayer.getUUID(), context)) {
                return 0;
            }
        }

        Collection<ServerPlayer> targets = params.targets();
        SkinActionType type = params.type();
        SkinVariant variant = params.variant();
        boolean withCape = params.withCape();
        String customSource = params.customSource();

        for (ServerPlayer target : targets) {
            SkinMetrics.INSTANCE.recordRefreshStarted(target.getUUID());
        }

        targets.forEach(player -> {
                if(Config.TOGGLE.get()) {
                    if(player == context.getSource().getEntity()) context.getSource().sendSuccess(() -> Component.literal(FEEDBACK_PREFIX + " " + SkinRefreshHandler.getLocalizedString("change")), false);
                    else player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + SkinRefreshHandler.getLocalizedString("change")));
                }
        });

        long t0 = System.nanoTime();
        CompletableFuture<CustomSkinProperty> skinPropertyCompletableFuture = fetchSkinProperty(type, variant, withCape, customSource, targets);
        long fetchStart = System.nanoTime();

        ScheduledFuture<?> timeoutFuture = skinCommandExecutor.schedule(() -> {
            if(skinPropertyCompletableFuture.completeExceptionally(new TimeoutException("Skin fetch timeout occurred"))) {
                EverlastingSkins.logger.error(SkinRefreshHandler.getLocalizedString("timeout"));
                for(ServerPlayer player: targets) player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + SkinRefreshHandler.getLocalizedString("timeout")));
            }
        }, 10000, TimeUnit.MILLISECONDS);

        skinPropertyCompletableFuture.whenComplete((skinProperty, throwable) -> {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }
            handleSkinCompletion(skinProperty, throwable, targets, context, type, customSource, t0, fetchStart);
        });

        return targets.size();
    }

    private static CompletableFuture<CustomSkinProperty> fetchSkinProperty(SkinActionType type, SkinVariant variant, boolean withCape, String customSource, Collection<ServerPlayer> targets) {
        return CompletableFuture.supplyAsync(() -> {
            CustomSkinProperty skinProperty = null;
            switch (type) {
                case clear: {
                    ServerPlayer first = targets.stream().findFirst().get();
                    String storedSrc = SkinRestorer.getSkinStorage().getSource(first.getUUID());
                    String pName = first.getGameProfile().getName();
                    SkinRefreshHandler.MojangRestoreResult restore = SkinRefreshHandler.tryRestoreFromMojang(getMojangAPI(), storedSrc, pName);
                    skinProperty = restore != null ? restore.skin : null;
                    break;
                }
                case url: {
                    String sanitized = EverlastingHelpers.sanitizeSkinInput(customSource);
                    if (!sanitized.equals(customSource)) {
                        skinProperty = getMojangAPI().getSkin(sanitized)
                                .map(MojangSkinDataResult::skinProperty).orElse(null);
                    } else {
                        skinProperty = getMineSkinAPI().genSkin(customSource, variant).property();
                    }
                    break;
                }
                case username:
                    skinProperty = getMojangAPI().getSkin(customSource)
                            .map(MojangSkinDataResult::skinProperty).orElse(null);
                    break;
                case random:
                    try {
                        skinProperty = getMojangAPI().getSkin(Objects.requireNonNull(RandomMojangSkin.randomUsername(withCape, variant)))
                                .map(MojangSkinDataResult::skinProperty).orElse(null);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                    break;
                case NEW:
                    try {
                        skinProperty = getMojangAPI().getSkin(Objects.requireNonNull(RandomMojangSkin.newUsername(variant)))
                                .map(MojangSkinDataResult::skinProperty).orElse(null);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                    break;
            }
            return skinProperty;
        }, skinCommandExecutor);
    }

    private static void handleSkinCompletion(CustomSkinProperty skinProperty, Throwable throwable,
                                             Collection<ServerPlayer> targets, CommandContext<CommandSourceStack> context,
                                             SkinActionType type, String customSource, long t0, long fetchStart) {
        skinCompletionsProcessed++;
        if (throwable != null) {
            EverlastingSkins.logger.error("Skin process error occurred");
            for (ServerPlayer player : targets) {
                SkinMetrics.INSTANCE.recordRefreshFailed(player.getUUID());
                player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + SkinRefreshHandler.getLocalizedString("error")));
            }
            return;
        }
        boolean isClear = type == SkinActionType.clear;
        if (skinProperty == null && !isClear) {
            String reason = SkinRefreshHandler.deriveReason(type, customSource);
            EverlastingSkins.logger.warn("Skin provider returned no result: {}", reason);
            for (ServerPlayer player : targets) {
                SkinMetrics.INSTANCE.recordRefreshFailed(player.getUUID());
                player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + reason));
            }
            return;
        }
        if (isClear && skinProperty == null) {
            EverlastingSkins.logger.info("Skin cleared for player(s) — no Mojang profile found");
            for (ServerPlayer player : targets) {
                SkinRestorer.getSkinStorage().setSkin(player.getUUID(), null);
                if (Config.TOGGLE.get()) {
                    player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " Skin cleared (no Mojang profile found)"));
                }
            }
            for (ServerPlayer player : targets) {
                SkinRestorer.server.execute(() -> SkinRefreshHandler.task(player));
            }
            return;
        }
        boolean isRestore = isClear && skinProperty != null;
        long fetchNanos = System.nanoTime() - fetchStart;
        for (ServerPlayer player : targets) {
            UUID uuid = player.getUUID();
            if (isSkinUnchanged(uuid, skinProperty)) {
                EverlastingSkins.logger.debug("SKIN_REFRESH skipped: identical skin for {}", uuid);
                SkinMetrics.INSTANCE.recordRefreshSkipped(uuid);
                continue;
            }
            SkinRestorer.getSkinStorage().setSkin(uuid, skinProperty);
            if (Config.TOGGLE.get()) {
                String msg = isRestore
                    ? "Skin restored from " + skinProperty.getSource()
                    : SkinRefreshHandler.getLocalizedString("fulfilled");
                if (player == context.getSource().getEntity()) {
                    context.getSource().sendSuccess(() -> Component.literal(FEEDBACK_PREFIX + " " + msg), false);
                } else {
                    player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + msg));
                }
            }
            long now = System.currentTimeMillis();
            Long last = lastRefreshByPlayer.get(uuid);
            if (last != null && now - last < debounceMillis) {
                EverlastingSkins.logger.debug("SKIN_REFRESH debounced for {}", uuid);
                SkinMetrics.INSTANCE.recordRefreshDebounced(uuid);
                continue;
            }
            lastRefreshByPlayer.put(uuid, now);
            SkinMetrics.INSTANCE.recordRefreshCompleted(uuid, t0, fetchNanos, 0, 0);
            SkinRestorer.server.execute(() -> SkinRefreshHandler.task(player));
        }
        for (ServerPlayer player : targets) {
            try {
                DiscordSrvHook.announceSkinChange(player, customSource);
            } catch (Exception e) {
                EverlastingSkins.logger.warn("Failed to announce skin change to DiscordSRV", e);
            }
        }
    }

    /**
     * Rank 1: returns true when the fetched skin equals the currently stored
     * one (same texture value and same source), so the refresh can be skipped.
     */
    private static boolean isSkinUnchanged(UUID uuid, @Nullable CustomSkinProperty newSkin) {
        if (newSkin == null) return false;
        CustomSkinProperty stored = SkinRestorer.getSkinStorage().getSkin(uuid);
        if (stored == null) return false;
        if (!newSkin.getOriginalProperty().value().equals(stored.getOriginalProperty().value())) {
            return false;
        }
        return Objects.equals(newSkin.getSource(), stored.getSource());
    }

    private static String resolvePermissionNode(SkinActionType type, boolean targetingOthers) {
        if (targetingOthers) return "everlastingskins.command.skin.other";
        return switch (type) {
            case clear -> "everlastingskins.command.skin.clear";
            case url -> "everlastingskins.command.skin.url";
            default -> "everlastingskins.command.skin";
        };
    }

    /**
     * Rank 4 rate limiting: per-player cooldown plus a sliding per-minute
     * window. Sends failure feedback and returns true when the command should
     * be rejected.
     */
    private static boolean isRateLimited(UUID playerUuid, CommandContext<CommandSourceStack> context) {
        long now = System.currentTimeMillis();

        long cooldownMs = Config.COOLDOWN_SECONDS.get() * 1000L;
        long lastCommand = lastCommandByPlayer.getOrDefault(playerUuid, 0L);
        long elapsed = now - lastCommand;
        if (lastCommand > 0 && elapsed < cooldownMs) {
            SkinMetrics.INSTANCE.recordRateLimited(playerUuid);
            context.getSource().sendFailure(Component.literal(
                    "Please wait " + ((cooldownMs - elapsed) / 1000) + "s before using /skin again"));
            return true;
        }

        ArrayDeque<Long> window = commandTimestampsByPlayer.computeIfAbsent(playerUuid, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > 60_000) {
                window.pollFirst();
            }
            if (window.size() >= Config.MAX_COMMANDS_PER_MINUTE.get()) {
                SkinMetrics.INSTANCE.recordRateLimited(playerUuid);
                context.getSource().sendFailure(Component.literal(
                        "Too many /skin commands. Try again later."));
                return true;
            }
            window.addLast(now);
        }

        lastCommandByPlayer.put(playerUuid, now);
        return false;
    }

    /** Clears per-player rate-limit state (used on logout and by tests). */
    public static void clearRateLimitState(UUID playerUuid) {
        lastCommandByPlayer.remove(playerUuid);
        commandTimestampsByPlayer.remove(playerUuid);
    }

}
