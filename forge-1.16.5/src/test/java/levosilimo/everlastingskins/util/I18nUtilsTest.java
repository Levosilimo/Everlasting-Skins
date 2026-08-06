/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import levosilimo.everlastingskins.Config;
import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the 1.16.5 lane's own {@link I18nUtils} — JSON locale loading,
 * locale-code normalization, per-locale lookup, format-arg filling and the
 * Config override chain.
 *
 * <p>Locales are loaded from the classpath resources
 * {@code /assets/everlastingskins/lang/<locale>.json} via
 * {@link I18nUtils#loadAll()}. The English file is complete; de_de carries a
 * partial key set and falls back to English for the rest.
 */
class I18nUtilsTest {

    @BeforeAll
    static void loadConfigAndLocales() {
        // Serve Config defaults so Config.LANGUAGE is readable.
        Config.COMMON_CONFIG.setConfig(
                InMemoryCommentedFormat.defaultInstance().createConfig(java.util.HashMap::new));
        I18nUtils.loadAll();
    }

    @Nested
    @DisplayName("defaultLocaleFor")
    class DefaultLocaleFor {

        @ParameterizedTest
        @CsvSource({
            "en_us, en",
            "zh_cn, zh_cn",
            "en_ud, en",
            "EN_GB, en",
            "ru_ru, ru",
            "uk_ua, uk",
            "de_de, de_de"
        })
        @DisplayName("Normalizes Minecraft locale codes against loaded files")
        void normalization(String raw, String expected) {
            assertEquals(expected, I18nUtils.defaultLocaleFor(raw));
        }

        @Test
        @DisplayName("null locale maps to the default locale")
        void nullLocaleMapsToDefault() {
            assertEquals("en", I18nUtils.defaultLocaleFor(null));
        }
    }

    @Nested
    @DisplayName("getLocalizedString")
    class GetLocalizedString {

        @Test
        @DisplayName("Resolves a key from the requested locale file")
        void resolvesFromLocaleFile() {
            String german = I18nUtils.getLocalizedString("discord_announce", "de_de");
            assertTrue(german.contains("geändert"), "expected German text, got: " + german);
        }

        @Test
        @DisplayName("Unsupported locale falls back to English")
        void fallsBackToEnglish() {
            String result = I18nUtils.getLocalizedString("discord_announce", "zz_zz");
            assertTrue(result.contains("changed their skin"), "expected English fallback, got: " + result);
            assertNotEquals("discord_announce", result);
        }

        @Test
        @DisplayName("Missing key falls back to the raw key")
        void missingKeyReturnsRawKey() {
            assertEquals("no_such_key_ever", I18nUtils.getLocalizedString("no_such_key_ever", "en"));
        }

        @Test
        @DisplayName("null key returns null")
        void nullKeyReturnsNull() {
            assertNull(I18nUtils.getLocalizedString(null, "en"));
        }
    }

    @Nested
    @DisplayName("Config override chain")
    class ConfigOverride {

        @Test
        @DisplayName("A customized messages_* entry overrides the locale files")
        void configOverrideWins() {
            String original = Config.MESSAGES_CHANGE.get();
            try {
                Config.MESSAGES_CHANGE.set("Custom change text");
                assertEquals("Custom change text", I18nUtils.getLocalizedString("change", "en"));
            } finally {
                Config.MESSAGES_CHANGE.set(original);
            }
        }

        @Test
        @DisplayName("The canonical default is not treated as an override")
        void canonicalDefaultNotAnOverride() {
            // Default equals DEFAULT_ENGLISH, so the locale file still serves.
            String result = I18nUtils.getLocalizedString("change", "en");
            assertEquals("Skin change queued", result);
        }
    }

    @Nested
    @DisplayName("get / format")
    class Get {

        @Test
        @DisplayName("Fills %s specifiers from args")
        void fillsFormatArgs() {
            String result = I18nUtils.get("no_skin_found", "Notch");
            assertEquals("No skin found for \"Notch\"", result);
        }

        @Test
        @DisplayName("Malformed admin template falls back to the raw template")
        void malformedTemplateReturnsTemplate() {
            String original = Config.MESSAGES_CHANGE.get();
            try {
                Config.MESSAGES_CHANGE.set("bad %s %d template");
                // get() fills %s from args; the %d conversion throws and the
                // template is returned unformatted rather than crashing.
                String result = I18nUtils.get("change", "x");
                assertEquals("bad %s %d template", result);
            } finally {
                Config.MESSAGES_CHANGE.set(original);
            }
        }
    }
}
