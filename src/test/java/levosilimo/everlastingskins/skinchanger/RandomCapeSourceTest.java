/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.FakeHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RandomCapeSource}: the with_capes listing scan, the
 * Cosmetica cape filter and the page/result limits. HTML is canned and the
 * Cosmetica verdict is stubbed via {@link FakeCosmeticaApi}; no live endpoint
 * is ever contacted.
 */
class RandomCapeSourceTest {

    private static final String WITH_CAPES_TEMPLATE = "http://test.local/with_capes?page=%d";

    private static final String CAPE_TEXTURE = "https://assets.namet.ag/test/cape.png";

    private FakeHttpClient httpClient;
    private FakeCosmeticaApi cosmetica;
    private RandomCapeSource source;

    @BeforeEach
    void setUp() {
        httpClient = new FakeHttpClient();
        cosmetica = new FakeCosmeticaApi();
        source = new RandomCapeSource(httpClient, cosmetica, WITH_CAPES_TEMPLATE, 1, 10);
    }

    private URI pageUri(int page) {
        return URI.create(String.format(WITH_CAPES_TEMPLATE, page));
    }

    private static String htmlOf(String... usernames) {
        StringBuilder html = new StringBuilder("<html><main>");
        for (String username : usernames) {
            html.append("\n<span class=\"card-title green-text truncate\">").append(username).append("</span>");
        }
        return html.append("\n</main></html>").toString();
    }

    @Nested
    @DisplayName("Listing scan and Cosmetica filter")
    class ScanAndFilter {

        @Test
        @DisplayName("fetches the with_capes page and returns every cape-bearing name")
        void fetches_with_capes_returns_names() throws Exception {
            httpClient.addResponse(pageUri(1), 200, htmlOf("Notch", "Jeb_", "Dinnerbone"));
            cosmetica.withCape("Notch", CAPE_TEXTURE);
            cosmetica.withCape("Jeb_", CAPE_TEXTURE);
            cosmetica.withCape("Dinnerbone", CAPE_TEXTURE);

            List<RandomCapeSource.CapeCandidate> candidates = source.findCapeBearers();

            assertEquals(3, candidates.size());
            assertEquals("Notch", candidates.get(0).username());
            assertEquals("Jeb_", candidates.get(1).username());
            assertEquals("Dinnerbone", candidates.get(2).username());
        }

        @Test
        @DisplayName("filters out players Cosmetica knows to be cape-less")
        void filters_out_players_without_cape() throws Exception {
            httpClient.addResponse(pageUri(1), 200, htmlOf("Notch", "Jeb_", "Dinnerbone"));
            cosmetica.withCape("Notch", CAPE_TEXTURE);
            cosmetica.withoutCape("Jeb_");
            cosmetica.withCape("Dinnerbone", CAPE_TEXTURE);

            List<RandomCapeSource.CapeCandidate> candidates = source.findCapeBearers();

            assertEquals(2, candidates.size());
            assertEquals("Notch", candidates.get(0).username());
            assertEquals("Dinnerbone", candidates.get(1).username());
        }

        @Test
        @DisplayName("filters out players whose cape is OptiFine-only, keeping official cape bearers")
        void filters_out_optifine_capebearer() throws Exception {
            httpClient.addResponse(pageUri(1), 200, htmlOf("Notch", "Dinnerbone"));
            cosmetica.withOptifineCape("Notch", CAPE_TEXTURE);
            cosmetica.withCape("Dinnerbone", CAPE_TEXTURE);

            List<RandomCapeSource.CapeCandidate> candidates = source.findCapeBearers();

            assertEquals(1, candidates.size());
            assertEquals("Dinnerbone", candidates.get(0).username());
            assertEquals(CAPE_TEXTURE, candidates.get(0).capeTextureUrl());
        }

        @Test
        @DisplayName("keeps players with an external cape and carries the texture URL")
        void keeps_players_with_external_cape() throws Exception {
            httpClient.addResponse(pageUri(1), 200, htmlOf("Notch"));
            cosmetica.withCape("Notch", CAPE_TEXTURE);

            List<RandomCapeSource.CapeCandidate> candidates = source.findCapeBearers();

            assertEquals(1, candidates.size());
            assertEquals("Notch", candidates.get(0).username());
            assertEquals(CAPE_TEXTURE, candidates.get(0).capeTextureUrl());
        }

        @Test
        @DisplayName("keeps players unknown to Cosmetica with a null texture")
        void keeps_players_unknown_to_cosmetica() throws Exception {
            httpClient.addResponse(pageUri(1), 200, htmlOf("Notch", "Ghost"));
            cosmetica.withCape("Notch", CAPE_TEXTURE);
            // "Ghost" is absent from the stub: getPlayer returns null.

            List<RandomCapeSource.CapeCandidate> candidates = source.findCapeBearers();

            assertEquals(2, candidates.size());
            assertEquals("Notch", candidates.get(0).username());
            assertEquals("Ghost", candidates.get(1).username());
            assertNull(candidates.get(1).capeTextureUrl());
        }

        @Test
        @DisplayName("skips the 'ad' placeholder entry")
        void skips_ad_placeholder() throws Exception {
            httpClient.addResponse(pageUri(1), 200, htmlOf("ad", "Steve"));
            cosmetica.withCape("ad", CAPE_TEXTURE);
            cosmetica.withCape("Steve", CAPE_TEXTURE);

            List<RandomCapeSource.CapeCandidate> candidates = source.findCapeBearers();

            assertEquals(1, candidates.size());
            assertEquals("Steve", candidates.get(0).username());
        }

