/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * i18n service backed by JSON resource files at
 * {@code /assets/everlastingskins/lang/<locale>.json}. Loaded once at server
 * start via {@link #loadAll()}; per-player lookups resolve the client's
 * language code through {@link #defaultLocaleFor(String)} and fall back to
 * {@code Config.LANGUAGE}, then the built-in English file, then the raw key.
 */
public final class I18nUtils {
    private static final Gson GSON = new Gson();
    private static final Map<String, Map<String, String>> LOCALES = new ConcurrentHashMap<>();

    /** Locale code sent to clients that map to nothing; also the normalization fallback. */
    private static final String DEFAULT_LOCALE = "en_us";
    /** Map key of the complete English file, used when the resolved locale misses a key. */
    private static final String FALLBACK_LOCALE = "en";

    private static final List<String> LOCALE_FILES = List.of(
            "en", "ru", "uk",
            "zh_cn", "es_es", "pt_br", "de_de", "fr_fr", "ja_jp", "ko_kr", "it_it");

    private I18nUtils() {}

    /** Load every locale resource from the classpath into memory. Idempotent per startup. */
    public static void loadAll() {
        for (String locale : LOCALE_FILES) {
            loadLocale(locale);
        }
    }

    private static void loadLocale(String locale) {
        String path = "/assets/everlastingskins/lang/" + locale + ".json";
        try (InputStream is = I18nUtils.class.getResourceAsStream(path)) {
            if (is == null) {
                EverlastingSkins.logger.warn("I18nUtils: locale resource {} not found", path);
                return;
            }
            Map<String, String> translations = GSON.fromJson(
                    new InputStreamReader(is, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, String>>() { }.getType());
            LOCALES.put(locale, translations != null ? translations : new HashMap<>());
        } catch (IOException | RuntimeException e) {
            EverlastingSkins.logger.warn("I18nUtils: failed to load locale {}: {}", locale, e.getMessage());
        }
    }

    /**
     * Normalize a Minecraft locale code against the loaded locale files:
     * exact match first ({@code zh_cn} → {@code zh_cn}), then language-only
     * prefix ({@code en_us} → {@code en}), then {@link #DEFAULT_LOCALE}.
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

    /** Per-player locale lookup; falls back to Config.LANGUAGE when the player is null. */
    public static String getLocalizedString(String key, ServerPlayer player) {
        String locale = null;
        if (player != null && player.clientInformation() != null) {
            locale = player.clientInformation().language();
        }
        if (locale == null || locale.isEmpty()) locale = Config.LANGUAGE.get();
        return getLocalizedString(key, locale);
    }

    /** Static locale lookup with fallback: resolved locale, English, then the raw key. */
    public static String getLocalizedString(String key, String locale) {
        if (key == null) return null;
        Map<String, String> translations = LOCALES.get(defaultLocaleFor(locale));
        if (translations != null && translations.containsKey(key)) {
            return translations.get(key);
        }
        Map<String, String> defaults = LOCALES.get(FALLBACK_LOCALE);
        if (defaults != null && defaults.containsKey(key)) {
            return defaults.get(key);
        }
        return key;
    }

    /**
     * Resolves a message key against the global locale (Config.LANGUAGE) and
     * fills {@code %s}/{@code %d} specifiers from {@code args}. Raw key is the
     * last fallback, so callers never see null.
     */
    public static String get(String key, Object... args) {
        if (key == null) return null;
        String resolved = getLocalizedString(key, Config.LANGUAGE.get());
        return format(resolved, args);
    }

    /** {@link #get} wrapped in a plain text Component for command feedback. */
    public static Component getLocalizedComponent(String key, Object... args) {
        return Component.literal(get(key, args));
    }

    /** Per-player variant of {@link #get}. */
    public static Component getLocalizedComponent(String key, ServerPlayer player, Object... args) {
        return Component.literal(formatMessage(key, player, args));
    }

    /** Resolves a template for {@code player} and fills format args. */
    public static String formatMessage(String key, ServerPlayer player, Object... args) {
        return format(getLocalizedString(key, player), args);
    }

    private static String format(String template, Object... args) {
        if (args == null || args.length == 0) return template;
        try {
            return String.format(template, args);
        } catch (IllegalFormatException e) {
            // Message templates are admin-editable; a malformed one must not break commands.
            EverlastingSkins.logger.debug("Invalid message format string: '{}'", template, e);
            return template;
        }
    }
}
