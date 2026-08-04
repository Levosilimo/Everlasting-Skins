/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.DefaultSkinResolver;
import levosilimo.everlastingskins.skinchanger.MojangProfileCache;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Shared candidate sources for /skin tab completion.
 *
 * <p>Both the 1.21 Brigadier suggesters and the 1.12.2
 * {@code getTabCompletions} switch consume these lists, so the completion
 * vocabulary (providers, cached usernames, allowlisted URLs, online players,
 * permission-gated metrics subcommands) stays consistent across versions.
 */
public final class CompletionSources {

    private static final String METRICS_RESET_PERMISSION = "everlastingskins.command.metrics.reset";

    /** View subcommands every metrics-authorized sender may complete. */
    private static final List<String> METRICS_VIEW_SUBCOMMANDS =
            Collections.unmodifiableList(Arrays.asList("human", "json", "players"));

    private static volatile MojangProfileCache profileCache = new MojangProfileCache();

    private CompletionSources() {
    }

    /**
     * Swaps the cache backing {@link #recentUsernames()}. Primarily a test
     * seam; production deployments can point it at the same cache instance
     * the Mojang API uses so recently fetched profiles are offered.
     */
    public static void setMojangProfileCache(MojangProfileCache cache) {
        profileCache = cache != null ? cache : new MojangProfileCache();
    }

    /**
     * Mojang usernames worth offering for {@code set mojang <name>}: cached
     * profile lookups first, then the configured default skins list with the
     * {@code <random>} token excluded (it is not a real username).
     */
    public static List<String> recentUsernames() {
        List<String> names = new ArrayList<>();
        for (String cached : profileCache.snapshot()) {
            if (!names.contains(cached)) names.add(cached);
        }
        for (String configured : Config.DEFAULT_SKINS_LIST) {
            if (!DefaultSkinResolver.RANDOM_TOKEN.equals(configured) && !names.contains(configured)) {
                names.add(configured);
            }
        }
        return Collections.unmodifiableList(names);
    }

    /** Every allowlisted domain as both an https:// and an http:// URL. */
    public static List<String> urlCandidates() {
        List<String> candidates = new ArrayList<>();
        for (String domain : Config.urlAllowlistDomains) {
            candidates.add("https://" + domain);
            candidates.add("http://" + domain);
        }
        return Collections.unmodifiableList(candidates);
    }

    /** Providers for {@code set <provider>}; web only when MineSkin is enabled. */
    public static List<String> providerNames() {
        List<String> providers = new ArrayList<>(Arrays.asList("mojang", "random"));
        if (Config.MINESKIN_ENABLED) {
            providers.add("web");
        }
        return Collections.unmodifiableList(providers);
    }

    /** Names of every player currently online on the server. */
    public static List<String> onlinePlayerNames(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(server.getPlayerList().getOnlinePlayerNames())));
    }

    /**
     * Metrics subcommands the sender may complete. View subcommands need the
     * base metrics permission; cleanup/reset additionally need the reset
     * permission, so unauthorized users never see them offered.
     */
    public static List<String> metricsSubcommands(ICommandSender sender) {
        List<String> subcommands = new ArrayList<>(METRICS_VIEW_SUBCOMMANDS);
        if (hasPermission(sender, METRICS_RESET_PERMISSION)) {
            subcommands.add("cleanup");
            subcommands.add("reset");
        }
        return Collections.unmodifiableList(subcommands);
    }

    /**
     * Silent permission check (no denial message is sent, unlike the command
     * execution paths). Console senders pass, mirroring the execution gates.
     */
    public static boolean hasPermission(ICommandSender sender, String node) {
        if (!(sender instanceof EntityPlayerMP)) return true;
        EntityPlayerMP player = (EntityPlayerMP) sender;
        PermissionContext ctx = PermissionContext.of(player.getUniqueID(), player);
        return PermissionServiceManager.hasPermission(ctx, node);
    }
}
