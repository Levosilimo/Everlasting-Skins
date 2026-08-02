/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.util;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

public final class I18nUtils {
    private static volatile I18nUtils INSTANCE;
    private static volatile boolean initialized = false;

    /** Canonical English text per message key; mirrors the Messages config defaults. */
    private static final Map<String, String> DEFAULT_ENGLISH = Map.ofEntries(
            Map.entry("change", "Skin change queued"),
            Map.entry("fulfilled", "Skin has been applied."),
            Map.entry("timeout", "Skin fetch timed out."),
            Map.entry("error", "Skin fetch failed."),
            Map.entry("restored_from", "Skin restored from %s"),
            Map.entry("cleared_no_profile", "Skin cleared (no Mojang profile found)"),
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
            Map.entry("discord_announce", "**%s** changed their skin to: `%s`")
    );

    private static final Map<String, Map<String, String>> localizedStrings = new HashMap<>();
    static {
        Map<String, String> russianStrings = new HashMap<>();
        russianStrings.put("change", "Обрабатываем...");
        russianStrings.put("fulfilled_force", "Оператор изменил ваш скин.");
        russianStrings.put("fulfilled", "Скин применён.");
        russianStrings.put("error", "Возникла ошибка при обработке скина.");
        russianStrings.put("timeout","Тайм-аут получения скина.");
        russianStrings.put("no_source", "Скин не установлен");
        russianStrings.put("discord_announce", "**%s** изменил свой скин на: `%s`");
        localizedStrings.put("ru", russianStrings);

        Map<String, String> ukrainianStrings = new HashMap<>();
        ukrainianStrings.put("change", "Опрацьовуємо...");
        ukrainianStrings.put("fulfilled_force", "Оператор змінив ваш скін.");
        ukrainianStrings.put("fulfilled", "Скін застосовано.");
        ukrainianStrings.put("error", "Сталася помилка при обробці скіна.");
        ukrainianStrings.put("timeout","Тайм-аут отримання скіна.");
        ukrainianStrings.put("no_source", "Cкіна не встановлено");
        ukrainianStrings.put("discord_announce", "**%s** змінив свій скін на: `%s`");
        localizedStrings.put("uk", ukrainianStrings);

        Map<String, String> englishStrings = new HashMap<>();
        englishStrings.put("change", "Processing...");
        englishStrings.put("fulfilled_force", "Operator changed your skin.");
        englishStrings.put("fulfilled", "Skin has been applied.");
        englishStrings.put("error", "Skin process error occurred.");
        englishStrings.put("timeout","Skin fetch timeout occurred.");
        englishStrings.put("no_source", "No source available");
        // The full message-key inventory so generated config/EverlastingSkins/en
        // exposes every key for per-locale override.
        DEFAULT_ENGLISH.forEach(englishStrings::putIfAbsent);
        localizedStrings.put("en", englishStrings);
    }

    public static I18nUtils getInstance() {
        if (INSTANCE == null) {
            synchronized (I18nUtils.class) {
                if (INSTANCE == null) {
                    INSTANCE = new I18nUtils();
                }
            }
        }
        return INSTANCE;
    }

    private I18nUtils() {
        // Defer directory init to first use — server reference may not be set yet.
    }

    /** Lazily initialize the localization directory and load files. */
    private static void ensureInitialized() {
        if (initialized) return;
        synchronized (I18nUtils.class) {
            if (initialized) return;
            if (SkinRestorer.server == null) {
                EverlastingSkins.logger.warn("I18nUtils: server not available yet, using defaults");
                return;
            }
            Path localizationsDir = SkinRestorer.server.getServerDirectory().resolve("config/EverlastingSkins/");
            try {
                Files.createDirectories(localizationsDir);
            } catch (IOException e) {
                EverlastingSkins.logger.error("Failed to create i18n directory.", e);
            }
            createLocalizationFiles(localizationsDir);
            loadProperties(localizationsDir);
            initialized = true;
        }
    }

