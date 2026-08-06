/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import levosilimo.everlastingskins.Config;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * I18nUtils — 1.12.2 MCP-mapped placeholder.
 *
 * <p>Uses MCP method getFile(String) instead of SRG d(String).</p>
 */
public final class I18nUtils {
    private static final Map<String, Map<String, String>> localizedStrings = new HashMap<>();
    private static final String[] LOCALES = { "en", "ru", "uk", "zh_cn", "es_es", "pt_br", "de_de", "fr_fr", "ja_jp", "ko_kr", "it_it" };

    public static String getLocalizedString(String key, String locale) {
        if (key == null || locale == null) return key;
        Map<String, String> map = localizedStrings.get(locale);
        if (map == null) return key;
        String value = map.get(key);
        return value != null ? value : key;
    }

    /**
     * Per-player locale variant: resolves the player's client language via
     * PlayerLanguage (AT-exposed EntityPlayerMP.language) and falls back to
     * Config.LANGUAGE when the player is null or has no language set.
     */
    public static String getLocalizedString(String key, EntityPlayerMP player) {
        String locale = PlayerLanguage.get(player);
        if (locale == null || locale.isEmpty()) locale = Config.LANGUAGE;
        return getLocalizedString(key, defaultLocaleFor(locale));
    }

    public static void loadAll() {
        localizedStrings.clear();
        MinecraftServer server = SkinRestorer.getServer();
        if (server == null) return;
        Path configDir = server.getFile("config/EverlastingSkins").toPath();
        try {
            Files.createDirectories(configDir);
        } catch (IOException ignored) { return; }
        for (String locale : LOCALES) {
            loadLocale(locale, configDir);
        }
    }

    private static void loadLocale(String locale, Path configDir) {
        Path override = configDir.resolve("lang_" + locale + ".properties");
        java.net.URL resourceUrl = I18nUtils.class.getResource("/assets/everlastingskins/lang/" + locale + ".properties");
        // Fresh Properties per locale — no cross-file key leak.
        Properties merged = new Properties();
        if (resourceUrl != null) {
            try (InputStream is = resourceUrl.openStream()) {
                merged.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            } catch (IOException ignored) {}
        }
        if (Files.exists(override)) {
            try (InputStream is = Files.newInputStream(override)) {
                merged.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            } catch (IOException ignored) {}
        }
        Map<String, String> map = new HashMap<>();
        for (String k : merged.stringPropertyNames()) map.put(k, merged.getProperty(k));
        localizedStrings.put(locale, map);
    }

    /** Formats a localized template (String.format semantics; safe fallback to the raw template). */
    public static String formatMessage(String key, String locale, Object... args) {
        String template = getLocalizedString(key, locale);
        try {
            return String.format(template, args);
        } catch (Exception e) {
            return template;
        }
    }

    /** Per-player variant of {@link #formatMessage(String, String, Object...)}. */
    public static String formatMessage(String key, EntityPlayerMP player, Object... args) {
        String template = getLocalizedString(key, player);
        try {
            return String.format(template, args);
        } catch (Exception e) {
            return template;
        }
    }

    public static String defaultLocaleFor(String language) {
        if (language == null) return "en";
        String lower = language.toLowerCase(Locale.ROOT);
        for (String l : LOCALES) if (lower.startsWith(l)) return l;
        return "en";
    }

    private I18nUtils() {}
}
