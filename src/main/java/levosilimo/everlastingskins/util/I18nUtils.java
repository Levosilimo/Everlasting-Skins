package levosilimo.everlastingskins.util;

import net.minecraft.server.MinecraftServer;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;

import java.io.IOException;
import java.io.InputStream;
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
    private static final String[] LOCALES = { "en", "ru", "uk" };

    public static String getLocalizedString(String key, String locale) {
        if (key == null || locale == null) return key;
        Map<String, String> map = localizedStrings.get(locale);
        if (map == null) return key;
        String value = map.get(key);
        return value != null ? value : key;
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
        Properties merged = new Properties();
        if (resourceUrl != null) {
            try (InputStream is = resourceUrl.openStream()) {
                merged.load(is);
            } catch (IOException ignored) {}
        }
        if (Files.exists(override)) {
            try (InputStream is = Files.newInputStream(override)) { merged.load(is); }
            catch (IOException ignored) {}
        }
        Map<String, String> map = new HashMap<>();
        for (String k : merged.stringPropertyNames()) map.put(k, merged.getProperty(k));
        localizedStrings.put(locale, map);
    }

    public static String defaultLocaleFor(String language) {
        if (language == null) return "en";
        String lower = language.toLowerCase(Locale.ROOT);
        for (String l : LOCALES) if (lower.startsWith(l)) return l;
        return "en";
    }

    private I18nUtils() {}
}
