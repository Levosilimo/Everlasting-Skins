/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge1710.config;

import levosilimo.everlastingskins.forge1710.EverlastingSkins;
import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * 1.7.10 server config — static fields + {@link #load(File)}, ported from
 * the mc1.12.2 template (same Forge {@link Configuration} API era).
 *
 * <p>Key set is scoped to what this lane consumes: URL allowlist (wired
 * into the MineSkin generator), the server/admin language (per-player
 * locale resolution is post-1.13), the metrics dump interval, and the
 * Mojang profile cache. Modern-only keys (HTTP client version, DiscordSRV,
 * message overrides, default skins, per-node op levels) are intentionally
 * absent — the lane has no consumers for them.
 *
 * <p>1.7.10 quirk: this era's {@code Configuration} lowercases category
 * names on lookup (caseSensitiveCustomCategories=false default) while the
 * parser stores file-case names, so the .cfg categories MUST be lowercase
 * ("messages"/"security"/"mojangcache") — the CamelCase categories of
 * the mc1.12.2 template silently miss on 1.7.10.
 */
public final class Config {

    /** Server/admin language for mod messages (see {@code I18nUtils}). */
    public static String LANGUAGE = "en";

    public static boolean urlAllowlistEnabled = false;
    public static String[] urlAllowlistDomains = new String[]{
        "imgur.com", "storage.googleapis.com", "cdn.discordapp.com",
        "textures.minecraft.net", "namemc.com", "crafatar.com",
        "mc-heads.net", "githubusercontent.com", "minecraftskins.com"
    };

    public static boolean metricsEnabled = true;
    public static int metricsDumpIntervalSeconds = 60;

    public static boolean mojangProfileCacheEnabled = true;
    public static long mojangProfileCacheTtlMs = TimeUnit.HOURS.toMillis(1);
    public static int mojangProfileCacheMaxSize = 1000;

    public static void load(File configFile) {
        Configuration cfg = new Configuration(configFile);
        try {
            cfg.load();
            LANGUAGE = cfg.getString("localization", "messages", LANGUAGE, "Language of mod messages");
            urlAllowlistEnabled = cfg.getBoolean("urlAllowlistEnabled", "security", urlAllowlistEnabled,
                "Enable URL domain allowlist for /skin set web (empty list = deny all)");
            urlAllowlistDomains = cfg.getStringList("urlAllowlistDomains", "security", urlAllowlistDomains,
                "Domains allowed for /skin set web (eTLD+1 suffix match; one entry covers all subdomains)");
            metricsEnabled = cfg.getBoolean("metricsEnabled", "everlastingskins", metricsEnabled,
                "Enable in-process metrics");
            metricsDumpIntervalSeconds = cfg.getInt("metricsDumpIntervalSeconds", "everlastingskins",
                metricsDumpIntervalSeconds, 0, 3600, "Metrics dump interval (seconds)");
            mojangProfileCacheEnabled = cfg.getBoolean("mojangProfileCacheEnabled", "mojangcache", true,
                "Enable Mojang profile cache (recommended for production servers; reduces Mojang API hits)");
            mojangProfileCacheTtlMs = cfg.getInt("mojangProfileCacheTtlMs", "mojangcache", 3600000, 0, 604800000,
                "Mojang profile cache TTL in milliseconds (default 1h, max 7 days)");
            mojangProfileCacheMaxSize = cfg.getInt("mojangProfileCacheMaxSize", "mojangcache", 1000, 0, 1000000,
                "Mojang profile cache max entries (default 1000, max 1M)");
        } catch (Exception e) {
            EverlastingSkins.logger.error("Failed to load config", e);
        } finally {
            if (cfg.hasChanged()) cfg.save();
        }
    }

    private Config() {}
}