        @Test
        @DisplayName("returns an empty list for a page without cards")
        void empty_page_returns_empty_list() throws Exception {
            httpClient.addResponse(pageUri(1), 200, "<html><main></main></html>");

            assertTrue(source.findCapeBearers().isEmpty());
        }
    }

    @Nested
    @DisplayName("Scan limits")
    class Limits {

        @Test
        @DisplayName("scans at most the configured page count")
        void respects_page_limit() throws Exception {
            httpClient.addResponse(pageUri(1), 200, htmlOf("Alice"));
            httpClient.addResponse(pageUri(2), 200, htmlOf("Bob"));
            cosmetica.withCape("Alice", CAPE_TEXTURE);
            cosmetica.withCape("Bob", CAPE_TEXTURE);

            // One page: only page-1 candidates are reachable.
            RandomCapeSource singlePage = new RandomCapeSource(httpClient, cosmetica, WITH_CAPES_TEMPLATE, 1, 10);
            List<RandomCapeSource.CapeCandidate> onePage = singlePage.findCapeBearers();
            assertEquals(1, onePage.size());
            assertEquals("Alice", onePage.get(0).username());

            // Two pages: both pages are scanned regardless of the random start.
            RandomCapeSource twoPages = new RandomCapeSource(httpClient, cosmetica, WITH_CAPES_TEMPLATE, 2, 10);
            List<RandomCapeSource.CapeCandidate> bothPages = twoPages.findCapeBearers();
            assertEquals(2, bothPages.size());
            assertTrue(bothPages.stream().anyMatch(c -> "Alice".equals(c.username())));
            assertTrue(bothPages.stream().anyMatch(c -> "Bob".equals(c.username())));
        }

        @Test
        @DisplayName("caps the result list at the configured limit")
        void respects_result_limit() throws Exception {
            httpClient.addResponse(pageUri(1), 200, htmlOf("A", "B", "C", "D", "E"));
            for (String name : new String[]{"A", "B", "C", "D", "E"}) {
                cosmetica.withCape(name, CAPE_TEXTURE);
            }

            RandomCapeSource limited = new RandomCapeSource(httpClient, cosmetica, WITH_CAPES_TEMPLATE, 1, 3);
            List<RandomCapeSource.CapeCandidate> candidates = limited.findCapeBearers();

            assertEquals(3, candidates.size());
            assertEquals("A", candidates.get(0).username());
            assertEquals("B", candidates.get(1).username());
            assertEquals("C", candidates.get(2).username());
        }
    }

    @Nested
    @DisplayName("Random pick")
    class RandomPick {

        @Test
        @DisplayName("returns the only candidate's username")
        void pick_returns_candidate() throws Exception {
            httpClient.addResponse(pageUri(1), 200, htmlOf("Notch"));
            cosmetica.withCape("Notch", CAPE_TEXTURE);

            assertEquals("Notch", source.pickRandomCapeUsername());
        }

        @Test
        @DisplayName("returns null when no candidate passes the filter")
        void pick_returns_null_when_empty() throws Exception {
            httpClient.addResponse(pageUri(1), 200, htmlOf("Notch"));
            cosmetica.withoutCape("Notch");

            assertNull(source.pickRandomCapeUsername());
        }

        @Test
        @DisplayName("picks from the candidate pool when several qualify")
        void pick_returns_member_of_pool() throws Exception {
            httpClient.addResponse(pageUri(1), 200, htmlOf("A", "B", "C"));
            for (String name : new String[]{"A", "B", "C"}) {
                cosmetica.withCape(name, CAPE_TEXTURE);
            }

            String picked = source.pickRandomCapeUsername();

            assertTrue(List.of("A", "B", "C").contains(picked));
        }
    }

    @Nested
    @DisplayName("mskins with_capes snapshot regression")
    class SnapshotRegression {

        @Test
        @DisplayName("extracts the same usernames as the saved snapshot")
        void snapshotUsernames() throws Exception {
            httpClient.addResponse(pageUri(1), 200, fixture("with_capes_page1.html"));

            List<RandomCapeSource.CapeCandidate> candidates = source.findCapeBearers();

            // 8 cards in the snapshot; the real 'ad' placeholder card is skipped.
            assertEquals(7, candidates.size());
            assertEquals("AntuYoutuber", candidates.get(0).username());
            assertEquals("ByeCode", candidates.get(1).username());
            assertEquals("Sanr1z", candidates.get(6).username());
        }
    }

    private static String fixture(String name) throws Exception {
        try (java.io.InputStream in = RandomCapeSourceTest.class.getResourceAsStream("/fixtures/mskins/" + name)) {
            if (in == null) {
                throw new AssertionError("Missing fixture: " + name);
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * Cosmetica verdict stub: canned per-player cape state, with names absent
     * from the map representing players Cosmetica does not know (null).
     */
    private static final class FakeCosmeticaApi extends CosmeticaApi {

        private final Map<String, CosmeticaPlayer> players = new HashMap<>();

        FakeCosmeticaApi() {
            super(new FakeHttpClient(), "http://unused/players/%player%");
        }

        void withCape(String username, String texture) {
            players.put(username, new CosmeticaPlayer(false,
                    new Account(null, username, new ExternalCape("cap-" + username, "official", texture, true), null),
                    null));
        }

        void withOptifineCape(String username, String texture) {
            players.put(username, new CosmeticaPlayer(false,
                    new Account(null, username, new ExternalCape("cap-" + username, "optifine", texture, true), null),
                    null));
        }

        void withoutCape(String username) {
            players.put(username, new CosmeticaPlayer(false,
                    new Account(null, username, null, null),
                    null));
        }

        @Override
        @Nullable
        public CosmeticaPlayer getPlayer(String nameOrUuid) {
            return players.get(nameOrUuid);
        }
    }
}
