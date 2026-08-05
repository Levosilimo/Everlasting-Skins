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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
    private static final List<String> METRICS_VIEW_SUBCOMMANDS = List.of("json", "players");

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
        for (String configured : Config.DEFAULT_SKINS_LIST.get()) {
            if (!DefaultSkinResolver.RANDOM_TOKEN.equals(configured) && !names.contains(configured)) {
                names.add(configured);
            }
        }
        return Collections.unmodifiableList(names);
    }

    /** Every allowlisted domain as both an https:// and an http:// URL. */
    public static List<String> urlCandidates() {
        List<String> candidates = new ArrayList<>();
        for (String domain : Config.URL_ALLOWLIST_DOMAINS.get()) {
            candidates.add("https://" + domain);
            candidates.add("http://" + domain);
        }
        return Collections.unmodifiableList(candidates);
    }

    /** Providers for {@code set <provider>}; web is always available on 1.21. */
    public static List<String> providerNames() {
        return Collections.unmodifiableList(List.of("mojang", "random", "web"));
    }

    /** Names of every player currently online on the server. */
    public static List<String> onlinePlayerNames(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(server.getPlayerList().getPlayerNamesArray())));
    }

    /**
     * Metrics subcommands the sender may complete. View subcommands need the
     * base metrics permission; cleanup/reset additionally need the reset
     * permission, so unauthorized users never see them offered.
     */
    public static List<String> metricsSubcommands(CommandSourceStack source) {
        List<String> subcommands = new ArrayList<>(METRICS_VIEW_SUBCOMMANDS);
        if (hasResetPermission(source)) {
            subcommands.add("cleanup");
            subcommands.add("reset");
        }
        return Collections.unmodifiableList(subcommands);
    }

    private static boolean hasResetPermission(CommandSourceStack source) {
        ServerPlayer player = source == null ? null : source.getPlayer();
        if (player == null) return true; // console senders bypass the gate
        PermissionContext ctx = PermissionContext.of(player.getUUID(), player);
        return PermissionServiceManager.hasPermission(ctx, METRICS_RESET_PERMISSION);
    }
}
