/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge21.util;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.forge21.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.DefaultSkinResolver;
import levosilimo.everlastingskins.skinchanger.MojangProfileCache;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

    /** Cap for the applied-skin history offered by {@link #suggestSkinNames}. */
    private static final int APPLIED_HISTORY_LIMIT = 50;

    private static volatile MojangProfileCache profileCache = new MojangProfileCache(
            Config.MOJANG_CACHE_TTL_MS.get(), Config.MOJANG_CACHE_MAX_SIZE.get());

    /**
     * Recently applied skin names/URLs, newest first. Recorded at the apply
     * site (SkinActionCommand's completion handler) so a server's admins get
     * meaningful completions even before any profile lookup runs. Bounded and
     * in-memory: suggestion candidates are a convenience, not a store.
     */
    private static final Deque<String> APPLIED_USERNAMES = new ArrayDeque<>();
    private static final Deque<String> APPLIED_URLS = new ArrayDeque<>();

    private CompletionSources() {
    }

    /**
     * Swaps the cache backing {@link #recentUsernames()}. Primarily a test
     * seam; production deployments can point it at the same cache instance
     * the Mojang API uses so recently fetched profiles are offered.
     */
    public static void setMojangProfileCache(MojangProfileCache cache) {
        profileCache = cache != null ? cache : new MojangProfileCache(
                Config.MOJANG_CACHE_TTL_MS.get(), Config.MOJANG_CACHE_MAX_SIZE.get());
    }

    /**
     * Records a successfully applied skin name so future {@code set mojang}
     * completions offer it. Deduplicated, newest first, capped at
     * {@link #APPLIED_HISTORY_LIMIT}.
     */
    public static void recordAppliedUsername(String username) {
        recordApplied(APPLIED_USERNAMES, username);
    }

    /**
     * Records a successfully applied skin URL so future {@code set web}
     * completions offer it. Deduplicated, newest first, capped at
     * {@link #APPLIED_HISTORY_LIMIT}.
     */
    public static void recordAppliedUrl(String url) {
        recordApplied(APPLIED_URLS, url);
    }

    private static void recordApplied(Deque<String> history, String value) {
        if (value == null || value.isEmpty()) return;
        synchronized (history) {
            history.remove(value);
            history.addFirst(value);
            while (history.size() > APPLIED_HISTORY_LIMIT) {
                history.removeLast();
            }
        }
    }

    /** Applied skin names, newest first. */
    public static List<String> appliedUsernames() {
        return snapshot(APPLIED_USERNAMES);
    }

    /** Applied skin URLs, newest first. */
    public static List<String> appliedUrls() {
        return snapshot(APPLIED_URLS);
    }

    /** Test seam: clears the applied-skin history. */
    public static void resetAppliedHistory() {
        synchronized (APPLIED_USERNAMES) {
            APPLIED_USERNAMES.clear();
        }
        synchronized (APPLIED_URLS) {
            APPLIED_URLS.clear();
        }
    }

    private static List<String> snapshot(Deque<String> history) {
        synchronized (history) {
            return Collections.unmodifiableList(new ArrayList<>(history));
        }
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
     * Candidate skin names for {@code set mojang <skin_name>}, tagged with
     * where each candidate came from. Priority order (first tag wins on
     * duplicates): applied-skin history, online players, cached profile
     * lookups, configured default skins. The map preserves insertion order.
     */
    public static Map<String, String> skinNameCandidatesTagged(CommandSourceStack source) {
        Map<String, String> tagged = new LinkedHashMap<>();
        for (String name : appliedUsernames()) {
            tagged.putIfAbsent(name, "Previously used");
        }
        if (source != null && source.getOnlinePlayerNames() != null) {
            for (String online : source.getOnlinePlayerNames()) {
                if (online != null && !online.isEmpty()) {
                    tagged.putIfAbsent(online, "Online");
                }
            }
        }
        for (String cached : profileCache.snapshot()) {
            tagged.putIfAbsent(cached, "Cached");
        }
        for (String configured : Config.DEFAULT_SKINS_LIST.get()) {
            if (!DefaultSkinResolver.RANDOM_TOKEN.equals(configured)) {
                tagged.putIfAbsent(configured, "Configured default");
            }
        }
        return tagged;
    }

    /**
     * Candidate skin names for {@code set mojang <skin_name>}: applied-skin
     * history, cached profile lookups, configured default skins, and the
     * names of every player currently online. Pure in-memory reads: no Mojang
     * lookup, so the caller keeps the suggester synchronous.
     */
    public static List<String> skinNameCandidates(CommandSourceStack source) {
        return Collections.unmodifiableList(new ArrayList<>(skinNameCandidatesTagged(source).keySet()));
    }

    /**
     * Suggests skin names for {@code set mojang <skin_name>} with
     * case-insensitive prefix-or-substring matching (so "not" and "otch" both
     * reach "Notch") and a per-suggestion tooltip naming the candidate's
     * origin. Synchronous and network-free by design: the candidates all come
     * from in-memory state, never a per-keystroke Mojang fetch.
     */
    public static CompletableFuture<Suggestions> suggestSkinNames(CommandSourceStack source, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> candidate : skinNameCandidatesTagged(source).entrySet()) {
            if (matchesSubStr(remaining, candidate.getKey().toLowerCase(Locale.ROOT))) {
                builder.suggest(candidate.getKey(), Component.literal(candidate.getValue()));
            }
        }
        return builder.buildFuture();
    }

    /**
     * Candidate URLs for {@code set web <url>}: applied-skin history first,
     * then the allowlisted domains as https:// and http:// URLs.
     */
    public static List<String> skinUrlCandidates() {
        List<String> candidates = new ArrayList<>(appliedUrls());
        for (String url : urlCandidates()) {
            if (!candidates.contains(url)) candidates.add(url);
        }
        return Collections.unmodifiableList(candidates);
    }

    /**
     * Suggests URLs for {@code set web <url>} with case-insensitive prefix
     * matching and a per-suggestion tooltip ("Previously used" for applied
     * history, "Allowlisted URL" for configured domains). Duplicate texts
     * are deduplicated text-first, so an applied URL that is also allowlisted
     * keeps its "Previously used" tooltip.
     */
    public static CompletableFuture<Suggestions> suggestSkinUrls(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        java.util.Set<String> suggested = new java.util.HashSet<>();
        for (String url : appliedUrls()) {
            if (startsWithIgnoreCase(url, remaining) && suggested.add(url)) {
                builder.suggest(url, Component.literal("Previously used"));
            }
        }
        for (String url : urlCandidates()) {
            if (startsWithIgnoreCase(url, remaining) && suggested.add(url)) {
                builder.suggest(url, Component.literal("Allowlisted URL"));
            }
        }
        return builder.buildFuture();
    }

    /**
     * Case-insensitive prefix-or-substring match (Velocity's
     * regionMatches-with-offset pattern): the typed text may match the start
     * of a candidate or any later position.
     */
    private static boolean matchesSubStr(String remaining, String candidate) {
        if (candidate.startsWith(remaining)) return true;
        for (int i = 1; i < candidate.length(); i++) {
            if (candidate.regionMatches(i, remaining, 0, remaining.length())) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithIgnoreCase(String candidate, String prefix) {
        return candidate.regionMatches(true, 0, prefix, 0, prefix.length());
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
        return PermissionServiceManager.hasPermission(ctx.uuid(), ctx.opLevel(), METRICS_RESET_PERMISSION);
    }
}
