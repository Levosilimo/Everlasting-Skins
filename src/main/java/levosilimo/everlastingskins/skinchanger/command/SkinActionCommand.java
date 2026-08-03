/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger.command;

import com.mojang.brigadier.context.CommandContext;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.integration.discordsrv.DiscordSrvHook;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.RandomMojangSkin;
import levosilimo.everlastingskins.skinchanger.SkinCommand;
import levosilimo.everlastingskins.skinchanger.SkinRefreshHandler;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.EverlastingHelpers;
import levosilimo.everlastingskins.util.I18nUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes /skin actions (set/source/clear) after the Brigadier builders have
 * validated input: permission gate, rate limit, optional stored-source skip,
 * async provider fetch, then completion handling with debounce + metrics.
 */
public final class SkinActionCommand {

    private static final String FEEDBACK_PREFIX = "§6[EverlastingSkins]§f";
    private static final ScheduledExecutorService skinCommandExecutor =
            Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    /** Per-UUID last refresh timestamps for the debounce. */
    static final ConcurrentHashMap<UUID, Long> lastRefreshByPlayer = new ConcurrentHashMap<>();
    /** Test-tunable debounce window; package-private for gametests. */
    public static volatile long debounceMillis = 100;

    /** Per-UUID last command timestamps for the cooldown rate limit. */
    static final ConcurrentHashMap<UUID, Long> lastCommandByPlayer = new ConcurrentHashMap<>();
    /** Per-UUID recent command timestamps for the per-minute window. */
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

    /** Clears per-player rate-limit state (used on logout and by tests). */
    public static void clearRateLimitState(UUID playerUuid) {
        lastCommandByPlayer.remove(playerUuid);
        commandTimestampsByPlayer.remove(playerUuid);
    }

    private SkinActionCommand() {
    }

