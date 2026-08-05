/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonObject;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.util.EndpointsConfig;
import levosilimo.everlastingskins.util.HttpClient;
import levosilimo.everlastingskins.util.HttpsUrlConnectionHttpClient;
import levosilimo.everlastingskins.util.JsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.*;

public class RandomMojangSkin {

    private static final Logger LOGGER = LogManager.getLogger(RandomMojangSkin.class);

    private static final HttpClient httpClient = new HttpsUrlConnectionHttpClient();

    private static final URI LATEST_SKINS_URI = EndpointsConfig.getURI("url.mskins.latest");
    private static final URI RANDOM_SKINS_URI = EndpointsConfig.getURI("url.mskins.random");

    private static final Random rand = new Random();

    /** Mojang API for per-username lookups (per-version code may swap it). */
    private static MojangAPI mojangAPI = new MojangApiHttpImpl();

    static void setMojangAPI(MojangAPI api) {
        mojangAPI = api;
    }

    @Nullable
    public static String randomUsername(boolean needCape, SkinVariant variant) throws IOException {
        for (int i = 0; i < 5; i++) {
            String html = fetchPage(needCape ? getRandomCapeUri() : RANDOM_SKINS_URI);
            List<String> usernames = extractUsernames(html);
            for (String username : usernames) {
                if (username == null || username.isEmpty() || username.equals("ad")) {
                    continue;
                }

                if (needCape && !hasCape(username)) {
                    continue;
                }

                if ((variant.equals(SkinVariant.SLIM) && !isSlim(username)) || ((variant.equals(SkinVariant.CLASSIC) && isSlim(username)))) {
                    continue;
                }
                return username;
            }
        }
        return null;
    }

    private static String fetchPage(URI uri) throws IOException {
        return httpClient.execute(
                uri,
                null,
                HttpClient.HttpType.JSON,
                "EverlastingSkins/1.0",
                HttpClient.HttpMethod.GET,
                Collections.emptyMap(),
                10_000
        ).body();
    }

    private static URI getRandomCapeUri() {
        int year = getRandomYearExcept2014();
        int page = getRandomPageForYear(year);
        return URI.create(EndpointsConfig.getString("url.mskins.cape") + year + "?page=" + page);
    }

    private static int getRandomYearExcept2014() {
        int year = rand.nextInt(6) + 2011;
        while (year == 2014) {
            year = rand.nextInt(6) + 2011;
        }
        return year;
    }

    private static int getRandomPageForYear(int year) {
        return rand.nextInt(24) + ((year % 10) * 5) + 1;
    }

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

    @Nullable
    public static String newUsername(SkinVariant variant) throws IOException {
        for (int i = 0; i < 5; i++) {
            String html = fetchPage(LATEST_SKINS_URI);
            List<String> usernames = extractUsernames(html);
            for (String username : usernames) {
                if (username.isEmpty() || username.equals("ad") || (variant.equals(SkinVariant.SLIM) && !isSlim(username))) {
                    continue;
                }
                return username;
            }
        }
        return null;
    }

    private static boolean hasCape(String username) {
        try {
            return hasCapeInDecoded(getDecodedStringForUsername(username));
        } catch (IOException e) {
            LOGGER.error("Failed to check cape for {}", username, e);
            return false;
        }
    }

    private static boolean isSlim(String username) {
        try {
            return isSlimInDecoded(getDecodedStringForUsername(username));
        } catch (IOException e) {
            LOGGER.error("Failed to check slim variant for {}", username, e);
            return false;
        }
    }

    /**
     * Test seam: cape check on an already-decoded textures payload, so tests
     * exercise the JSON interpretation without a live skin lookup.
     */
    static boolean hasCapeInDecoded(String decodedJson) {
        JsonObject decodedJSON = JsonUtils.parseJson(decodedJson);
        return decodedJSON.getAsJsonObject("textures").has("CAPE");
    }

    /**
     * Test seam: slim check on an already-decoded textures payload.
     */
    static boolean isSlimInDecoded(String decodedJson) {
        JsonObject decodedJSON = JsonUtils.parseJson(decodedJson);
        return decodedJSON.getAsJsonObject("textures").getAsJsonObject("SKIN").has("metadata");
    }

    private static String getDecodedStringForUsername(String username) throws IOException {
        String skinValue = mojangAPI.getSkin(username)
                .orElseThrow(() -> new IOException("No skin data for username: " + username))
                .skinProperty().getOriginalProperty().getValue();
        return new String(Base64.getDecoder().decode(skinValue));
    }
}
