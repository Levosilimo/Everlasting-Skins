package levosilimo.everlastingskins.util;

import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

public final class I18nUtils {
    private static volatile I18nUtils INSTANCE;

    private static final Map<String, Map<String, String>> localizedStrings = new HashMap<>();
    static {
        Map<String, String> russianStrings = new HashMap<>();
        russianStrings.put("change", "Обрабатываем...");
        russianStrings.put("fulfilled_force", "Оператор изменил ваш скин.");
        russianStrings.put("fulfilled", "Скин применён.");
        russianStrings.put("error", "Возникла ошибка при обработке скина.");
        russianStrings.put("timeout","Тайм-аут получения скина.");
        russianStrings.put("no_source", "Скин не установлен");
        localizedStrings.put("ru", russianStrings);

        Map<String, String> ukrainianStrings = new HashMap<>();
        ukrainianStrings.put("change", "Опрацьовуємо...");
        ukrainianStrings.put("fulfilled_force", "Оператор змінив ваш скін.");
        ukrainianStrings.put("fulfilled", "Скін застосовано.");
        ukrainianStrings.put("error", "Сталася помилка при обробці скіна.");
        ukrainianStrings.put("timeout","Тайм-аут отримання скіна.");
        ukrainianStrings.put("no_source", "Cкіна не встановлено");
        localizedStrings.put("uk", ukrainianStrings);

        Map<String, String> englishStrings = new HashMap<>();
        englishStrings.put("change", "Processing...");
        englishStrings.put("fulfilled_force", "Operator changed your skin.");
        englishStrings.put("fulfilled", "Skin has been applied.");
        englishStrings.put("error", "Skin process error occurred.");
        englishStrings.put("timeout","Skin fetch timeout occurred.");
        englishStrings.put("no_source", "Skin is not set");
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
        Path localizationsDir = SkinRestorer.server.getServerDirectory().resolve("config/EverlastingSkins/");
        try {
            Files.createDirectories(localizationsDir);
        } catch (IOException e) {
            EverlastingSkins.logger.error("Failed to create i18n directory.", e);
        }
        createLocalizationFiles(localizationsDir);
        loadProperties(localizationsDir);
    }

    private void loadProperties(Path localizationsDir) {
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
        Map<String, String> localizedMap = localizedStrings.get(locale);
        if (localizedMap != null) {
            return localizedMap.getOrDefault(key, key);
        }
        return key;
    }
}
