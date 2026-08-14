/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge152.config;

import levosilimo.everlastingskins.forge152.EverlastingSkins;
import net.minecraftforge.common.Configuration;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * 1.5.2 server config — static fields + {@link #load(File)}, ported from the
 * forge-1.6.4 / forge-1.7.10 reference ports (PR #478 / #477) off the
 * mc1.12.2 template.
 *
 * <p>Key set mirrors the sibling ports exactly: LANGUAGE (server/admin
 * language; per-player locale resolution is post-1.13), the URL allowlist
 * (consumed by the MineSkin generator on GameProfile lanes — 1.5.2 has no
 * URL pipeline and rejects {@code /skin set web} honestly, so the fields
 * ship for cfg parity and future porting), the metrics dump interval, and
 * the Mojang profile cache.
 *
 * <p>1.5.2-era API (verified against the vendored decompiled tree): same
 * surface as 1.6.4 — {@code Configuration} lives at
 * {@code net.minecraftforge.common} (the {@code .config} subpackage is
 * 1.7+), the constructor NPEs outside an FML-booted JVM via
 * {@code FMLInjectionData.data()[6]} (cpw.mods.fml.relauncher on this
 * line), and only the raw {@code get(category, key, default, comment)}
 * overloads returning {@code Property} exist — no typed
 * getString/getBoolean/getInt convenience methods. Values are read via
 * {@code Property.getString()/getBoolean()/getInt()/getStringList()}.
 *
 * <p>Category quirk (same as 1.6.4/1.7.10): this era's {@code Configuration}
 * lowercases category names on lookup (caseSensitiveCustomCategories=false
 * default) while the parser keeps file-case names, so the .cfg categories
 * MUST be lowercase ("messages"/"security"/"mojangcache"/"everlastingskins")
 * or they silently miss.
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
            LANGUAGE = cfg.get("messages", "localization", LANGUAGE, "Language of mod messages").getString();
            urlAllowlistEnabled = cfg.get("security", "urlAllowlistEnabled", urlAllowlistEnabled,
                "Enable URL domain allowlist for /skin set web (empty list = deny all)").getBoolean(urlAllowlistEnabled);
            urlAllowlistDomains = cfg.get("security", "urlAllowlistDomains", urlAllowlistDomains,
                "Domains allowed for /skin set web (eTLD+1 suffix match; one entry covers all subdomains)").getStringList();
            metricsEnabled = cfg.get("everlastingskins", "metricsEnabled", metricsEnabled,
                "Enable in-process metrics").getBoolean(metricsEnabled);
            metricsDumpIntervalSeconds = cfg.get("everlastingskins", "metricsDumpIntervalSeconds",
                metricsDumpIntervalSeconds, "Metrics dump interval (seconds)").getInt(metricsDumpIntervalSeconds);
            mojangProfileCacheEnabled = cfg.get("mojangcache", "mojangProfileCacheEnabled", true,
                "Enable Mojang profile cache (recommended for production servers; reduces Mojang API hits)").getBoolean(true);
            mojangProfileCacheTtlMs = cfg.get("mojangcache", "mojangProfileCacheTtlMs", 3600000,
                "Mojang profile cache TTL in milliseconds (default 1h, max 7 days)").getInt(3600000);
            mojangProfileCacheMaxSize = cfg.get("mojangcache", "mojangProfileCacheMaxSize", 1000,
                "Mojang profile cache max entries (default 1000, max 1M)").getInt(1000);
        } catch (Exception e) {
            EverlastingSkins.logger.error("Failed to load config", e);
        } finally {
            if (cfg.hasChanged()) cfg.save();
        }
    }

    private Config() {}
}
