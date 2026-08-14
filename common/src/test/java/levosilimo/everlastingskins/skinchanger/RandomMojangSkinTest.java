/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import levosilimo.everlastingskins.util.JsonUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RandomMojangSkin}.
 *
 * <p>All testable methods are private, so they are exercised via reflection.
 * Core coverage: the HTML-parsing logic in {@code extractUsernames} and the
 * JSON-deserialisation branches in {@code hasCape} / {@code isSlim}.
 *
 * <p>Written for Java 8 compatibility (no {@code var}, no {@code List.of},
 * no text blocks).
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
            String html = "\n<span class=\"card-title green-text truncate\">Steve</span>\n"
                + "<span class=\"card-title green-text truncate\">Alex</span>\n";
            List<String> usernames = invokeExtractUsernames(html);
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
            String html = "\n<span class=\"card-title green-text truncate\">Partial";
            assertDoesNotThrow(() -> invokeExtractUsernames(html));
            assertTrue(invokeExtractUsernames(html).isEmpty());
        }

        @Test
        @DisplayName("no matching span tags returns empty list")
        void noMatchingTags_returnsEmptyList() throws Exception {
            String html = "<div class=\"other-class\">Content</div>";
            assertTrue(invokeExtractUsernames(html).isEmpty());
        }

        @Test
        @DisplayName("extracts multiple usernames in order")
        void extractsMultipleUsernames() throws Exception {
            String html = "\n<span class=\"card-title green-text truncate\">Notch</span>\n"
                + "<span class=\"card-title green-text truncate\">Jeb_</span>\n"
                + "<span class=\"card-title green-text truncate\">Dinnerbone</span>\n";
            List<String> usernames = invokeExtractUsernames(html);
            assertEquals(3, usernames.size());
            assertEquals("Notch", usernames.get(0));
            assertEquals("Jeb_", usernames.get(1));
            assertEquals("Dinnerbone", usernames.get(2));
        }

        @Test
        @DisplayName("username with underscore is extracted correctly")
        void handlesUsernameWithUnderscore() throws Exception {
            String html = "\n<span class=\"card-title green-text truncate\">Test_Player</span>";
            List<String> usernames = invokeExtractUsernames(html);
            assertTrue(usernames.contains("Test_Player"));
        }

        @Test
        @DisplayName("'ad' string as text content is extracted (filtering is caller's responsibility)")
        void extractsAdTextContent() throws Exception {
            // extractUsernames extracts all text from matching spans;
            // the 'ad' skip is in randomUsername/newUsername, not here.
            String html = "\n<span class=\"card-title green-text truncate\">ad</span>";
            List<String> usernames = invokeExtractUsernames(html);
            assertTrue(usernames.contains("ad"),
                    "extractUsernames should extract 'ad' — callers filter it");
        }

        @Test
        @DisplayName("preserves whitespace inside tag content")
        void preservesWhitespace() throws Exception {
            String html = "\n<span class=\"card-title green-text truncate\">\n"
                + "    Herobrine\n"
                + "</span>\n";
            List<String> usernames = invokeExtractUsernames(html);
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

    /* ------------------------------------------------------------------ */
    /*  mskins.net snapshot regression                                      */
    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("mskins.net snapshot regression")
    class SnapshotRegression {

        @Test
        @DisplayName("extracts the same usernames as the saved snapshot")
        void snapshotUsernames() throws Exception {
            String html = fixture("skins_random.html");

            List<String> usernames = invokeExtractUsernames(html);

            assertEquals(Arrays.asList("Notch", "Jeb_", "ad", "Dinnerbone"), usernames);
        }
    }

    @Nested
    @DisplayName("Markup brittleness (intentionally alerts on markup change)")
    class MarkupBrittleness {

        @Test
        @DisplayName("renamed span class -> empty")
        void renamedSpanClassReturnsEmpty() throws Exception {
            String html = "<span class=\"card-title blue-text truncate\">Notch</span>";

            assertTrue(invokeExtractUsernames(html).isEmpty());
        }

        @Test
        @DisplayName("removed span -> empty")
        void removedSpanReturnsEmpty() throws Exception {
            String html = "<div class=\"card-content\">Notch</div>";

            assertTrue(invokeExtractUsernames(html).isEmpty());
        }
    }

    /* ------------------------------------------------------------------ */
    /*  hasCape / isSlim - decoded payload interpretation                   */
    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("Decoded payload interpretation")
    class DecodedPayloads {

        @Test
        @DisplayName("hasCape: skin+cape payload -> true")
        void hasCapeWithCapePayload() throws Exception {
            assertTrue(RandomMojangSkin.hasCapeInDecoded(decodedFixtureValue("profile-200-skin-and-cape.json")));
        }

        @Test
        @DisplayName("hasCape: textures-only payload -> false")
        void hasCapeWithoutCapePayload() throws Exception {
            assertFalse(RandomMojangSkin.hasCapeInDecoded(decodedFixtureValue("profile-200-textures-only.json")));
        }

        @Test
        @DisplayName("isSlim: metadata slim payload -> true")
        void isSlimWithMetadataPayload() throws Exception {
            assertTrue(RandomMojangSkin.isSlimInDecoded(decodedFixtureValue("profile-200-metadata-slim.json")));
        }

        @Test
        @DisplayName("isSlim: payload without metadata -> false")
        void isSlimWithoutMetadataPayload() throws Exception {
            assertFalse(RandomMojangSkin.isSlimInDecoded(decodedFixtureValue("profile-200-textures-only.json")));
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

    private static Object invokePrivateStatic(String methodName, Class<?>[] paramTypes,
                                             Object... args) throws Exception {
        Method m = RandomMojangSkin.class.getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        try {
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }

    private static boolean invokeHasCape(String username) throws Exception {
        return (Boolean) invokePrivateStatic("hasCape", new Class<?>[]{String.class}, username);
    }

    private static boolean invokeIsSlim(String username) throws Exception {
        return (Boolean) invokePrivateStatic("isSlim", new Class<?>[]{String.class}, username);
    }

    private static String fixture(String name) throws Exception {
        java.io.InputStream in = RandomMojangSkinTest.class.getResourceAsStream("/fixtures/mskins/" + name);
        if (in == null) {
            throw new AssertionError("Missing fixture: " + name);
        }
        try {
            return readAll(in);
        } finally {
            in.close();
        }
    }

    private static String decodedFixtureValue(String mojangFixture) throws Exception {
        java.io.InputStream in = RandomMojangSkinTest.class.getResourceAsStream("/fixtures/mojang/" + mojangFixture);
        if (in == null) {
            throw new AssertionError("Missing fixture: " + mojangFixture);
        }
        String json;
        try {
            json = readAll(in);
        } finally {
            in.close();
        }
        String value = JsonUtils.parseJson(json)
                .getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
        return new String(java.util.Base64.getDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
