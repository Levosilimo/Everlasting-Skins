/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.MojangProfileCache;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Shared candidate sources for /skin tab completion (1.10.2 era-adapted
 * port of the mc1.12.2 class of the same name).
 *
 * <p>This lane has no Config file, so the 1.12.2 configuration inputs are
 * replaced by compile-time constants mirroring the mc1.12.2 defaults: the
 * URL allowlist domains ({@link #URL_ALLOWLIST_DOMAINS}) and the always-on
 * MineSkin provider ({@link #providerNames()}).
 *
 * <p>Permission checks use the lane's {@code (UUID, requiredOpLevel, node)}
 * contract — the {@link levosilimo.everlastingskins.permission.forge.ForgePermissionService}
 * resolves the player's actual op level itself.
 */
public final class CompletionSources {

    private static final String METRICS_RESET_PERMISSION = "everlastingskins.command.metrics.reset";

    /** View subcommands every metrics-authorized sender may complete. */
    private static final List<String> METRICS_VIEW_SUBCOMMANDS =
            Collections.unmodifiableList(Arrays.asList("human", "json", "players"));

    /**
     * Allowlisted URL domains for {@code set web <classic|slim> <url>}
     * completion — mirror of the mc1.12.2 Config default list (this lane
     * has no config file; the security check itself still runs via
     * {@link UrlAllowlist} in :common).
     */
    private static final List<String> URL_ALLOWLIST_DOMAINS = Collections.unmodifiableList(Arrays.asList(
            "imgur.com", "storage.googleapis.com", "cdn.discordapp.com",
            "textures.minecraft.net", "namemc.com", "crafatar.com",
            "mc-heads.net", "githubusercontent.com", "minecraftskins.com"));

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

    /** Mojang usernames worth offering for {@code set mojang <name>}. */
    public static List<String> recentUsernames() {
        return Collections.unmodifiableList(new ArrayList<>(profileCache.snapshot()));
    }

    /** Every allowlisted domain as both an https:// and an http:// URL. */
    public static List<String> urlCandidates() {
        List<String> candidates = new ArrayList<>();
        for (String domain : URL_ALLOWLIST_DOMAINS) {
            candidates.add("https://" + domain);
            candidates.add("http://" + domain);
        }
        return Collections.unmodifiableList(candidates);
    }

    /** Providers for {@code set <provider>}; web is always available on this lane. */
    public static List<String> providerNames() {
        return Collections.unmodifiableList(Arrays.asList("mojang", "random", "web"));
    }

    /** Names of every player currently online on the server. */
    public static List<String> onlinePlayerNames(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(server.getPlayerList().getOnlinePlayerNames())));
    }

    /**
     * Metrics subcommands the sender may complete. View subcommands are
     * always offered (the execution gate re-checks); cleanup/reset are
     * offered only with the reset permission, so unauthorized users never
     * see them.
     */
    public static List<String> metricsSubcommands(ICommandSender sender) {
        List<String> subcommands = new ArrayList<>(METRICS_VIEW_SUBCOMMANDS);
        if (hasPermission(sender, METRICS_RESET_PERMISSION, 2)) {
            subcommands.add("cleanup");
            subcommands.add("reset");
        }
        return Collections.unmodifiableList(subcommands);
    }

    /**
     * Silent permission check (no denial message is sent, unlike the command
     * execution paths). Console senders pass, mirroring the execution gates.
     */
    public static boolean hasPermission(ICommandSender sender, String node, int requiredOpLevel) {
        if (!(sender instanceof EntityPlayerMP)) return true;
        EntityPlayerMP player = (EntityPlayerMP) sender;
        return PermissionServiceManager.hasPermission(player.getUniqueID(), requiredOpLevel, node);
    }
}
