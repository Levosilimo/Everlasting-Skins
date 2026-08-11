/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.forge102.EverlastingSkins;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Provider-fetch pipeline for /skin actions (1.10.2 era-adapted mirror of
 * the mc1.12.2 class of the same name). Runs off the server thread with a
 * hard timeout so a slow Mojang/MineSkin HTTP call can never block the tick.
 *
 * <p>This lane has no Config file, so the 1.12.2 rate-limit, debounce and
 * message-toggle layers are intentionally absent; the persistence +
 * GameProfile-mutation core is kept 1:1.
 */
final class SkinAction {

    static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() * 2));

    /** Source-class discriminator persisted on Mojang-sourced skin properties. */
    static final String SOURCE_MOJANG = "MojangAPI";
    /** Source-class discriminator persisted on MineSkin-sourced skin properties. */
    static final String SOURCE_MINESKIN = "MineSkin";

    private SkinAction() {
    }

    static void apply(Collection<EntityPlayerMP> targets, ICommandSender sender,
            SkinActionType type, SkinVariant variant, boolean withCape, @Nullable String customSource) {
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
                        CustomSkinProperty sp = SkinCommand.getMineSkinAPI()
                            .genSkin(customSource, variant).property();
                        for (EntityPlayerMP t : targets) {
                            fetched.put(t.getUniqueID(), sp);
                        }
                        break;
                    }
                    case username:
                    case random: {
                        String fetchName = customSource;
                        if (type == SkinActionType.random) {
                            // Cape mode swaps the candidate source: RandomMojangSkin's
                            // decoded-CAPE check only finds legacy cape holders (Mojang
                            // stopped embedding CAPE in the textures payload), so
                            // RandomCapeSource (mskins with_capes + Cosmetica) is used
                            // instead. The variant filter is intentionally not applied
                            // in cape mode: the listing is not variant-tagged.
                            fetchName = withCape
                                ? new RandomCapeSource().pickRandomCapeUsername()
                                : RandomMojangSkin.randomUsername(false, variant);
                        }
                        CustomSkinProperty sp = SkinCommand.getMojangAPI().getSkin(fetchName)
                            .map(MojangSkinDataResult::skinProperty).orElse(null);
                        for (EntityPlayerMP t : targets) {
                            fetched.put(t.getUniqueID(), sp);
                        }
                        break;
                    }
                    default:
                        throw new IllegalStateException("Unsupported action type: " + type);
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
                EverlastingSkins.LOGGER.error("Skin fetch timeout");
                for (EntityPlayerMP p : targets) {
                    SkinMetrics.INSTANCE.recordTimedOut(p.getUniqueID());
                    p.sendMessage(new TextComponentString(SkinCommand.PREFIX + "Skin fetch timed out."));
                }
            }
        }, 10, TimeUnit.SECONDS);

        future.whenComplete((fetched, err) -> {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }
            if (err != null) {
                EverlastingSkins.LOGGER.error("Skin process error", err);
                for (EntityPlayerMP p : targets) {
                    p.sendMessage(new TextComponentString(SkinCommand.PREFIX + "Failed to fetch skin."));
                }
                return;
            }
            MinecraftServer server = SkinRestorer.getServer();
            for (EntityPlayerMP p : targets) {
                CustomSkinProperty sp = fetched != null ? fetched.get(p.getUniqueID()) : null;
                if (sp == null) {
                    if (type != SkinActionType.clear) {
                        EverlastingSkins.LOGGER.warn("Skin provider returned no result for {}", p.getName());
                        p.sendMessage(new TextComponentString(SkinCommand.PREFIX + "No skin found."));
                        continue;
                    }
                    EverlastingSkins.LOGGER.info("Skin cleared for player {} — no Mojang profile found", p.getName());
                    SkinRestorer.getSkinStorage().setSkin(p.getUniqueID(), null);
                    if (server != null) {
                        server.addScheduledTask(() -> SkinRefreshTask.task(p, null, fetchNanos[0]));
                    }
                    continue;
                }
                boolean isRestore = (type == SkinActionType.clear);
                if (isSkinUnchanged(p.getUniqueID(), sp)) {
                    EverlastingSkins.LOGGER.debug("SKIN_REFRESH skipped: identical skin for {}", p.getUniqueID());
                    SkinMetrics.INSTANCE.recordRefreshSkipped(p.getUniqueID());
                    continue;
                }
                SkinRestorer.getSkinStorage().setSkin(p.getUniqueID(), sp);
                p.sendMessage(new TextComponentString(SkinCommand.PREFIX
                    + (isRestore ? "Restored original skin." : "Skin applied!")));
                if (Boolean.getBoolean("everlastingskins.e2e")) {
                    // Sentinel for the real-client E2E (slice 2): the /skin
                    // reply is a chat message only the client sees, so the E2E
                    // asserts this server-log marker (the driver boots the
                    // server with -Deverlastingskins.e2e=true).
                    EverlastingSkins.LOGGER.info("ES_E2E_SKIN=ok player={} source={}", p.getName(), customSource);
                }
                if (server != null) {
                    server.addScheduledTask(() -> SkinRefreshTask.task(p, sp, fetchNanos[0]));
                }
            }
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
}