    private static void loadProperties(Path localizationsDir) {
        Properties properties = new Properties();

        try (Stream<Path> files = Files.walk(localizationsDir)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                InputStream input;
                try {
                    input = Files.newInputStream(file.toFile().toPath());
                    properties.load(input);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                Map<String, String> localizedMap = new HashMap<>();
                properties.forEach((key, value) -> localizedMap.put((String) key, (String) value));
                Map<String, String> defaultMap = localizedStrings.get(file.getFileName().toString());
                if (defaultMap != null) defaultMap.forEach(localizedMap::putIfAbsent);
                localizedStrings.put(file.getFileName().toString(), localizedMap);
            });
        } catch (IOException e) {
            EverlastingSkins.logger.error("Failed to load i18n properties.", e);
        }
    }

    private static void createLocalizationFiles(Path localizationsDir) {
        if (localizationsDir == null) return;
        try {
            for (Map.Entry<String, Map<String, String>> entry : localizedStrings.entrySet()) {
                String locale = entry.getKey();
                Map<String, String> strings = entry.getValue();
                Path localeFile = localizationsDir.resolve(locale);
                if (!Files.exists(localeFile)) {
                    Properties properties = new Properties();
                    for (Map.Entry<String, String> stringEntry : strings.entrySet()) {
                        properties.setProperty(stringEntry.getKey(), stringEntry.getValue());
                    }
                    try (OutputStream outputStream = Files.newOutputStream(localeFile)) {
                        properties.store(outputStream, "Localization for " + locale);
                    }
                }
            }
        } catch (IOException e) {
            EverlastingSkins.logger.error("Failed to create localization files.", e);
        }
    }

    public String getLocalizedString(String key, String locale) {
        ensureInitialized();
        Map<String, String> localizedMap = localizedStrings.get(locale);
        if (localizedMap != null) {
            return localizedMap.getOrDefault(key, key);
        }
        return key;
    }

    /**
     * Resolves a message key with the priority: operator override in the
     * Messages config section, per-locale resource file, then the Messages
     * config default, then the raw key. {@code %s}/{@code %d} specifiers in
     * the resolved template are filled from {@code args}.
     */
    public static String get(String key, Object... args) {
        if (key == null) return null;
        ensureInitialized();
        String configValue = configValue(key);
        if (configValue != null && !configValue.equals(DEFAULT_ENGLISH.get(key))) {
            return format(configValue, args);
        }
        String localized = getInstance().getLocalizedString(key, currentLocale());
        if (localized != null && !localized.equals(key)) {
            return format(localized, args);
        }
        return format(configValue != null ? configValue : key, args);
    }

    /** {@link #get} wrapped in a plain text Component for command feedback. */
    public static Component getLocalizedComponent(String key, Object... args) {
        return Component.literal(get(key, args));
    }

    private static String currentLocale() {
        try {
            return Config.LANGUAGE.get();
        } catch (Exception e) {
            return "en";
        }
    }

    /** Config entry for a message key, or null when the key has no Messages entry. */
    private static String configValue(String key) {
        ForgeConfigSpec.ConfigValue<String> config = switch (key) {
            case "change" -> Config.MESSAGES_CHANGE;
            case "fulfilled" -> Config.MESSAGES_FULFILLED;
            case "timeout" -> Config.MESSAGES_TIMEOUT;
            case "error" -> Config.MESSAGES_ERROR;
            case "restored_from" -> Config.MESSAGES_RESTORED_FROM;
            case "cleared_no_profile" -> Config.MESSAGES_CLEARED_NO_PROFILE;
            case "no_source" -> Config.MESSAGES_NO_SOURCE;
            case "player_only" -> Config.MESSAGES_PLAYER_ONLY;
            case "permission_denied" -> Config.MESSAGES_PERMISSION_DENIED;
            case "cooldown" -> Config.MESSAGES_COOLDOWN;
            case "rate_limited" -> Config.MESSAGES_RATE_LIMITED;
            case "no_skin_found" -> Config.MESSAGES_NO_SKIN_FOUND;
            case "no_skin_found_plain" -> Config.MESSAGES_NO_SKIN_FOUND_PLAIN;
            case "mineskin_rejected" -> Config.MESSAGES_MINESKIN_REJECTED;
            case "no_random_username" -> Config.MESSAGES_NO_RANDOM_USERNAME;
            case "provider_no_result" -> Config.MESSAGES_PROVIDER_NO_RESULT;
            case "metrics_top_players" -> Config.MESSAGES_METRICS_TOP_PLAYERS;
            case "metrics_refreshes" -> Config.MESSAGES_METRICS_REFRESHES;
            case "metrics_no_refreshes" -> Config.MESSAGES_METRICS_NO_REFRESHES;
            case "metrics_cleanup" -> Config.MESSAGES_METRICS_CLEANUP;
            case "metrics_reset" -> Config.MESSAGES_METRICS_RESET;
            case "discord_announce" -> Config.MESSAGES_DISCORD_ANNOUNCE;
            default -> null;
        };
        if (config == null) return null;
        try {
            return config.get();
        } catch (Exception e) {
            // Config may not be loaded yet; locale defaults still apply.
            EverlastingSkins.logger.debug("Message config '{}' unavailable, using locale defaults", key, e);
            return null;
        }
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
