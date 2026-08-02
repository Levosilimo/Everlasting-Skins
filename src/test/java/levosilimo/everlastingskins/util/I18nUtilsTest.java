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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link I18nUtils} — JSON locale loading, locale-code
 * normalization, per-player lookup, and format-arg filling.
 *
 * <p>Locales are loaded from the classpath resources
 * {@code /assets/everlastingskins/lang/<locale>.json} via
 * {@link I18nUtils#loadAll()}. The English file is complete; ru/uk carry a
 * partial legacy key set and fall back to English for the rest; the other
 * eight locale files are empty placeholders (fall back to English).</p>
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
            "uk_ua, uk"
        })
        @DisplayName("Normalizes Minecraft locale codes against loaded files")
        void normalization(String raw, String expected) {
            assertEquals(expected, I18nUtils.defaultLocaleFor(raw));
        }

        @Test
        @DisplayName("Null locale falls back to the default constant")
        void nullFallsBackToDefault() {
            assertEquals("en_us", I18nUtils.defaultLocaleFor(null));
        }

        @Test
        @DisplayName("Unknown locale falls back to the default constant")
        void unknownFallsBackToDefault() {
            assertEquals("en_us", I18nUtils.defaultLocaleFor("xx_xx"));
            assertEquals("en_us", I18nUtils.defaultLocaleFor("pl"));
        }
    }

    @Nested
    @DisplayName("getLocalizedString")
    class GetLocalizedString {

        @Test
        @DisplayName("Known key with valid locale returns translation")
        void knownKeyWithValidLocale() {
            assertEquals("Skin change queued", I18nUtils.getLocalizedString("change", "en"));
            assertEquals("Запрос на смену скина отправлен", I18nUtils.getLocalizedString("change", "ru"));
            assertEquals("Запит на зміну скіна надіслано", I18nUtils.getLocalizedString("change", "uk"));
        }

        @Test
        @DisplayName("Client-style codes resolve through defaultLocaleFor")
        void clientStyleCodesResolve() {
            assertEquals("Skin change queued", I18nUtils.getLocalizedString("change", "en_us"));
            assertEquals("Запрос на смену скина отправлен", I18nUtils.getLocalizedString("change", "ru_ru"));
            assertEquals("Скін успішно застосовано.", I18nUtils.getLocalizedString("fulfilled", "uk_ua"));
        }

        @Test
        @DisplayName("Fulfilled force returns operator-changed message")
        void fulfilledForce() {
            assertEquals("Operator changed your skin.", I18nUtils.getLocalizedString("fulfilled_force", "en"));
            assertEquals("Оператор изменил ваш скин.", I18nUtils.getLocalizedString("fulfilled_force", "ru"));
            assertEquals("Оператор змінив ваш скін.", I18nUtils.getLocalizedString("fulfilled_force", "uk"));
        }

        @Test
        @DisplayName("ru/uk now carry the full key set, not just legacy keys")
        void ruUkFullCoverage() {
            assertEquals("Метрики сброшены", I18nUtils.getLocalizedString("metrics_reset", "ru"));
            assertEquals("Недостаточно прав", I18nUtils.getLocalizedString("permission_denied", "ru"));
            assertEquals("Метрики скинуто", I18nUtils.getLocalizedString("metrics_reset", "uk"));
            assertEquals("Скін не знайдено", I18nUtils.getLocalizedString("no_skin_found_plain", "uk"));
        }

        @Test
        @DisplayName("Placeholder locales now carry natural translations")
        void placeholderLocalesTranslated() {
            assertEquals("皮肤更换已请求", I18nUtils.getLocalizedString("change", "zh_cn"));
            assertEquals("Cambio de aspecto solicitado", I18nUtils.getLocalizedString("change", "es_es"));
            assertEquals("Скин не найден", I18nUtils.getLocalizedString("no_skin_found_plain", "ru"));
        }

        @Test
        @DisplayName("Unknown key returns key itself as fallback")
        void unknownKeyReturnsKey() {
            assertEquals("nonexistent_key_xyz", I18nUtils.getLocalizedString("nonexistent_key_xyz", "en"));
            assertEquals("nonexistent_key_xyz", I18nUtils.getLocalizedString("nonexistent_key_xyz", "ru"));
        }

        @Test
        @DisplayName("Unknown locale falls back to English text, not the raw key")
        void unknownLocaleReturnsEnglish() {
            assertEquals("Skin change queued", I18nUtils.getLocalizedString("change", "de"));
            assertEquals("Skin change queued", I18nUtils.getLocalizedString("change", "fr"));
        }

        @Test
        @DisplayName("Null key returns null")
        void nullKey() {
            assertNull(I18nUtils.getLocalizedString(null, "en"));
            assertNull(I18nUtils.getLocalizedString(null, "ru"));
        }

        @Test
        @DisplayName("Null locale resolves through the default constant")
        void nullLocaleResolvesEnglish() {
            assertEquals("Skin change queued", I18nUtils.getLocalizedString("change", (String) null));
        }

        @Test
        @DisplayName("Null key and null locale returns null")
        void nullKeyAndNullLocale() {
            assertNull(I18nUtils.getLocalizedString(null, (String) null));
        }

        @Test
        @DisplayName("All keys in en.json resolve to non-empty text")
        void allEnglishKeysResolve() {
            String[] keys = {"change", "fulfilled", "fulfilled_force", "timeout", "error",
                "restored_from", "cleared_no_profile", "no_source", "player_only",
                "permission_denied", "cooldown", "rate_limited", "no_skin_found",
                "no_skin_found_plain", "mineskin_rejected", "no_random_username",
                "provider_no_result", "metrics_top_players", "metrics_refreshes",
                "metrics_no_refreshes", "metrics_cleanup", "metrics_reset",
                "discord_announce"};
            for (String key : keys) {
                String result = I18nUtils.getLocalizedString(key, "en");
                assertNotNull(result, () -> "Key '" + key + "' produced null");
                assertNotEquals(key, result, () -> "Key '" + key + "' not translated");
                assertFalse(result.isEmpty(), () -> "Key '" + key + "' produced empty string");
            }
        }
    }

    @Nested
    @DisplayName("get / getLocalizedComponent")
    class Get {

        @Test
        @DisplayName("Existing key uses global locale text (Config.LANGUAGE)")
        void existingKeyUsesLocaleText() {
            assertEquals("Skin change queued", I18nUtils.get("change"));
        }

        @Test
        @DisplayName("Format specifiers are applied to the resolved template")
        void formatArgsApplied() {
            assertEquals("No skin found for \"Steve\"", I18nUtils.get("no_skin_found", "Steve"));
            assertEquals("Metrics cleanup: pruned 3 stale player entries", I18nUtils.get("metrics_cleanup", 3));
            assertEquals("Please wait 2s before using /skin again", I18nUtils.get("cooldown", 2));
        }

        @Test
        @DisplayName("Malformed template returns the raw template")
        void malformedTemplateReturnsRaw() {
            assertEquals("Skin restored from %s", I18nUtils.get("restored_from"));
        }

        @Test
        @DisplayName("Null key returns null")
        void nullKey() {
            assertNull(I18nUtils.get(null));
        }

        @Test
        @DisplayName("getLocalizedComponent wraps get() in a literal component")
        void componentWrap() {
            assertEquals("Skin change queued", I18nUtils.getLocalizedComponent("change").getString());
        }
    }

    @Nested
    @DisplayName("Per-player lookup")
    class PerPlayer {

        @Test
        @DisplayName("Null player falls back to Config.LANGUAGE")
        void nullPlayerFallsBackToGlobal() {
            assertEquals("Skin change queued", I18nUtils.getLocalizedString("change", (net.minecraft.server.level.ServerPlayer) null));
        }

        @Test
        @DisplayName("formatMessage fills args with null player")
        void formatMessageWithNullPlayer() {
            assertEquals("No skin found for \"Steve\"",
                    I18nUtils.formatMessage("no_skin_found", null, "Steve"));
            assertEquals("Metrics cleanup: pruned 5 stale player entries",
                    I18nUtils.formatMessage("metrics_cleanup", null, 5));
        }

        @Test
        @DisplayName("getLocalizedComponent per-player overload wraps formatMessage")
        void componentOverload() {
            assertEquals("Skin change queued",
                    I18nUtils.getLocalizedComponent("change", (net.minecraft.server.level.ServerPlayer) null).getString());
        }
    }
}
