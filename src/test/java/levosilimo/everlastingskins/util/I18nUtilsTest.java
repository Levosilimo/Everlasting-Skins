/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link I18nUtils} — 1.12.2 locale resolution and fallback logic.
 *
 * <p>The 1.12.2 I18nUtils is fully static and relies on {@code loadAll()} to
 * populate its map (which requires a live {@code MinecraftServer}).  Without
 * a server the map is empty, so every key falls back to itself.  The
 * {@code defaultLocaleFor(String)} language→locale mapping is independently
 * testable without server infrastructure.</p>
 */
class I18nUtilsTest {

    private static final String[] EXPECTED_KEYS = {
        "change", "fulfilled", "timeout", "error", "restored_from", "cleared_no_profile",
        "no_source", "player_only", "permission_denied", "cooldown", "rate_limited",
        "no_skin_found", "no_skin_found_plain", "mineskin_rejected", "no_random_username",
        "provider_no_result", "metrics_top_players", "metrics_refreshes",
        "metrics_no_refreshes", "metrics_cleanup", "metrics_reset", "discord_announce"
    };

    @Nested
    @DisplayName("getLocalizedString (empty map fallback)")
    class GetLocalizedString {

        @Test
        @DisplayName("Any key returns key itself when map is empty")
        void anyKeyReturnsKey() {
            assertEquals("change", I18nUtils.getLocalizedString("change", "en"));
            assertEquals("fulfilled", I18nUtils.getLocalizedString("fulfilled", "ru"));
            assertEquals("error", I18nUtils.getLocalizedString("error", "uk"));
        }

        @Test
        @DisplayName("Unknown key returns key itself")
        void unknownKeyReturnsKey() {
            assertEquals("nonexistent", I18nUtils.getLocalizedString("nonexistent", "en"));
        }

        @Test
        @DisplayName("Null key returns null")
        void nullKeyReturnsNull() {
            assertNull(I18nUtils.getLocalizedString(null, "en"));
        }

        @Test
        @DisplayName("Null locale returns key")
        void nullLocaleReturnsKey() {
            assertEquals("change", I18nUtils.getLocalizedString("change", (String) null));
        }

        @Test
        @DisplayName("Null key and null locale returns null")
        void nullKeyAndNullLocale() {
            assertNull(I18nUtils.getLocalizedString((String) null, (String) null));
        }

        @Test
        @DisplayName("Unknown locale returns key itself")
        void unknownLocaleReturnsKey() {
            assertEquals("change", I18nUtils.getLocalizedString("change", "de"));
            assertEquals("change", I18nUtils.getLocalizedString("change", "fr"));
        }

        @Test
        @DisplayName("Empty string key returns empty string")
        void emptyKeyReturnsEmpty() {
            assertEquals("", I18nUtils.getLocalizedString("", "en"));
        }
    }

    @Nested
    @DisplayName("defaultLocaleFor")
    class DefaultLocaleFor {

        @Test
        @DisplayName("Exact locale codes return themselves")
        void exactLocaleCodes() {
            assertEquals("en", I18nUtils.defaultLocaleFor("en"));
            assertEquals("ru", I18nUtils.defaultLocaleFor("ru"));
            assertEquals("uk", I18nUtils.defaultLocaleFor("uk"));
            assertEquals("zh_cn", I18nUtils.defaultLocaleFor("zh_cn"));
            assertEquals("es_es", I18nUtils.defaultLocaleFor("es_es"));
            assertEquals("pt_br", I18nUtils.defaultLocaleFor("pt_br"));
            assertEquals("de_de", I18nUtils.defaultLocaleFor("de_de"));
            assertEquals("fr_fr", I18nUtils.defaultLocaleFor("fr_fr"));
            assertEquals("ja_jp", I18nUtils.defaultLocaleFor("ja_jp"));
            assertEquals("ko_kr", I18nUtils.defaultLocaleFor("ko_kr"));
            assertEquals("it_it", I18nUtils.defaultLocaleFor("it_it"));
        }

        @ParameterizedTest
        @CsvSource({
            "en_US, en",
            "en_GB, en",
            "ru_RU, ru",
            "uk_UA, uk",
            "ZH_CN, zh_cn",
            "ES_ES, es_es",
            "PT_BR, pt_br",
            "DE_DE, de_de",
            "FR_FR, fr_fr",
            "JA_JP, ja_jp",
            "KO_KR, ko_kr",
            "IT_IT, it_it",
            "EN,    en",
            "RU,    ru",
            "UK,    uk"
        })
        @DisplayName("Language-region codes map to base locale")
        void languageRegionCodes(String language, String expected) {
            assertEquals(expected, I18nUtils.defaultLocaleFor(language));
        }

