/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.i18n;

import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Drift guards for the canonical key set: every constant must have a
 * canonical English entry, the English {@code .lang} resource must cover
 * the whole set, and every shipped locale file must stay within it.
 */
public class LanguageKeysTest {

    @Test
    public void everyConstantHasDefaultEnglish() {
        for (String key : allConstants()) {
            assertTrue("no DEFAULT_ENGLISH entry for " + key,
                LanguageKeys.DEFAULT_ENGLISH.containsKey(key));
            assertTrue("isKnownKey rejects canonical key " + key,
                LanguageKeys.isKnownKey(key));
        }
        assertEquals(allConstants(), LanguageKeys.DEFAULT_ENGLISH.keySet());
    }

    @Test
    public void englishLangFileCoversEveryKey() throws Exception {
        Properties en = loadLang("en");
        assertEquals(allConstants(), en.stringPropertyNames());
    }

    @Test
    public void everyLocaleLangFileStaysWithinTheKeySet() throws Exception {
        Set<String> known = allConstants();
        assertEquals(11, LanguageKeys.LOCALES.size());
        for (String locale : LanguageKeys.LOCALES) {
            Properties props = loadLang(locale);
            assertTrue("locale " + locale + " has keys outside the canonical set",
                known.containsAll(props.stringPropertyNames()));
        }
    }

    @Test
    public void unknownKey_isRejected() {
        assertTrue(!LanguageKeys.isKnownKey("not_a_key"));
        assertTrue(!LanguageKeys.isKnownKey(null));
    }

    /** All public static final String constants of {@link LanguageKeys}. */
    private static Set<String> allConstants() {
        Set<String> keys = new HashSet<String>();
        for (Field field : LanguageKeys.class.getDeclaredFields()) {
            if (field.getType() == String.class
                && Modifier.isPublic(field.getModifiers())
                && Modifier.isStatic(field.getModifiers())
                && Modifier.isFinal(field.getModifiers())) {
                try {
                    keys.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
        return keys;
    }

    private static Properties loadLang(String locale) throws Exception {
        String path = "/assets/everlastingskins/lang/" + locale + ".lang";
        InputStream is = LanguageKeysTest.class.getResourceAsStream(path);
        assertNotNull("missing .lang resource " + path, is);
        Properties props = new Properties();
        props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
        is.close();
        return props;
    }
}
