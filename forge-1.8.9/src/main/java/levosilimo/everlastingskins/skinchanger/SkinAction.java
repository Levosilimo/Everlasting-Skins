/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provider-fetch pipeline for the 1.8.9 {@code /skin} command. Runs the
 * Mojang/MineSkin/random HTTP fetch off the server thread and applies the
 * result back on the server thread via {@link MinecraftServer#addScheduledTask}
 * so packet sends stay thread-safe — the same shape as the mc1.12.2
 * SkinAction, trimmed to this lane's scope (no Config, no rate limiting,
 * no DiscordSRV hook).
 */
final class SkinAction {

    /** Source-class discriminator persisted on Mojang-sourced skins. */
    static final String SOURCE_MOJANG = "MojangAPI";

    private static ExecutorService executor = Executors.newCachedThreadPool();

    private SkinAction() {}

    /** Test seam: inject a direct executor for deterministic tests. */
    static void setExecutorForTest(ExecutorService testExecutor) {
        executor = testExecutor;
    }

    static void apply(Collection<EntityPlayerMP> targets, ICommandSender sender,
            SkinActionType type, SkinVariant variant, boolean withCape, @Nullable String customSource) {
        if (targets.isEmpty()) {
            return;
        }
        // A5 (mc1.12.2 parity): when the stored skin already came from the
        // provider this request would use AND was fetched for the same
        // username, skip the fetch and re-apply the stored skin.
        if (type == SkinActionType.username && storedSourceMatches(targets, customSource)) {
            for (EntityPlayerMP p : targets) {
                UUID uuid = SkinRestorer.profileIdOf(p);
                SkinMetrics.INSTANCE.recordRefreshSkippedStored(uuid);
                CustomSkinProperty stored = SkinRestorer.getSkinStorage().getSkin(uuid);
                MinecraftServer server = SkinRestorer.getServer();
                if (server != null) {
                    server.addScheduledTask(() -> SkinRestorer.applySkin(p, stored));
                }
            }
            return;
        }
        executor.submit(() -> {
            Map<UUID, CustomSkinProperty> fetched = new HashMap<>();
            try {
                switch (type) {
                    case clear:
                        // Each target restores from its OWN stored source —
                        // the first target's Mojang skin must not leak to
                        // the others.
                        for (EntityPlayerMP t : targets) {
                            UUID uuid = SkinRestorer.profileIdOf(t);
                            String storedSource = SkinRestorer.getSkinStorage().getSource(uuid);
                            SkinCommand.MojangRestoreResult restore = SkinCommand.tryRestoreFromMojang(
                                SkinCommand.getMojangAPI(), storedSource, t.getGameProfile().getName());
                            fetched.put(uuid, restore != null ? restore.skin : null);
                        }
                        break;
                    case url: {
                        levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse response =
                            SkinCommand.getMineSkinAPI().genSkin(customSource, variant);
                        CustomSkinProperty sp = response != null ? response.property() : null;
                        for (EntityPlayerMP t : targets) {
                            fetched.put(SkinRestorer.profileIdOf(t), sp);
                        }
                        break;
                    }
                    case username:
                    case random: {
                        String fetchName = customSource;
                        if (type == SkinActionType.random) {
                            // Cape mode swaps the candidate source: the
                            // RandomCapeSource (mskins with_capes + Cosmetica)
                            // listing instead of RandomMojangSkin, which only
                            // finds legacy cape holders.
                            fetchName = withCape
                                ? new RandomCapeSource().pickRandomCapeUsername()
                                : RandomMojangSkin.randomUsername(false, variant);
                        }
                        CustomSkinProperty sp = SkinCommand.getMojangAPI().getSkin(fetchName)
                            .map(MojangSkinDataResult::skinProperty).orElse(null);
                        for (EntityPlayerMP t : targets) {
                            fetched.put(SkinRestorer.profileIdOf(t), sp);
                        }
                        break;
                    }
                    default:
                        return;
                }
            } catch (Exception e) {
                EverlastingSkins.LOGGER.error("Skin fetch failed for /skin {}", type, e);
                for (EntityPlayerMP p : targets) {
                    SkinMetrics.INSTANCE.recordTimedOut(SkinRestorer.profileIdOf(p));
                    p.addChatMessage(new ChatComponentText(SkinCommand.PREFIX + "Skin fetch failed, please try again"));
                }
                return;
            }
            MinecraftServer server = SkinRestorer.getServer();
            if (server == null) {
                return;
            }
            server.addScheduledTask(() -> {
                for (EntityPlayerMP p : targets) {
                    UUID uuid = SkinRestorer.profileIdOf(p);
                    CustomSkinProperty sp = fetched.get(uuid);
                    if (sp == null || sp.isEmpty()) {
                        if (type != SkinActionType.clear) {
                            EverlastingSkins.LOGGER.warn("Skin provider returned no result for {}", p.getName());
                            p.addChatMessage(new ChatComponentText(SkinCommand.PREFIX + "No skin found for that source"));
                            continue;
                        }
                        // Clear with no Mojang profile to restore: storage is
                        // already dropped and the applied textures stripped.
                        SkinRestorer.clearSkin(p);
                        continue;
                    }
                    SkinRestorer.applySkin(p, sp);
                    p.addChatMessage(new ChatComponentText(SkinCommand.PREFIX + "Skin applied"));
                }
            });
        });
    }

    /**
     * True when every target's stored skin is Mojang-class AND was fetched
     * for the requested username (mirror of the mc1.12.2 A5 skip).
     */
    private static boolean storedSourceMatches(Collection<EntityPlayerMP> targets, @Nullable String customSource) {
        if (customSource == null) {
            return false;
        }
        SkinStorage storage = SkinRestorer.getSkinStorage();
        if (storage == null) {
            return false;
        }
        for (EntityPlayerMP p : targets) {
            CustomSkinProperty stored = storage.getSkin(SkinRestorer.profileIdOf(p));
            if (stored == null
                    || !SOURCE_MOJANG.equals(stored.getSource())
                    || !customSource.equals(stored.getUsername())) {
                return false;
            }
        }
        return true;
    }
}
