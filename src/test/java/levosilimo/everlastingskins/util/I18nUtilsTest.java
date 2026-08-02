/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.util;

import levosilimo.everlastingskins.Config;
import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link I18nUtils} — locale loading, property merging, and the
 * config-first message lookup.
 *
 * <p>I18nUtils uses a static initializer to populate three built-in locales
 * (en, ru, uk). The en locale carries the full message-key inventory; ru/uk
 * carry the six legacy keys.  {@code ensureInitialized()} is a no-op when
 * {@code SkinRestorer.server == null} (the normal test environment), so
 * file-backed property loading is not exercised; only compile-time defaults
 * are tested here.</p>
 */
class I18nUtilsTest {

    @BeforeAll
    static void loadConfig() {
        // Serve Config defaults so the Messages config override path is testable.
        Config.COMMON_CONFIG.setConfig(
                InMemoryCommentedFormat.defaultInstance().createConfig(java.util.HashMap::new));
    }

    @Nested
    @DisplayName("getLocalizedString")
    class GetLocalizedString {

        @Test
        @DisplayName("Known key with valid locale returns translation")
        void knownKeyWithValidLocale() {
            I18nUtils i18n = I18nUtils.getInstance();
            assertEquals("Processing...", i18n.getLocalizedString("change", "en"));
            assertEquals("Обрабатываем...", i18n.getLocalizedString("change", "ru"));
            assertEquals("Опрацьовуємо...", i18n.getLocalizedString("change", "uk"));
        }

        @Test
        @DisplayName("Fulfilled force returns operator-changed message")
        void fulfilledForce() {
            I18nUtils i18n = I18nUtils.getInstance();
            assertEquals("Operator changed your skin.", i18n.getLocalizedString("fulfilled_force", "en"));
            assertEquals("Оператор изменил ваш скин.", i18n.getLocalizedString("fulfilled_force", "ru"));
            assertEquals("Оператор змінив ваш скін.", i18n.getLocalizedString("fulfilled_force", "uk"));
        }

        @Test
        @DisplayName("Fulfilled returns skin-applied message")
        void fulfilled() {
            I18nUtils i18n = I18nUtils.getInstance();
            assertEquals("Skin has been applied.", i18n.getLocalizedString("fulfilled", "en"));
            assertEquals("Скин применён.", i18n.getLocalizedString("fulfilled", "ru"));
            assertEquals("Скін застосовано.", i18n.getLocalizedString("fulfilled", "uk"));
        }

        @Test
        @DisplayName("Error returns skin-error message")
        void errorKey() {
            I18nUtils i18n = I18nUtils.getInstance();
            assertEquals("Skin process error occurred.", i18n.getLocalizedString("error", "en"));
            assertEquals("Возникла ошибка при обработке скина.", i18n.getLocalizedString("error", "ru"));
            assertEquals("Сталася помилка при обробці скіна.", i18n.getLocalizedString("error", "uk"));
        }

        @Test
        @DisplayName("Timeout returns timeout message")
        void timeoutKey() {
            I18nUtils i18n = I18nUtils.getInstance();
            assertEquals("Skin fetch timeout occurred.", i18n.getLocalizedString("timeout", "en"));
            assertEquals("Тайм-аут получения скина.", i18n.getLocalizedString("timeout", "ru"));
            assertEquals("Тайм-аут отримання скіна.", i18n.getLocalizedString("timeout", "uk"));
        }

        @Test
        @DisplayName("No source returns skin-not-set message")
        void noSourceKey() {
            I18nUtils i18n = I18nUtils.getInstance();
            assertEquals("No source available", i18n.getLocalizedString("no_source", "en"));
            assertEquals("Скин не установлен", i18n.getLocalizedString("no_source", "ru"));
            assertEquals("Cкіна не встановлено", i18n.getLocalizedString("no_source", "uk"));
        }

        @Test
        @DisplayName("Unknown key returns key itself as fallback")
        void unknownKeyReturnsKey() {
            I18nUtils i18n = I18nUtils.getInstance();
            assertEquals("nonexistent_key_xyz", i18n.getLocalizedString("nonexistent_key_xyz", "en"));
            assertEquals("nonexistent_key_xyz", i18n.getLocalizedString("nonexistent_key_xyz", "ru"));
            assertEquals("nonexistent_key_xyz", i18n.getLocalizedString("nonexistent_key_xyz", "uk"));
        }

