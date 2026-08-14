/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge152.util;

import levosilimo.everlastingskins.forge152.config.Config;
import levosilimo.everlastingskins.i18n.LanguageKeys;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 1.5.2 I18nUtils key resolution over the classpath {@code .lang} files —
 * mirror of the forge-1.6.4 / forge-1.7.10 I18nUtilsTest. The test
 * classpath carries src/main/resources (harness sourceSets), so the same
 * 11 locale files resolve here. {@link #setUp()} reloads all locales so
 * tests are independent of any prior load state.
 */
public class I18nUtilsTest {

    @Before
    public void setUp() {
        I18nUtils.loadAll();
        Config.LANGUAGE = "en";
    }

    @After
    public void tearDown() {
        Config.LANGUAGE = "en";
    }

    @Test
    public void resolvesTranslationForLocale() {
        assertEquals("Запрос на смену скина отправлен",
            I18nUtils.getLocalizedString(LanguageKeys.CHANGE, "ru"));
        assertEquals("Скін успішно застосовано.",
            I18nUtils.getLocalizedString(LanguageKeys.FULFILLED, "uk"));
        assertEquals("皮肤更换已请求",
            I18nUtils.getLocalizedString(LanguageKeys.CHANGE, "zh_cn"));
        assertEquals("Skin change queued",
            I18nUtils.getLocalizedString(LanguageKeys.CHANGE, "en"));
    }

    @Test
    public void defaultLocaleFor_normalizesPrefixAndNull() {
        assertEquals("en", I18nUtils.defaultLocaleFor(null));
        assertEquals("en", I18nUtils.defaultLocaleFor("en_us"));
        assertEquals("zh_cn", I18nUtils.defaultLocaleFor("zh_cn"));
        assertEquals("zh_cn", I18nUtils.defaultLocaleFor("zh_CN"));
        assertEquals("ru", I18nUtils.defaultLocaleFor("ru_ru"));
        assertEquals("en", I18nUtils.defaultLocaleFor("xx_yy"));
    }

    @Test
    public void localeMissingKey_fallsBackToEnglish() {
        // stored_from_other_username ships only in en.lang (mirrors the 1.21 JSONs).
        assertEquals("Skin already stored from Mojang as %s; run /skin clear to switch usernames",
            I18nUtils.getLocalizedString(LanguageKeys.STORED_FROM_OTHER_USERNAME, "de_de"));
    }

    @Test
    public void get_usesConfigLanguage() {
        Config.LANGUAGE = "ru";
        assertEquals("Скин успешно применён.", I18nUtils.get(LanguageKeys.FULFILLED));
    }

    @Test
    public void get_formatsArgs() {
        assertEquals("No skin found for \"Steve\"",
            I18nUtils.get(LanguageKeys.NO_SKIN_FOUND, "Steve"));
        assertEquals("Metrics cleanup: pruned 7 stale player entries",
            I18nUtils.get(LanguageKeys.METRICS_CLEANUP, 7));
        // The .lang files escape the leading space (Properties.load strips it);
        // the rendered suffix must be "5 refreshes", not "5refreshes".
        assertEquals(" refreshes", I18nUtils.get(LanguageKeys.METRICS_REFRESHES));
    }

    @Test
    public void unknownKey_returnsRawKey() {
        // 1.5.2 StringTranslate.translateKey falls back to the key itself
        // (translateTable.getProperty(key, key)), same as 1.6.4.
        assertEquals("totally_unknown_key", I18nUtils.get("totally_unknown_key"));
        assertEquals("totally_unknown_key", I18nUtils.getLocalizedString("totally_unknown_key", "ru"));
    }

    @Test
    public void nullKey_returnsNull() {
        assertEquals(null, I18nUtils.get(null));
    }
}
