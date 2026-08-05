/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fuzz corpus for {@link RandomMojangSkin#extractUsernames}, the HTML
 * scraper behind the random-skin feature (AI-generated code surface, cf.
 * Pearce et al., S&P 2022). The parser is private, so properties drive it
 * via reflection, mirroring {@link RandomMojangSkinTest}.
 * <p>
 * Contracts asserted here:
 * <ul>
 *   <li>malformed HTML (garbage, truncated markers, unclosed spans)
 *       extracts nothing and never throws;</li>
 *   <li>oversized documents (many spans, multi-KB usernames, MBs of noise)
 *       never throw and only yield sane, verbatim usernames;</li>
 *   <li>path-traversal payloads inside span content come out byte-identical
 *       — nothing is decoded, unescaped or resolved to a file path (the
 *       parser is pure string slicing and must stay that way).</li>
 * </ul>
 */
class RandomMojangSkinFuzzTest {

    @Provide
    net.jqwik.api.Arbitrary<String> malformedHtml() {
        return MalformedJsonCorpus.malformedHtml();
    }

    @Provide
    net.jqwik.api.Arbitrary<String> oversizedHtml() {
        return MalformedJsonCorpus.oversizedHtml();
    }

    @Provide
    net.jqwik.api.Arbitrary<String> traversalHtml() {
        return MalformedJsonCorpus.traversalHtml();
    }

    /* ------------------------------------------------------------------ */
    /*  E1: malformed HTML extracts nothing                                */
    /* ------------------------------------------------------------------ */

    /**
     * Malformed HTML — random garbage, truncated span markers, a single
     * never-closed span — must yield an empty list and never throw. The
     * corpus is built so no input contains two complete openers, which is
     * the only shape the naive parser can extract a name from.
     */
    @Property(tries = 100)
    @Label("E1: malformed HTML extracts no usernames and never throws")
    void extractUsernames_malformedHtml_returnsEmpty(@ForAll @From("malformedHtml") String html) {
        List<String> usernames = assertDoesNotThrow(() -> invokeExtractUsernames(html));
        assertTrue(usernames.isEmpty(), () -> "malformed HTML yielded usernames: " + usernames);
    }

    /* ------------------------------------------------------------------ */
    /*  E2: oversized documents never throw                                */
    /* ------------------------------------------------------------------ */

    /**
     * Oversized documents — thousands of spans, a 1-64 KB username, up to
     * 1 MB of noise, hundreds of dangling openers — must never throw and
     * must only yield sane usernames: non-empty, free of {@code <} and
     * control characters, each a verbatim substring of the input, and
     * bounded in count.
     */
    @Property(tries = 40)
    @Label("E2: oversized HTML never throws and yields only sane verbatim usernames")
    void extractUsernames_oversizedDocument_noThrow(@ForAll @From("oversizedHtml") String html) {
        List<String> usernames = assertDoesNotThrow(() -> invokeExtractUsernames(html));

        assertTrue(usernames.size() <= 20000,
                () -> "implausible username count " + usernames.size() + " for " + html.length() + " chars");
        for (String username : usernames) {
            assertTrue(!username.isEmpty(), "extractor returned an empty username");
            assertTrue(username.indexOf('<') == -1,
                    () -> "username contains '<': " + excerpt(username));
            assertTrue(html.indexOf(username) != -1,
                    () -> "username is not a verbatim substring of the input: " + excerpt(username));
            for (int i = 0; i < username.length(); i++) {
                char c = username.charAt(i);
                assertTrue(c >= 0x20 && c != 0x7F,
                        () -> "username contains control character 0x" + Integer.toHexString(c));
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  E3: traversal payloads stay verbatim                               */
    /* ------------------------------------------------------------------ */

    /**
     * Path-traversal payloads inside span content must come out byte-
     * identical: the extractor never decodes ({@code %2F} stays
     * {@code %2F}), never unescapes, never resolves the payload to a file
     * path, and never throws. Every extracted username must be a verbatim
     * substring of the served document.
     */
    @Property(tries = 50)
    @Label("E3: path-traversal payloads extract verbatim, with no access outside the prefix")
    void extractUsernames_pathTraversal_noAccessOutsidePrefix(@ForAll @From("traversalHtml") String html) {
        List<String> usernames = assertDoesNotThrow(() -> invokeExtractUsernames(html));

        assertEquals(2, usernames.size(),
                () -> "traversal document must yield exactly its two embedded payloads: " + usernames);
        for (String username : usernames) {
            assertTrue(!username.isEmpty());
            assertTrue(html.indexOf(username) != -1,
                    () -> "traversal payload was decoded or transformed: " + excerpt(username));
        }
    }

    /* ================================================================== */
    /*  Reflection helper (extractUsernames is private)                    */
    /* ================================================================== */

    @SuppressWarnings("unchecked")
    private static List<String> invokeExtractUsernames(String html) throws Exception {
        Method m = RandomMojangSkin.class.getDeclaredMethod("extractUsernames", String.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, html);
    }

    private static String excerpt(String s) {
        if (s.length() <= 96) {
            return s;
        }
        return s.substring(0, 96) + "…(" + s.length() + " chars)";
    }
}