        @ParameterizedTest
        @CsvSource({
            "pl_pl, en",
            "tr_tr, en",
            "nl_nl, en",
            "vi_vn, en",
            "th_th, en",
            "zh_tw, en"
        })
        @DisplayName("Unsupported languages fall back to English")
        void unsupportedLanguagesFallback(String language, String expected) {
            assertEquals(expected, I18nUtils.defaultLocaleFor(language));
        }

        @Test
        @DisplayName("Null language defaults to English")
        void nullLanguageDefaultsToEnglish() {
            assertEquals("en", I18nUtils.defaultLocaleFor(null));
        }

        @Test
        @DisplayName("Empty string defaults to English")
        void emptyStringDefaultsToEnglish() {
            assertEquals("en", I18nUtils.defaultLocaleFor(""));
        }

        @ParameterizedTest
        @ValueSource(strings = {"en", "ru", "uk", "zh_cn", "es_es", "pt_br", "de_de", "fr_fr", "ja_jp", "ko_kr", "it_it"})
        @DisplayName("Known locale returns itself (value source)")
        void knownLocalesReturnThemselves(String locale) {
            assertEquals(locale, I18nUtils.defaultLocaleFor(locale));
        }
    }

    @Nested
    @DisplayName("getLocalizedString (per-player overload)")
    class GetLocalizedStringPerPlayer {

        @Test
        @DisplayName("null player falls back through Config.LANGUAGE to the empty map -> key itself")
        void nullPlayerFallsBackToKey() {
            assertEquals("fulfilled", I18nUtils.getLocalizedString("fulfilled", (EntityPlayerMP) null));
        }

        @Test
        @DisplayName("mocked player with any client language still resolves through the empty map -> key itself")
        void mockedPlayerResolvesLocale() {
            EntityPlayerMP player = mock(EntityPlayerMP.class);
            player.language = "ru_ru";
            assertEquals("timeout", I18nUtils.getLocalizedString("timeout", player));
        }
    }

    @Nested
    @DisplayName("formatMessage")
    class FormatMessage {

        @Test
        @DisplayName("template without placeholders ignores extra args")
        void noPlaceholdersIgnoresArgs() {
            assertEquals("fulfilled", I18nUtils.formatMessage("fulfilled", "en", "bogus"));
        }

        @Test
        @DisplayName("unknown key returns key itself")
        void unknownKeyReturnsKey() {
            assertEquals("nonexistent", I18nUtils.formatMessage("nonexistent", "en", 1));
        }

        @Test
        @DisplayName("per-player overload resolves a player's locale (empty map -> key itself)")
        void perPlayerOverload() {
            EntityPlayerMP player = mock(EntityPlayerMP.class);
            player.language = "uk_ua";
            assertEquals("cooldown", I18nUtils.formatMessage("cooldown", player, 5));
        }
    }

    @Nested
    @DisplayName("shipped locale resources")
    class ShippedLocales {

        @ParameterizedTest
        @ValueSource(strings = {"en", "ru", "uk", "zh_cn", "es_es", "pt_br", "de_de", "fr_fr", "ja_jp", "ko_kr", "it_it"})
        @DisplayName("locale file ships all 22 keys (UTF-8 readable)")
        void localeFileHasAll22Keys(String locale) throws IOException {
            String path = "/assets/everlastingskins/lang/" + locale + ".properties";
            Properties props = new Properties();
            try (InputStream is = I18nUtilsTest.class.getResourceAsStream(path)) {
                assertNotNull(is, "missing resource " + path);
                props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            }
            for (String key : EXPECTED_KEYS) {
                assertTrue(props.containsKey(key), "missing key '" + key + "' in " + path);
                assertFalse(props.getProperty(key).trim().isEmpty(), "empty value for '" + key + "' in " + path);
            }
        }

        @Test
        @DisplayName("no stray format specifiers in translations without args")
        void noStrayPercentInPlainKeys() throws IOException {
            for (String locale : new String[]{"en", "ru", "uk", "zh_cn", "es_es", "pt_br", "de_de", "fr_fr", "ja_jp", "ko_kr", "it_it"}) {
                Properties props = new Properties();
                try (InputStream is = I18nUtilsTest.class.getResourceAsStream(
                        "/assets/everlastingskins/lang/" + locale + ".properties")) {
                    props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                }
                for (String key : EXPECTED_KEYS) {
                    String value = props.getProperty(key);
                    boolean expectsArg = "restored_from".equals(key) || "cooldown".equals(key)
                        || "no_skin_found".equals(key) || "metrics_cleanup".equals(key)
                        || "discord_announce".equals(key);
                    if (!expectsArg) {
                        assertFalse(value.contains("%"),
                            "plain key '" + key + "' in " + locale + " must not contain '%': " + value);
                    }
                }
            }
        }
    }
}
