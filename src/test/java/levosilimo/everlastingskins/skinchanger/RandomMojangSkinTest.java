/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RandomMojangSkin}.
 *
 * <p>All testable methods are private, so they are exercised via reflection.
 * Core coverage: the HTML-parsing logic in {@code extractUsernames} and the
 * JSON-deserialisation branches in {@code hasCape} / {@code isSlim}.
 */
class RandomMojangSkinTest {

    /* ------------------------------------------------------------------ */
    /*  extractUsernames                                                    */
    /* ------------------------------------------------------------------ */

    @Nested
    class ExtractUsernames {

        @Test
        @DisplayName("parses valid HTML with span tags")
        void parsesValidHtml() throws Exception {
            // matches after position 0 — the naive parser skips index 0 match
            var html = "\n<span class=\"card-title green-text truncate\">Steve</span>\n"
                + "<span class=\"card-title green-text truncate\">Alex</span>\n";
            var usernames = invokeExtractUsernames(html);
            assertTrue(usernames.contains("Steve"), "Should contain Steve");
            assertTrue(usernames.contains("Alex"), "Should contain Alex");
        }

        @Test
        @DisplayName("empty HTML returns empty list")
        void emptyHtml_returnsEmptyList() throws Exception {
            assertTrue(invokeExtractUsernames("").isEmpty());
        }

        @Test
        @DisplayName("malformed HTML (unclosed tag) returns empty list")
        void malformedHtml_handlesGracefully() throws Exception {
            var html = "\n<span class=\"card-title green-text truncate\">Partial";
            assertDoesNotThrow(() -> invokeExtractUsernames(html));
            assertTrue(invokeExtractUsernames(html).isEmpty());
        }

        @Test
        @DisplayName("no matching span tags returns empty list")
        void noMatchingTags_returnsEmptyList() throws Exception {
            var html = "<div class=\"other-class\">Content</div>";
            assertTrue(invokeExtractUsernames(html).isEmpty());
        }

        @Test
        @DisplayName("extracts multiple usernames in order")
        void extractsMultipleUsernames() throws Exception {
            var html = "\n<span class=\"card-title green-text truncate\">Notch</span>\n"
                + "<span class=\"card-title green-text truncate\">Jeb_</span>\n"
                + "<span class=\"card-title green-text truncate\">Dinnerbone</span>\n";
            var usernames = invokeExtractUsernames(html);
            assertEquals(3, usernames.size());
            assertEquals("Notch", usernames.get(0));
            assertEquals("Jeb_", usernames.get(1));
            assertEquals("Dinnerbone", usernames.get(2));
        }

        @Test
        @DisplayName("username with underscore is extracted correctly")
        void handlesUsernameWithUnderscore() throws Exception {
            var html = "\n<span class=\"card-title green-text truncate\">Test_Player</span>";
            var usernames = invokeExtractUsernames(html);
            assertTrue(usernames.contains("Test_Player"));
        }

        @Test
        @DisplayName("'ad' string as text content is extracted (filtering is caller's responsibility)")
        void extractsAdTextContent() throws Exception {
            // extractUsernames extracts all text from matching spans;
            // the 'ad' skip is in randomUsername/newUsername, not here.
            var html = "\n<span class=\"card-title green-text truncate\">ad</span>";
            var usernames = invokeExtractUsernames(html);
            assertTrue(usernames.contains("ad"),
                    "extractUsernames should extract 'ad' — callers filter it");
        }

        @Test
        @DisplayName("preserves whitespace inside tag content")
        void preservesWhitespace() throws Exception {
            var html = "\n<span class=\"card-title green-text truncate\">\n"
                + "    Herobrine\n"
                + "</span>\n";
            var usernames = invokeExtractUsernames(html);
            assertFalse(usernames.isEmpty());
            assertTrue(usernames.get(0).contains("Herobrine"));
        }
    }

    /* ------------------------------------------------------------------ */
    /*  hasCape / isSlim — both catch IOException and return false          */
    /* ------------------------------------------------------------------ */

    @Nested
    class RemoteCalls {

        @Test
        @DisplayName("hasCape returns false when API call fails")
        void hasCape_nonexistentUser_returnsFalse() throws Exception {
            assertFalse(invokeHasCape("nonexistent_user_xxx"));
        }

        @Test
        @DisplayName("isSlim returns false when API call fails")
        void isSlim_nonexistentUser_returnsFalse() throws Exception {
            assertFalse(invokeIsSlim("nonexistent_user_xxx"));
        }
    }

    @Nested
    @DisplayName("mskins.net snapshot regression")
    class SnapshotRegression {

        @Test
        @DisplayName("extracts the same usernames as the saved snapshot")
        void snapshotUsernames() throws Exception {
            String html = fixture("skins_random.html");

            List<String> usernames = invokeExtractUsernames(html);

            assertEquals(List.of("Notch", "Jeb_", "ad", "Dinnerbone"), usernames);
        }
    }

    /* ================================================================== */
    /*  Reflection helpers                                                 */
    /* ================================================================== */

    @SuppressWarnings("unchecked")
    private static List<String> invokeExtractUsernames(String html) throws Exception {
        Method m = RandomMojangSkin.class.getDeclaredMethod("extractUsernames", String.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, html);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokePrivateStatic(String methodName, Class<?>[] paramTypes,
                                             Object... args) throws Exception {
        Method m = RandomMojangSkin.class.getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        try {
            return (T) m.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    private static boolean invokeHasCape(String username) throws Exception {
        return invokePrivateStatic("hasCape", new Class<?>[]{String.class}, username);
    }

    private static boolean invokeIsSlim(String username) throws Exception {
        return invokePrivateStatic("isSlim", new Class<?>[]{String.class}, username);
    }

    private static String fixture(String name) throws Exception {
        try (java.io.InputStream in = RandomMojangSkinTest.class.getResourceAsStream("/fixtures/mskins/" + name)) {
            if (in == null) {
                throw new AssertionError("Missing fixture: " + name);
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
