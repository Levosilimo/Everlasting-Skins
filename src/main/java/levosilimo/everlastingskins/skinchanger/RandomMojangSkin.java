package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonObject;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.util.EndpointsConfig;
import levosilimo.everlastingskins.util.HttpClient;
import levosilimo.everlastingskins.util.HttpsUrlConnectionHttpClient;
import levosilimo.everlastingskins.util.JsonUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.*;

public class RandomMojangSkin {

    private static final HttpClient httpClient = new HttpsUrlConnectionHttpClient();

    private static final URI LATEST_SKINS_URI = EndpointsConfig.getURI("url.mskins.latest");
    private static final URI RANDOM_SKINS_URI = EndpointsConfig.getURI("url.mskins.random");

    private static final Random rand = new Random();

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
                "SkinRestorer",
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
            String decodedSTR = getDecodedStringForUsername(username);
            JsonObject decodedJSON = JsonUtils.parseJson(decodedSTR);
            return decodedJSON.getAsJsonObject("textures").has("CAPE");
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean isSlim(String username) {
        try {
            String decodedSTR = getDecodedStringForUsername(username);
            JsonObject decodedJSON = JsonUtils.parseJson(decodedSTR);
            return decodedJSON.getAsJsonObject("textures").getAsJsonObject("SKIN").has("metadata");
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String getDecodedStringForUsername(String username) throws IOException {
        String skinValue = SkinCommand.getMojangAPI().getSkin(username)
                .orElseThrow(() -> new IOException("No skin data for username: " + username))
                .skinProperty().getOriginalProperty().value();
        return new String(Base64.getDecoder().decode(skinValue));
    }
}