    public static int execute(CommandContext<CommandSourceStack> context, SkinCommand.SkinActionParameters params) {
        Collection<ServerPlayer> targets = params.targets();
        SkinActionType type = params.type();
        SkinVariant variant = params.variant();
        boolean withCape = params.withCape();
        String customSource = params.customSource();
        ServerPlayer selfPlayer = context.getSource().getPlayer();
        if (selfPlayer == null) {
            context.getSource().sendFailure(I18nUtils.getLocalizedComponent("player_only", selfPlayer));
            return 0;
        }
        boolean targetingOthers = targets.stream().anyMatch(t -> !t.equals(selfPlayer));
        if (!PermissionServiceManager.hasPermission(
                PermissionContext.of(selfPlayer.getUUID(), selfPlayer),
                resolvePermissionNode(type, targetingOthers))) {
            context.getSource().sendFailure(Component.literal(FEEDBACK_PREFIX + " " + I18nUtils.formatMessage("permission_denied", selfPlayer)));
            return 0;
        }
        if (Config.RATE_LIMIT_ENABLED.get() && isRateLimited(selfPlayer, context)) {
            return 0;
        }
        for (ServerPlayer target : targets) {
            SkinMetrics.INSTANCE.recordRefreshStarted(target.getUUID());
        }
        targets.forEach(player -> {
            if (Config.TOGGLE.get()) {
                if (player == context.getSource().getEntity()) {
                    context.getSource().sendSuccess(() -> Component.literal(FEEDBACK_PREFIX + " " + I18nUtils.formatMessage("change", player)), false);
                } else {
                    player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + I18nUtils.formatMessage("change", player)));
                }
            }
        });

        long t0 = System.nanoTime();
        if (type == SkinActionType.username && storedSourceMatches(targets, customSource)) {
            for (ServerPlayer player : targets) {
                SkinMetrics.INSTANCE.recordRefreshSkippedStored(player.getUUID());
                SkinRestorer.server.execute(() -> SkinRefreshHandler.task(player));
            }
            return targets.size();
        }

        CompletableFuture<Map<UUID, CustomSkinProperty>> future = fetchSkinProperty(type, variant, withCape, customSource, targets);
        long fetchStart = System.nanoTime();
        ScheduledFuture<?> timeoutFuture = skinCommandExecutor.schedule(() -> {
            if (future.completeExceptionally(new TimeoutException("Skin fetch timeout occurred"))) {
                EverlastingSkins.logger.error(I18nUtils.get("timeout"));
                for (ServerPlayer player : targets) {
                    player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + I18nUtils.get("timeout")));
                }
            }
        }, 10000, TimeUnit.MILLISECONDS);
        future.whenComplete((fetched, throwable) -> {
            timeoutFuture.cancel(false);
            handleCompletion(fetched, throwable, targets, context, type, customSource, t0, fetchStart);
        });
        return targets.size();
    }

    /** A5: stored skin's source already matches the request, skip the fetch. */
    private static boolean storedSourceMatches(Collection<ServerPlayer> targets, @Nullable String customSource) {
        return targets.stream().allMatch(player -> {
            CustomSkinProperty stored = SkinRestorer.getSkinStorage().getSkin(player.getUUID());
            return stored != null && Objects.equals(stored.getSource(), customSource);
        });
    }

    private static CompletableFuture<Map<UUID, CustomSkinProperty>> fetchSkinProperty(SkinActionType type, SkinVariant variant,
                                                                           boolean withCape, String customSource,
                                                                           Collection<ServerPlayer> targets) {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, CustomSkinProperty> fetched = new HashMap<>();
            try {
                switch (type) {
                    case clear:
                        // Each target restores from its OWN stored source — the
                        // first target's Mojang skin must not leak to the others.
                        for (ServerPlayer t : targets) {
                            String storedSource = SkinRestorer.getSkinStorage().getSource(t.getUUID());
                            SkinRefreshHandler.MojangRestoreResult restore = SkinRefreshHandler.tryRestoreFromMojang(
                                    SkinCommand.getMojangAPI(), storedSource, t.getGameProfile().getName());
                            fetched.put(t.getUUID(), restore != null ? restore.skin : null);
                        }
                        break;
                    case url: {
                        CustomSkinProperty skinProperty;
                        String sanitized = EverlastingHelpers.sanitizeSkinInput(customSource);
                        if (!sanitized.equals(customSource)) {
                            skinProperty = SkinCommand.getMojangAPI().getSkin(sanitized)
                                    .map(MojangSkinDataResult::skinProperty).orElse(null);
                        } else {
                            skinProperty = SkinCommand.getMineSkinAPI().genSkin(customSource, variant).property();
                        }
                        for (ServerPlayer t : targets) {
                            fetched.put(t.getUUID(), skinProperty);
                        }
                        break;
                    }
                    case username: {
                        CustomSkinProperty skinProperty = SkinCommand.getMojangAPI().getSkin(customSource)
                                .map(MojangSkinDataResult::skinProperty).orElse(null);
                        for (ServerPlayer t : targets) {
                            fetched.put(t.getUUID(), skinProperty);
                        }
                        break;
                    }
                    case random: {
                        CustomSkinProperty skinProperty = SkinCommand.getMojangAPI()
                                .getSkin(Objects.requireNonNull(RandomMojangSkin.randomUsername(withCape, variant)))
                                .map(MojangSkinDataResult::skinProperty).orElse(null);
                        for (ServerPlayer t : targets) {
                            fetched.put(t.getUUID(), skinProperty);
                        }
                        break;
                    }
                    case NEW: {
                        CustomSkinProperty skinProperty = SkinCommand.getMojangAPI()
                                .getSkin(Objects.requireNonNull(RandomMojangSkin.newUsername(variant)))
                                .map(MojangSkinDataResult::skinProperty).orElse(null);
                        for (ServerPlayer t : targets) {
                            fetched.put(t.getUUID(), skinProperty);
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
            return fetched;
        }, skinCommandExecutor);
    }

    private static void handleCompletion(Map<UUID, CustomSkinProperty> fetched, Throwable throwable,
                                         Collection<ServerPlayer> targets, CommandContext<CommandSourceStack> context,
                                         SkinActionType type, String customSource, long t0, long fetchStart) {
        skinCompletionsProcessed++;
        if (throwable != null) {
            EverlastingSkins.logger.error("Skin process error occurred");
            for (ServerPlayer player : targets) {
                if (throwable instanceof TimeoutException) {
                    SkinMetrics.INSTANCE.recordTimedOut(player.getUUID());
                } else {
                    SkinMetrics.INSTANCE.recordRefreshFailed(player.getUUID());
                }
                player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + I18nUtils.formatMessage("error", player)));
            }
            return;
        }
        boolean isClear = type == SkinActionType.clear;
        long fetchNanos = System.nanoTime() - fetchStart;
        for (ServerPlayer player : targets) {
            UUID uuid = player.getUUID();
            CustomSkinProperty skinProperty = fetched != null ? fetched.get(uuid) : null;
            if (skinProperty == null && !isClear) {
                String reason = SkinRefreshHandler.deriveReason(type, customSource);
                EverlastingSkins.logger.warn("Skin provider returned no result: {}", reason);
                SkinMetrics.INSTANCE.recordRefreshFailed(uuid);
                player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + reason));
                continue;
            }
            if (isClear && skinProperty == null) {
                EverlastingSkins.logger.info("Skin cleared for player {} — no Mojang profile found", player.getGameProfile().getName());
                SkinRestorer.getSkinStorage().setSkin(uuid, null);
                if (Config.TOGGLE.get()) {
                    player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + I18nUtils.formatMessage("cleared_no_profile", player)));
                }
                continue;
            }
            boolean isRestore = isClear && skinProperty != null;
            if (isSkinUnchanged(uuid, skinProperty)) {
                EverlastingSkins.logger.debug("SKIN_REFRESH skipped: identical skin for {}", uuid);
                SkinMetrics.INSTANCE.recordRefreshSkipped(uuid);
                continue;
            }
            // The debounce gates persistence as well as the profile refresh:
            // storing a skin whose refresh was skipped would leave the stored
            // source/skin different from the applied GameProfile.
            long now = System.currentTimeMillis();
            Long last = lastRefreshByPlayer.get(uuid);
            long window = debounceMillis > 0 ? debounceMillis : Config.DEBOUNCE_MILLIS.get();
            if (last != null && now - last < window) {
                EverlastingSkins.logger.debug("SKIN_REFRESH debounced for {}", uuid);
                SkinMetrics.INSTANCE.recordRefreshDebounced(uuid);
                continue;
            }
            lastRefreshByPlayer.put(uuid, now);
            SkinRestorer.getSkinStorage().setSkin(uuid, skinProperty);
            if (Config.TOGGLE.get()) {
                String msg = isRestore
                    ? I18nUtils.formatMessage("restored_from", player, skinProperty.getSource())
                    : I18nUtils.formatMessage("fulfilled", player);
                if (player == context.getSource().getEntity()) {
                    context.getSource().sendSuccess(() -> Component.literal(FEEDBACK_PREFIX + " " + msg), false);
                } else {
                    player.sendSystemMessage(Component.literal(FEEDBACK_PREFIX + " " + msg));
                }
            }
            SkinMetrics.INSTANCE.recordRefreshCompleted(uuid, t0, fetchNanos, 0, 0);
            SkinRestorer.server.execute(() -> SkinRefreshHandler.task(player));
            try {
                DiscordSrvHook.announceSkinChange(player, customSource);
            } catch (Exception e) {
                EverlastingSkins.logger.warn("Failed to announce skin change to DiscordSRV", e);
            }
        }
    }

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

    /** Per-player cooldown plus a sliding per-minute window. */
    private static boolean isRateLimited(ServerPlayer player, CommandContext<CommandSourceStack> context) {
        UUID playerUuid = player.getUUID();
        if (player != null && PermissionServiceManager.hasPermission(
                PermissionContext.of(player.getUUID(), player),
                "everlastingskins.bypass.cooldown")) {
            return false;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = Config.COOLDOWN_SECONDS.get() * 1000L;
        long lastCommand = lastCommandByPlayer.getOrDefault(playerUuid, 0L);
        long elapsed = now - lastCommand;
        if (lastCommand > 0 && elapsed < cooldownMs) {
            SkinMetrics.INSTANCE.recordRateLimited(playerUuid);
            context.getSource().sendFailure(Component.literal(
                    I18nUtils.formatMessage("cooldown", player, (cooldownMs - elapsed) / 1000)));
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
                        I18nUtils.formatMessage("rate_limited", player)));
                return true;
            }
            window.addLast(now);
        }
        lastCommandByPlayer.put(playerUuid, now);
        return false;
    }
}