        @Test
        @DisplayName("Unknown locale returns key itself")
        void unknownLocaleReturnsKey() {
            I18nUtils i18n = I18nUtils.getInstance();
            assertEquals("change", i18n.getLocalizedString("change", "de"));
            assertEquals("change", i18n.getLocalizedString("change", "fr"));
            assertEquals("change", i18n.getLocalizedString("change", "es"));
            assertEquals("change", i18n.getLocalizedString("change", "pl"));
        }

        @Test
        @DisplayName("Null key with valid locale returns null")
        void nullKeyWithValidLocale() {
            I18nUtils i18n = I18nUtils.getInstance();
            assertNull(i18n.getLocalizedString(null, "en"));
            assertNull(i18n.getLocalizedString(null, "ru"));
            assertNull(i18n.getLocalizedString(null, "uk"));
        }

        @Test
        @DisplayName("Null locale returns key itself")
        void nullLocaleReturnsKey() {
            I18nUtils i18n = I18nUtils.getInstance();
            assertEquals("change", i18n.getLocalizedString("change", null));
            assertEquals("error", i18n.getLocalizedString("error", null));
        }

        @Test
        @DisplayName("Null key and null locale returns null")
        void nullKeyAndNullLocale() {
            I18nUtils i18n = I18nUtils.getInstance();
            assertNull(i18n.getLocalizedString(null, null));
        }

        @ParameterizedTest
        @CsvSource({
            "en, Processing...",
            "ru, Обрабатываем...",
            "uk, Опрацьовуємо..."
        })
        @DisplayName("Parametrized: known key 'change' per locale")
        void parametrizedChangeKey(String locale, String expected) {
            I18nUtils i18n = I18nUtils.getInstance();
            assertEquals(expected, i18n.getLocalizedString("change", locale));
        }

        @Test
        @DisplayName("All known keys have translations in all built-in locales")
        void allKeysHaveTranslations() {
            I18nUtils i18n = I18nUtils.getInstance();
            String[] locales = {"en", "ru", "uk"};
            String[] keys = {"change", "fulfilled_force", "fulfilled", "error", "timeout", "no_source"};
            for (String locale : locales) {
                for (String key : keys) {
                    String result = i18n.getLocalizedString(key, locale);
                    assertNotNull(result,
                        () -> "Key '" + key + "' produced null in locale '" + locale + "'");
                    assertNotEquals(key, result,
                        () -> "Key '" + key + "' not translated in locale '" + locale + "'");
                    assertFalse(result.isEmpty(),
                        () -> "Key '" + key + "' produced empty string in locale '" + locale + "'");
                }
            }
        }
    }

    @Nested
    @DisplayName("get (config-first message lookup)")
    class Get {

        @Test
        @DisplayName("Existing key falls back to built-in locale text at defaults")
        void existingKeyUsesLocaleText() {
            assertEquals("Processing...", I18nUtils.get("change"));
        }

        @Test
        @DisplayName("New key falls back to the config default")
        void newKeyUsesConfigDefault() {
            assertEquals("Permission denied", I18nUtils.get("permission_denied"));
            assertEquals("No random username available", I18nUtils.get("no_random_username"));
        }

        @Test
        @DisplayName("Format specifiers are applied to the resolved template")
        void formatArgsApplied() {
            assertEquals("No skin found for \"Steve\"", I18nUtils.get("no_skin_found", "Steve"));
            assertEquals("Metrics cleanup: pruned 3 stale player entries", I18nUtils.get("metrics_cleanup", 3));
            assertEquals("Please wait 2s before using /skin again", I18nUtils.get("cooldown", 2));
        }

        @Test
        @DisplayName("Messages config override wins over locale text")
        void configOverrideWins() {
            ForgeConfigSpec.ConfigValue<String> cfg = Config.MESSAGES_CHANGE;
            String original = cfg.get();
            try {
                cfg.set("Custom change message");
                assertEquals("Custom change message", I18nUtils.get("change"));
            } finally {
                cfg.set(original);
            }
        }

        @Test
        @DisplayName("Null key returns null")
        void nullKey() {
            assertNull(I18nUtils.get(null));
        }

        @Test
        @DisplayName("getLocalizedComponent wraps get() in a literal component")
        void componentWrap() {
            assertEquals("Processing...", I18nUtils.getLocalizedComponent("change").getString());
        }
    }

    @Nested
    @DisplayName("getInstance (singleton)")
    class GetInstance {

        @Test
        @DisplayName("Returns same instance on repeated calls")
        void singletonReturnsSameInstance() {
            assertSame(I18nUtils.getInstance(), I18nUtils.getInstance());
            assertSame(I18nUtils.getInstance(), I18nUtils.getInstance());
        }
    }
}
