package levosilimo.everlastingskins.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

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
            assertEquals("change", I18nUtils.getLocalizedString("change", null));
        }

        @Test
        @DisplayName("Null key and null locale returns null")
        void nullKeyAndNullLocale() {
            assertNull(I18nUtils.getLocalizedString(null, null));
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
        }

        @ParameterizedTest
        @CsvSource({
            "en_US, en",
            "en_GB, en",
            "ru_RU, ru",
            "uk_UA, uk",
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
            "fr, en",
            "de, en",
            "es, en",
            "zh, en",
            "ja, en"
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
        @ValueSource(strings = {"en", "ru", "uk"})
        @DisplayName("Known locale returns itself (value source)")
        void knownLocalesReturnThemselves(String locale) {
            assertEquals(locale, I18nUtils.defaultLocaleFor(locale));
        }
    }
}
