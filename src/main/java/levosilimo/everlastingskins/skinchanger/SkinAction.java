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
import levosilimo.everlastingskins.integration.discordsrv.DiscordSrvHook;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.EverlastingHelpers;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Provider-fetch pipeline for /skin actions. Runs off the server thread with
 * a hard timeout so a slow Mojang/MineSkin HTTP call can never block the tick.
 */
final class SkinAction {

    static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() * 2));

    /** Per-UUID last refresh timestamps for the debounce window. */
    private static final ConcurrentHashMap<UUID, Long> lastRefreshByPlayer = new ConcurrentHashMap<>();
    /** Per-UUID last command timestamps for the cooldown rate limit. */
    private static final ConcurrentHashMap<UUID, Long> lastCommandByPlayer = new ConcurrentHashMap<>();
    /** Per-UUID recent command timestamps for the per-minute window. */
    private static final ConcurrentHashMap<UUID, ArrayDeque<Long>> commandTimestampsByPlayer = new ConcurrentHashMap<>();

    private SkinAction() {
    }

    static void apply(Collection<EntityPlayerMP> targets, ICommandSender sender,
            SkinActionType type, SkinVariant variant, boolean withCape, @Nullable String customSource) {
        if (sender instanceof EntityPlayerMP && Config.RATE_LIMIT_ENABLED
                && isRateLimited((EntityPlayerMP) sender)) {
            return;
        }
        for (EntityPlayerMP p : targets) {
            if (Config.TOGGLE) {
                p.sendMessage(new TextComponentString(SkinCommand.PREFIX + "Processing..."));
            }
        }
        if (type == SkinActionType.username && storedSourceMatches(targets, customSource)) {
            for (EntityPlayerMP p : targets) {
                SkinMetrics.INSTANCE.recordRefreshSkippedStored(p.getUniqueID());
                CustomSkinProperty stored = SkinRestorer.getSkinStorage().getSkin(p.getUniqueID());
                SkinRestorer.getServer().addScheduledTask(() -> SkinRefreshTask.task(p, stored, 0L));
            }
            return;
        }
        long[] fetchNanos = {0L};
        CompletableFuture<Map<UUID, CustomSkinProperty>> future = CompletableFuture.supplyAsync(() -> {
            Map<UUID, CustomSkinProperty> fetched = new HashMap<>();
            long fetchStartNanos = System.nanoTime();
            try {
                switch (type) {
                    case clear:
                        // Each target restores from its OWN stored source — the
                        // first target's Mojang skin must not leak to the others.
                        for (EntityPlayerMP t : targets) {
                            String storedSource = SkinRestorer.getSkinStorage().getSource(t.getUniqueID());
                            SkinCommand.MojangRestoreResult restore = SkinCommand.tryRestoreFromMojang(
                                SkinCommand.getMojangAPI(), storedSource, t.getGameProfile().getName());
                            fetched.put(t.getUniqueID(), restore != null ? restore.skin : null);
                        }
                        break;
                    case url: {
                        CustomSkinProperty sp = null;
                        String sanitized = EverlastingHelpers.sanitizeSkinInput(customSource);
                        if (!sanitized.equals(customSource)) {
                            sp = SkinCommand.getMojangAPI().getSkin(sanitized)
                                .map(MojangSkinDataResult::skinProperty).orElse(null);
                        } else {
                            sp = MineSkinFeatureFlag.isEnabled()
                                ? SkinCommand.getMineSkinAPI().genSkin(customSource, variant).property()
                                : null;
                        }
                        for (EntityPlayerMP t : targets) {
                            fetched.put(t.getUniqueID(), sp);
                        }
                        break;
                    }
                    case username:
                    case random:
                    case NEW: {
                        String fetchName = customSource;
                        if (type == SkinActionType.random) {
                            fetchName = RandomMojangSkin.randomUsername(withCape, variant);
                        } else if (type == SkinActionType.NEW) {
                            fetchName = RandomMojangSkin.newUsername(variant);
                        }
                        CustomSkinProperty sp = SkinCommand.getMojangAPI().getSkin(fetchName)
                            .map(MojangSkinDataResult::skinProperty).orElse(null);
                        for (EntityPlayerMP t : targets) {
                            fetched.put(t.getUniqueID(), sp);
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                fetchNanos[0] = System.nanoTime() - fetchStartNanos;
            }
            return fetched;
        }, EXECUTOR);

        final ScheduledFuture<?> timeoutFuture = EXECUTOR.schedule(() -> {
            if (future.completeExceptionally(new TimeoutException("Skin fetch timeout"))) {
                EverlastingSkins.logger.error("Skin fetch timeout");
                for (EntityPlayerMP p : targets) {
                    SkinMetrics.INSTANCE.recordTimedOut(p.getUniqueID());
                    p.sendMessage(new TextComponentString(SkinCommand.PREFIX + "Skin fetch timeout"));
                }
            }
        }, 10, TimeUnit.SECONDS);

        future.whenComplete((fetched, err) -> {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }
            if (err != null) {
                EverlastingSkins.logger.error("Skin process error", err);
                for (EntityPlayerMP p : targets) {
                    p.sendMessage(new TextComponentString(SkinCommand.PREFIX + "Skin process error"));
                }
                return;
            }
            for (EntityPlayerMP p : targets) {
                CustomSkinProperty sp = fetched != null ? fetched.get(p.getUniqueID()) : null;
                if (sp == null) {
                    if (type != SkinActionType.clear) {
                        String reason = deriveReason(type, customSource);
                        EverlastingSkins.logger.warn("Skin provider returned no result: {}", reason);
                        p.sendMessage(new TextComponentString(SkinCommand.PREFIX + reason));
                        continue;
                    }
                    EverlastingSkins.logger.info("Skin cleared for player {} — no Mojang profile found", p.getName());
                    SkinRestorer.getSkinStorage().setSkin(p.getUniqueID(), null);
                    if (Config.TOGGLE) {
                        p.sendMessage(new TextComponentString(SkinCommand.PREFIX + "Skin cleared (no Mojang profile found)"));
                    }
                    SkinRestorer.getServer().addScheduledTask(() -> SkinRefreshTask.task(p, null, fetchNanos[0]));
                    continue;
                }
                boolean isRestore = (type == SkinActionType.clear);
                if (isSkinUnchanged(p.getUniqueID(), sp)) {
                    EverlastingSkins.logger.debug("SKIN_REFRESH skipped: identical skin for {}", p.getUniqueID());
                    SkinMetrics.INSTANCE.recordRefreshSkipped(p.getUniqueID());
                    continue;
                }
                SkinRestorer.getSkinStorage().setSkin(p.getUniqueID(), sp);
                if (Config.TOGGLE) {
                    String msg = isRestore
                        ? "Skin restored from " + sp.getSource()
                        : "Skin applied";
                    p.sendMessage(new TextComponentString(SkinCommand.PREFIX + msg));
                }
                long now = System.currentTimeMillis();
                Long last = lastRefreshByPlayer.get(p.getUniqueID());
                if (last != null && now - last < Config.DEBOUNCE_MILLIS) {
                    EverlastingSkins.logger.debug("SKIN_REFRESH debounced for {}", p.getUniqueID());
                    SkinMetrics.INSTANCE.recordRefreshDebounced(p.getUniqueID());
                    continue;
                }
                lastRefreshByPlayer.put(p.getUniqueID(), now);
                SkinRestorer.getServer().addScheduledTask(() -> SkinRefreshTask.task(p, sp, fetchNanos[0]));
                try {
                    DiscordSrvHook.announceSkinChange(p, customSource);
                } catch (Exception e) {
                    EverlastingSkins.logger.warn("Failed to announce skin change to DiscordSRV", e);
                }
            }
        });
    }

    /** A5: stored skin's source already matches the request, skip the fetch. */
    private static boolean storedSourceMatches(Collection<EntityPlayerMP> targets, @Nullable String customSource) {
        return targets.stream().allMatch(p -> {
            CustomSkinProperty stored = SkinRestorer.getSkinStorage().getSkin(p.getUniqueID());
            return stored != null && Objects.equals(stored.getSource(), customSource);
        });
    }

    /** True when the fetched skin is byte-identical to what the player already has. */
    private static boolean isSkinUnchanged(UUID uuid, @Nullable CustomSkinProperty newSkin) {
        if (newSkin == null) return false;
        CustomSkinProperty stored = SkinRestorer.getSkinStorage().getSkin(uuid);
        if (stored == null) return false;
        if (!newSkin.getOriginalProperty().getValue().equals(stored.getOriginalProperty().getValue())) {
            return false;
        }
        return Objects.equals(newSkin.getSource(), stored.getSource());
    }

    /** Per-player cooldown plus a sliding per-minute window. */
    private static boolean isRateLimited(EntityPlayerMP sender) {
        if (PermissionServiceManager.hasPermission(
                PermissionContext.of(sender.getUniqueID(), sender),
                "everlastingskins.bypass.cooldown")) {
            return false;
        }
        UUID uuid = sender.getUniqueID();
        long now = System.currentTimeMillis();
        long cooldownMs = Config.COOLDOWN_SECONDS * 1000L;
        long lastCommand = lastCommandByPlayer.getOrDefault(uuid, 0L);
        long elapsed = now - lastCommand;
        if (lastCommand > 0 && elapsed < cooldownMs) {
            SkinMetrics.INSTANCE.recordRateLimited(uuid);
            sender.sendMessage(new TextComponentString(SkinCommand.PREFIX
                + "Please wait " + ((cooldownMs - elapsed) / 1000) + "s before using /skin again"));
            return true;
        }
        ArrayDeque<Long> window = commandTimestampsByPlayer.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > 60_000) {
                window.pollFirst();
            }
            if (window.size() >= Config.MAX_COMMANDS_PER_MINUTE) {
                SkinMetrics.INSTANCE.recordRateLimited(uuid);
                sender.sendMessage(new TextComponentString(SkinCommand.PREFIX + "Too many /skin commands. Try again later."));
                return true;
            }
            window.addLast(now);
        }
        lastCommandByPlayer.put(uuid, now);
        return false;
    }

    private static String deriveReason(SkinActionType type, @Nullable String customSource) {
        switch (type) {
            case username:
                return customSource != null
                    ? "No skin found for \"" + customSource + "\""
                    : "No skin found";
            case url: {
                if (customSource != null) {
                    String sanitized = EverlastingHelpers.sanitizeSkinInput(customSource);
                    if (!sanitized.equals(customSource)) {
                        return "No skin found for \"" + sanitized + "\"";
                    }
                }
                return "MineSkin rejected the URL";
            }
            case random:
            case NEW:
                return "No random username available";
            default:
                return "Provider returned no result";
        }
    }
}
