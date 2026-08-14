/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge164.util;

import levosilimo.everlastingskins.forge164.EverlastingSkins;
import levosilimo.everlastingskins.forge164.config.Config;
import levosilimo.everlastingskins.i18n.LanguageKeys;
import net.minecraft.src.StatCollector;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * i18n service for the 1.6.4 lane — ported verbatim from the forge-1.7.10
 * reference (PR #477); only the package and the {@link StatCollector}
 * import changed (1.6.4 MCP domain is {@code net.minecraft.src.*}).
 *
 * <p>Backed by {@code .lang} resource files at
 * {@code /assets/everlastingskins/lang/<locale>.lang} (11 locales, keys from
 * {@link LanguageKeys}), loaded once at server start via {@link #loadAll()}.
 * Resolution order: the resolved locale file (from {@link Config#LANGUAGE}),
 * the built-in English file, the game's own {@link StatCollector} table
 * (1.6.4 {@code StringTranslate} returns the raw key when absent), then the
 * raw key. The 1.6.4 server has no per-player locale surface (that arrives
 * with the 1.13+ client information packet), so lookups use the
 * server/admin language from {@link Config#LANGUAGE} only.
 */
public final class I18nUtils {

    private static final Map<String, Map<String, String>> LOCALES = new HashMap<String, Map<String, String>>();

    /** Locale code used as the normalization + text fallback. */
    private static final String DEFAULT_LOCALE = "en";

    private I18nUtils() {}

    /** Loads every locale resource from the classpath. Idempotent per startup. */
    public static void loadAll() {
        LOCALES.clear();
        for (String locale : LanguageKeys.LOCALES) {
            loadLocale(locale);
        }
    }

    private static void loadLocale(String locale) {
        String path = "/assets/everlastingskins/lang/" + locale + ".lang";
        Properties props = new Properties();
        try (InputStream is = I18nUtils.class.getResourceAsStream(path)) {
            if (is == null) {
                EverlastingSkins.logger.warn("I18nUtils: locale resource {} not found", path);
                return;
            }
            props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            EverlastingSkins.logger.warn("I18nUtils: failed to load locale {}: {}", locale, e.getMessage());
            return;
        }
        Map<String, String> map = new HashMap<String, String>();
        for (String key : props.stringPropertyNames()) {
            map.put(key, props.getProperty(key));
        }
        LOCALES.put(locale, map);
    }

    /**
     * Normalizes a language code against the loaded locale files: exact
     * match first ({@code zh_cn} → {@code zh_cn}), then language-only prefix
     * ({@code en_us} → {@code en}), then {@link #DEFAULT_LOCALE}.
     */
    public static String defaultLocaleFor(String locale) {
        if (locale == null) return DEFAULT_LOCALE;
        String lang = locale.toLowerCase(Locale.ROOT);
        if (LOCALES.containsKey(lang)) return lang;
        int sep = lang.indexOf('_');
        if (sep > 0) {
            String shortLang = lang.substring(0, sep);
            if (LOCALES.containsKey(shortLang)) return shortLang;
        }
        return DEFAULT_LOCALE;
    }

    /**
     * Static locale lookup: resolved locale file, English file, the game's
     * {@link StatCollector} table, then the raw key — callers never see null.
     */
    public static String getLocalizedString(String key, String locale) {
        if (key == null) return null;
        Map<String, String> translations = LOCALES.get(defaultLocaleFor(locale));
        if (translations != null && translations.containsKey(key)) {
            return translations.get(key);
        }
        Map<String, String> defaults = LOCALES.get(DEFAULT_LOCALE);
        if (defaults != null && defaults.containsKey(key)) {
            return defaults.get(key);
        }
        // The game's own table (vanilla + any mod .lang the server loaded);
        // returns the key itself when absent.
        return StatCollector.translateToLocal(key);
    }

    /**
     * Resolves a message key against {@link Config#LANGUAGE} and fills
     * {@code %s}/{@code %d} specifiers from {@code args}. Raw key is the
     * last fallback, so callers never see null.
     */
    public static String get(String key, Object... args) {
        if (key == null) return null;
        String resolved = getLocalizedString(key, Config.LANGUAGE);
        return format(resolved, args);
    }

    private static String format(String template, Object... args) {
        if (args == null || args.length == 0) return template;
        try {
            return String.format(template, args);
        } catch (IllegalFormatException e) {
            // Admin-editable locale files must not break commands.
            EverlastingSkins.logger.debug("Invalid message format string: '{}'", template, e);
            return template;
        }
    }
}
