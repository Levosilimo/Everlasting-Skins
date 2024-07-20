package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonObject;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.util.JsonUtils;
import levosilimo.everlastingskins.util.WebUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class RandomMojangSkin {

    private static final URL LATEST_SKINS_URL;
    private static final URL RANDOM_SKINS_URL;

    static {
        try {
            LATEST_SKINS_URL = new URL("https://mskins.net/ru/skins/latest");
            RANDOM_SKINS_URL = new URL("https://mskins.net/en/skins/random");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Random rand = new Random();

    @Nullable
    public static String randomUsername(boolean needCape, SkinVariant variant) throws IOException {
        for (int i = 0; i < 5; i++) {
            String html = WebUtils.GETRequest(needCape ? getRandomCapeUrl() : RANDOM_SKINS_URL);
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

    private static URL getRandomCapeUrl() throws MalformedURLException {
        int year = getRandomYearExcept2014();
        int page = getRandomPageForYear(year);
        return new URL("https://mskins.net/en/cape/minecon_" + year + "?page=" + page);
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
            String html = WebUtils.GETRequest(LATEST_SKINS_URL);
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
        String skinValue = SkinCommand.mojangAPI.getSkin(username).skinProperty().getOriginalProperty().getValue();
        return new String(Base64.getDecoder().decode(skinValue));
    }
}
