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
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.ForgeConfigSpec;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
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

    private static final List<String> LOCALE_FILES = Collections.unmodifiableList(Arrays.asList(
            "en", "ru", "uk",
            "zh_cn", "es_es", "pt_br", "de_de", "fr_fr", "ja_jp", "ko_kr", "it_it"));

    /** Canonical English text per message key; mirrors the Messages config defaults. */
    private static final Map<String, String> DEFAULT_ENGLISH = unmodifiableMapOf(
            "change", "Skin change queued",
            "fulfilled", "Skin has been applied.",
            "timeout", "Skin fetch timed out.",
            "error", "Skin fetch failed.",
            "restored_from", "Skin restored from %s",
            "cleared_no_profile", "Skin cleared (no Mojang profile found)",
            "stored_from_other_username", "Skin already stored from Mojang as %s; run /skin clear to switch usernames",
            "no_source", "No source available",
            "player_only", "Player only command",
            "permission_denied", "Permission denied",
            "cooldown", "Please wait %ds before using /skin again",
            "rate_limited", "Too many /skin commands. Try again later.",
            "no_skin_found", "No skin found for \"%s\"",
            "no_skin_found_plain", "No skin found",
            "mineskin_rejected", "MineSkin rejected the URL",
            "no_random_username", "No random username available",
            "provider_no_result", "Provider returned no result",
            "metrics_top_players", "Top players by refresh count:",
            "metrics_refreshes", " refreshes",
            "metrics_no_refreshes", "(no refreshes recorded)",
            "metrics_cleanup", "Metrics cleanup: pruned %d stale player entries",
            "metrics_reset", "Metrics reset",
            "discord_announce", "**%s** changed their skin to: `%s`");

    /** Message keys that expose a {@code messages_*} Config entry for per-server override. */
    private static final Map<String, ForgeConfigSpec.ConfigValue<String>> CONFIG_MESSAGES = unmodifiableConfigMap(
            "change", Config.MESSAGES_CHANGE,
            "fulfilled", Config.MESSAGES_FULFILLED,
            "timeout", Config.MESSAGES_TIMEOUT,
            "error", Config.MESSAGES_ERROR,
            "restored_from", Config.MESSAGES_RESTORED_FROM,
            "cleared_no_profile", Config.MESSAGES_CLEARED_NO_PROFILE,
            "no_source", Config.MESSAGES_NO_SOURCE,
            "player_only", Config.MESSAGES_PLAYER_ONLY,
            "permission_denied", Config.MESSAGES_PERMISSION_DENIED,
            "cooldown", Config.MESSAGES_COOLDOWN,
            "rate_limited", Config.MESSAGES_RATE_LIMITED,
            "no_skin_found", Config.MESSAGES_NO_SKIN_FOUND,
            "no_skin_found_plain", Config.MESSAGES_NO_SKIN_FOUND_PLAIN,
            "mineskin_rejected", Config.MESSAGES_MINESKIN_REJECTED,
            "no_random_username", Config.MESSAGES_NO_RANDOM_USERNAME,
            "provider_no_result", Config.MESSAGES_PROVIDER_NO_RESULT,
            "metrics_top_players", Config.MESSAGES_METRICS_TOP_PLAYERS,
            "metrics_refreshes", Config.MESSAGES_METRICS_REFRESHES,
            "metrics_no_refreshes", Config.MESSAGES_METRICS_NO_REFRESHES,
            "metrics_cleanup", Config.MESSAGES_METRICS_CLEANUP,
            "metrics_reset", Config.MESSAGES_METRICS_RESET,
            "discord_announce", Config.MESSAGES_DISCORD_ANNOUNCE);

    // Java 8 port: the 1.21 lane used List.of/Map.ofEntries (Java 9+),
    // which the source-level-8 javac rejects.

    private static Map<String, String> unmodifiableMapOf(String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, ForgeConfigSpec.ConfigValue<String>> unmodifiableConfigMap(
            Object... kv) {
        Map<String, ForgeConfigSpec.ConfigValue<String>> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            @SuppressWarnings("unchecked")
            ForgeConfigSpec.ConfigValue<String> value = (ForgeConfigSpec.ConfigValue<String>) kv[i + 1];
            map.put((String) kv[i], value);
        }
        return Collections.unmodifiableMap(map);
    }

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

    /**
     * Per-player locale lookup; falls back to Config.LANGUAGE when the player
     * is null. 1.16.5 port note: ServerPlayer does not expose the client
     * locale on this version (the 1.21 lane reads it from
     * {@code player.clientInformation().language()}; on 1.16.5 the locale
     * only reaches the server inside ServerboundClientInformationPacket and
     * is not retained on the player), so the per-player variant resolves
     * against Config.LANGUAGE like the null-player path.
     */
    public static String getLocalizedString(String key, ServerPlayerEntity player) {
        return getLocalizedString(key, Config.LANGUAGE.get());
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
    public static ITextComponent getLocalizedComponent(String key, Object... args) {
        return new StringTextComponent(get(key, args));
    }

    /** Per-player variant of {@link #get}. */
    public static ITextComponent getLocalizedComponent(String key, ServerPlayerEntity player, Object... args) {
        return new StringTextComponent(formatMessage(key, player, args));
    }

    /** Resolves a template for {@code player} and fills format args. */
    public static String formatMessage(String key, ServerPlayerEntity player, Object... args) {
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
