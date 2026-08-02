package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.integration.discordsrv.DiscordSrvHook;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.EverlastingHelpers;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() * 2));

    private SkinAction() {
    }

    static void apply(Collection<EntityPlayerMP> targets, ICommandSender sender,
            SkinActionType type, SkinVariant variant, boolean withCape, @Nullable String customSource) {
        for (EntityPlayerMP p : targets) {
            if (Config.TOGGLE) {
                p.sendMessage(new TextComponentString(SkinCommand.PREFIX + "Processing..."));
            }
        }
        long[] fetchNanos = {0L};
        CompletableFuture<CustomSkinProperty> future = CompletableFuture.supplyAsync(() -> {
            CustomSkinProperty sp = null;
            long fetchStartNanos = System.nanoTime();
            try {
                switch (type) {
                    case clear:
                        String storedSrc = null;
                        String pName = null;
                        for (EntityPlayerMP t : targets) {
                            storedSrc = SkinRestorer.getSkinStorage().getSource(t.getUniqueID());
                            pName = t.getGameProfile().getName();
                            break;
                        }
                        SkinCommand.MojangRestoreResult restore = SkinCommand.tryRestoreFromMojang(SkinCommand.getMojangAPI(), storedSrc, pName);
                        sp = restore != null ? restore.skin : null;
                        break;
                    case url: {
                        String sanitized = EverlastingHelpers.sanitizeSkinInput(customSource);
                        if (!sanitized.equals(customSource)) {
                            sp = SkinCommand.getMojangAPI().getSkin(sanitized)
                                .map(MojangSkinDataResult::skinProperty).orElse(null);
                        } else {
                            sp = MineSkinFeatureFlag.isEnabled()
                                ? SkinCommand.getMineSkinAPI().genSkin(customSource, variant).property()
                                : null;
                        }
                        break;
                    }
                    case username:
                        sp = SkinCommand.getMojangAPI().getSkin(customSource)
                            .map(MojangSkinDataResult::skinProperty).orElse(null);
                        break;
                    case random:
                        sp = SkinCommand.getMojangAPI().getSkin(RandomMojangSkin.randomUsername(withCape, variant))
                            .map(MojangSkinDataResult::skinProperty).orElse(null);
                        break;
                    case NEW:
                        sp = SkinCommand.getMojangAPI().getSkin(RandomMojangSkin.newUsername(variant))
                            .map(MojangSkinDataResult::skinProperty).orElse(null);
                        break;
                }
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                fetchNanos[0] = System.nanoTime() - fetchStartNanos;
            }
            return sp;
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

        future.whenComplete((sp, err) -> {
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
            if (sp == null) {
                if (type != SkinActionType.clear) {
                    String reason = deriveReason(type, customSource);
                    EverlastingSkins.logger.warn("Skin provider returned no result: {}", reason);
                    for (EntityPlayerMP p : targets) {
                        p.sendMessage(new TextComponentString(SkinCommand.PREFIX + reason));
                    }
                    return;
                }
                EverlastingSkins.logger.info("Skin cleared for player(s) — no Mojang profile found");
                for (EntityPlayerMP p : targets) {
                    SkinRestorer.getSkinStorage().setSkin(p.getUniqueID(), null);
                    if (Config.TOGGLE) {
                        p.sendMessage(new TextComponentString(SkinCommand.PREFIX + "Skin cleared (no Mojang profile found)"));
                    }
                }
                for (EntityPlayerMP p : targets) {
                    SkinRestorer.getServer().addScheduledTask(() -> SkinRefreshTask.task(p, null, fetchNanos[0]));
                }
                return;
            }
            boolean isRestore = (type == SkinActionType.clear);
            for (EntityPlayerMP p : targets) {
                SkinRestorer.getSkinStorage().setSkin(p.getUniqueID(), sp);
                if (Config.TOGGLE) {
                    String msg = isRestore
                        ? "Skin restored from " + sp.getSource()
                        : "Skin applied";
                    p.sendMessage(new TextComponentString(SkinCommand.PREFIX + msg));
                }
            }
            for (EntityPlayerMP p : targets) {
                SkinRestorer.getServer().addScheduledTask(() -> SkinRefreshTask.task(p, sp, fetchNanos[0]));
            }
            for (EntityPlayerMP p : targets) {
                try {
                    DiscordSrvHook.announceSkinChange(p, customSource);
                } catch (Exception e) {
                    EverlastingSkins.logger.warn("Failed to announce skin change to DiscordSRV", e);
                }
            }
        });
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
