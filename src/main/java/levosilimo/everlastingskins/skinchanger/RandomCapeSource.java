/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.util.EndpointsConfig;
import levosilimo.everlastingskins.util.HttpClient;
import levosilimo.everlastingskins.util.HttpsUrlConnectionHttpClient;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Cape-bearing random skin source: mskins.net's "with capes" listing
 * ({@code /en/skins/with_capes?page=N}, 48 cape-tagged player skins per page)
 * filtered through the Cosmetica cape directory.
 * <p>
 * mskins renders the cape client-side, so the listing HTML carries no cape
 * identity; each candidate name is therefore checked against
 * {@link CosmeticaApi} and only players Cosmetica reports as cape-bearing are
 * returned. Players unknown to Cosmetica are kept with a {@code null} texture
 * URL so the consumer can still try a Mojang-side lookup.
 */
public class RandomCapeSource {

    private static final String USER_AGENT = "EverlastingSkins/1.0";
    private static final int REQUEST_TIMEOUT = 10_000;

    /** Pages of the with_capes listing scanned per lookup when not tuned. */
    static final int DEFAULT_PAGE_COUNT = 2;
    /** Cap on returned candidates per lookup when not tuned. */
    static final int DEFAULT_RESULT_LIMIT = 10;

    private final HttpClient httpClient;
    private final CosmeticaApi cosmeticaApi;
    private final String withCapesTemplate;
    private final int pageCount;
    private final int resultLimit;
    private final Random random = new Random();

    public RandomCapeSource() {
        this(new HttpsUrlConnectionHttpClient(), new CosmeticaApi(),
                EndpointsConfig.getString("url.mskins.with_capes"), DEFAULT_PAGE_COUNT, DEFAULT_RESULT_LIMIT);
    }

    /** Test seam: injected client, Cosmetica API and scan tuning. */
    public RandomCapeSource(HttpClient httpClient, CosmeticaApi cosmeticaApi, String withCapesTemplate,
                            int pageCount, int resultLimit) {
        this.httpClient = httpClient;
        this.cosmeticaApi = cosmeticaApi;
        this.withCapesTemplate = withCapesTemplate;
        this.pageCount = Math.max(1, pageCount);
        this.resultLimit = Math.max(1, resultLimit);
    }

    /** A cape-bearing player name plus the cape texture Cosmetica knows. */
    public record CapeCandidate(String username, @Nullable String capeTextureUrl) {
    }

    /**
     * Scans up to {@link #pageCount} pages of the with_capes listing from a
     * random start page (wrapping around) and returns up to
     * {@link #resultLimit} candidates: players Cosmetica reports as
     * cape-bearing (with their cape texture URL) plus players unknown to
     * Cosmetica (null texture — the consumer decides).
     * <p>
     * A transport failure fetching a listing page aborts the scan; Cosmetica
     * failures never abort it (they fail closed per player).
     */
    public List<CapeCandidate> findCapeBearers() throws IOException {
        List<CapeCandidate> candidates = new ArrayList<>();
        int startPage = random.nextInt(pageCount) + 1;
        for (int offset = 0; offset < pageCount && candidates.size() < resultLimit; offset++) {
            int page = (startPage - 1 + offset) % pageCount + 1;
            for (String username : extractUsernames(fetchPage(page))) {
                if (username.isEmpty() || username.equals("ad")) {
                    continue;
                }
                CapeCandidate candidate = probeCape(username);
                if (candidate != null) {
                    candidates.add(candidate);
                    if (candidates.size() >= resultLimit) {
                        break;
                    }
                }
            }
        }
        return candidates;
    }

    /**
     * Random cape-bearing username for the {@code /skin set random <cape>}
     * path, or {@code null} when the scan found no usable candidate.
     */
    @Nullable
    public String pickRandomCapeUsername() throws IOException {
        List<CapeCandidate> candidates = findCapeBearers();
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size())).username();
    }

    private String fetchPage(int page) throws IOException {
        return httpClient.execute(
                URI.create(String.format(withCapesTemplate, page)),
                null,
                HttpClient.HttpType.JSON,
                USER_AGENT,
                HttpClient.HttpMethod.GET,
                Collections.emptyMap(),
                REQUEST_TIMEOUT
        ).body();
    }

    /**
     * Cosmetica verdict on a listing candidate: keep players with a cape
     * (texture URL attached) and players Cosmetica does not know (null
     * texture — the consumer decides); drop players Cosmetica knows to be
     * cape-less.
     */
    @Nullable
    private CapeCandidate probeCape(String username) {
        CosmeticaApi.CosmeticaPlayer player = cosmeticaApi.getPlayer(username);
        if (player == null) {
            return new CapeCandidate(username, null);
        }
        if (!player.hasCape()) {
            return null;
        }
        return new CapeCandidate(username, player.capeTextureUrl());
    }

    /**
     * Same span-based parser as {@link RandomMojangSkin}: the with_capes
     * listing uses the identical {@code card-title green-text truncate} card
     * markup.
     */
    private static List<String> extractUsernames(String html) {
        List<String> usernames = new ArrayList<>();
        int currentIndex = 0;

        while (true) {
            int charPointer = html.indexOf("<span class=\"card-title green-text truncate\">", currentIndex);
            if (charPointer == -1 || charPointer <= currentIndex) {
                break;
            }
            charPointer += 45;
            int stopIndex = html.indexOf("<", charPointer);
            if (stopIndex == -1) {
                break;
            }

            String username = html.substring(charPointer, stopIndex);
            if (!username.isEmpty()) {
                usernames.add(username);
            }

            currentIndex = stopIndex;
        }

        return usernames;
    }
}
