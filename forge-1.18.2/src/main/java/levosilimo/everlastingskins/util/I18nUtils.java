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
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;

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
 * language code through {@link #defaultLocaleFor(String)}. Resolution order:
 * Config override (admin per-server customization), the resolved locale file,
 * the built-in English file, then the raw key.
 */
public final class I18nUtils {
    private static final Gson GSON = new Gson();
    private static final Map<String, Map<String, String>> LOCALES = new ConcurrentHashMap<>();

    /** Locale code sent to clients that map to nothing; also the normalization fallback. */
    private static final String DEFAULT_LOCALE = "en";

    private static final List<String> LOCALE_FILES = List.of(
            "en", "ru", "uk",
            "zh_cn", "es_es", "pt_br", "de_de", "fr_fr", "ja_jp", "ko_kr", "it_it");

    /** Canonical English text per message key; mirrors the Messages config defaults. */
    private static final Map<String, String> DEFAULT_ENGLISH = Map.ofEntries(
            Map.entry("change", "Skin change queued"),
            Map.entry("fulfilled", "Skin has been applied."),
            Map.entry("timeout", "Skin fetch timed out."),
            Map.entry("error", "Skin fetch failed."),
            Map.entry("restored_from", "Skin restored from %s"),
            Map.entry("cleared_no_profile", "Skin cleared (no Mojang profile found)"),
            Map.entry("stored_from_other_username", "Skin already stored from Mojang as %s; run /skin clear to switch usernames"),
            Map.entry("no_source", "No source available"),
            Map.entry("player_only", "Player only command"),
            Map.entry("permission_denied", "Permission denied"),
            Map.entry("cooldown", "Please wait %ds before using /skin again"),
            Map.entry("rate_limited", "Too many /skin commands. Try again later."),
            Map.entry("no_skin_found", "No skin found for \"%s\""),
            Map.entry("no_skin_found_plain", "No skin found"),
            Map.entry("mineskin_rejected", "MineSkin rejected the URL"),
            Map.entry("no_random_username", "No random username available"),
            Map.entry("provider_no_result", "Provider returned no result"),
            Map.entry("metrics_top_players", "Top players by refresh count:"),
            Map.entry("metrics_refreshes", " refreshes"),
            Map.entry("metrics_no_refreshes", "(no refreshes recorded)"),
            Map.entry("metrics_cleanup", "Metrics cleanup: pruned %d stale player entries"),
            Map.entry("metrics_reset", "Metrics reset"),
            Map.entry("discord_announce", "**%s** changed their skin to: `%s`"));

    /** Message keys that expose a {@code messages_*} Config entry for per-server override. */
    private static final Map<String, ForgeConfigSpec.ConfigValue<String>> CONFIG_MESSAGES = Map.ofEntries(
            Map.entry("change", Config.MESSAGES_CHANGE),
            Map.entry("fulfilled", Config.MESSAGES_FULFILLED),
            Map.entry("timeout", Config.MESSAGES_TIMEOUT),
            Map.entry("error", Config.MESSAGES_ERROR),
            Map.entry("restored_from", Config.MESSAGES_RESTORED_FROM),
            Map.entry("cleared_no_profile", Config.MESSAGES_CLEARED_NO_PROFILE),
            Map.entry("no_source", Config.MESSAGES_NO_SOURCE),
            Map.entry("player_only", Config.MESSAGES_PLAYER_ONLY),
            Map.entry("permission_denied", Config.MESSAGES_PERMISSION_DENIED),
            Map.entry("cooldown", Config.MESSAGES_COOLDOWN),
            Map.entry("rate_limited", Config.MESSAGES_RATE_LIMITED),
            Map.entry("no_skin_found", Config.MESSAGES_NO_SKIN_FOUND),
            Map.entry("no_skin_found_plain", Config.MESSAGES_NO_SKIN_FOUND_PLAIN),
            Map.entry("mineskin_rejected", Config.MESSAGES_MINESKIN_REJECTED),
            Map.entry("no_random_username", Config.MESSAGES_NO_RANDOM_USERNAME),
            Map.entry("provider_no_result", Config.MESSAGES_PROVIDER_NO_RESULT),
            Map.entry("metrics_top_players", Config.MESSAGES_METRICS_TOP_PLAYERS),
            Map.entry("metrics_refreshes", Config.MESSAGES_METRICS_REFRESHES),
            Map.entry("metrics_no_refreshes", Config.MESSAGES_METRICS_NO_REFRESHES),
            Map.entry("metrics_cleanup", Config.MESSAGES_METRICS_CLEANUP),
            Map.entry("metrics_reset", Config.MESSAGES_METRICS_RESET),
            Map.entry("discord_announce", Config.MESSAGES_DISCORD_ANNOUNCE));

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
        if (player != null) {
            locale = player.getLanguage();
        }
        if (locale == null || locale.isEmpty()) locale = Config.LANGUAGE.get();
        return getLocalizedString(key, locale);
    }

    /**
     * Static locale lookup with the override chain: Config override, resolved
     * locale file, default English file, then the raw key.
     */
    public static String getLocalizedString(String key, String locale) {
        if (key == null) return null;

        // 1. Config override (admin per-server customization)
        String configOverride = getConfigOverride(key);
        if (configOverride != null) return configOverride;

        // 2. Locale file for the resolved locale
        Map<String, String> translations = LOCALES.get(defaultLocaleFor(locale));
        if (translations != null && translations.containsKey(key)) {
            return translations.get(key);
        }

        // 3. Default locale (en)
        Map<String, String> defaults = LOCALES.get(DEFAULT_LOCALE);
        if (defaults != null && defaults.containsKey(key)) {
            return defaults.get(key);
        }

        // 4. Raw key
        return key;
    }

    /**
     * Look up a Config override for the given key.
     * Returns the override if defined (and not equal to the canonical default).
     */
    private static String getConfigOverride(String key) {
        ForgeConfigSpec.ConfigValue<String> configValue = CONFIG_MESSAGES.get(key);
        if (configValue == null) return null;
        String userValue;
        try {
            userValue = configValue.get();
        } catch (Exception e) {
            // Config may not be loaded yet; locale defaults still apply.
            EverlastingSkins.logger.debug("Message config '{}' unavailable, using locale defaults", key, e);
            return null;
        }
        if (userValue == null || userValue.isEmpty()) return null;
        // Equal to the canonical default ⇒ admin did not customize this message.
        if (userValue.equals(DEFAULT_ENGLISH.get(key))) return null;
        return userValue;
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
        return new TextComponent(get(key, args));
    }

    /** Per-player variant of {@link #get}. */
    public static Component getLocalizedComponent(String key, ServerPlayer player, Object... args) {
        return new TextComponent(formatMessage(key, player, args));
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
