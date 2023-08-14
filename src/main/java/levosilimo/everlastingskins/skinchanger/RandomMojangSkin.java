package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import levosilimo.everlastingskins.enums.CapeVariant;
import levosilimo.everlastingskins.enums.SkinVariant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import static levosilimo.everlastingskins.util.RandomUserAgent.getRandomUserAgent;

public class RandomMojangSkin {
    private static final List<String> BLACK_LIST = Arrays.asList("ad");
    private static final int MIN_SKIN_INDEX = 9000;
    private static final int MAX_SKIN_INDEX = 40000;
    private static final String SPAN_TEXT = "<span class=\"card-title green-text truncate\">";
    public static String randomNickname(CapeVariant capeVariant, SkinVariant variant, boolean latest) {
        String url;
        if (latest) url = "https://mskins.net/ru/skins/latest";
        else if (capeVariant.equals(CapeVariant.CAPE)) {
            Random rand = new Random();
            int year = rand.nextInt(6) + 2011;
            while (year == 2014) {
                year = rand.nextInt(6) + 2011;
            }
            int page = rand.nextInt(24) + ((year % 10) * 5) + 1;
            url = String.format("https://mskins.net/en/cape/minecon_%d?page=%d", year, page);
        } else {
            url = "https://mskins.net/en/skins/random";
        }
        String html = getHtmlFromUrl(url);
        if (html.length() < 20000) return "Notch";

        HashSet<String> nicknames = new HashSet<>();
        int charPointer = MIN_SKIN_INDEX;
        int randomSkinIndex = -1;
        while (charPointer <= MAX_SKIN_INDEX) {
            randomSkinIndex = html.indexOf(SPAN_TEXT, charPointer) + SPAN_TEXT.length();
            if (randomSkinIndex > 44) {
                charPointer = randomSkinIndex + 1;
                int randomUsernameIndexEnd = html.indexOf("<", randomSkinIndex);
                if(randomUsernameIndexEnd == -1) break;
                nicknames.add(html.substring(randomSkinIndex, randomUsernameIndexEnd));
            } else break;
        }
        if (nicknames.isEmpty()) return "Notch";
        Stack<String> nicknamesStack = new Stack<>();
        nicknamesStack.addAll(nicknames);
        Collections.shuffle(nicknamesStack);
        String nickname = "Notch";
        while (!nicknamesStack.isEmpty()){
            nickname = nicknamesStack.pop();
            while ((nickname.isEmpty() || BLACK_LIST.contains(nickname)) && !nicknamesStack.isEmpty()) {
                nickname = nicknamesStack.pop();
            }
            if (capeVariant.equals(CapeVariant.CAPE) && !hasCape(nickname) || capeVariant.equals(CapeVariant.NO_CAPE) && hasCape(nickname)) {
                continue;
            }
            if ((variant == SkinVariant.SLIM && !isSlim(nickname))
                    || (variant == SkinVariant.CLASSIC && isSlim(nickname))) {
                continue;
            }
            break;
        }

        return nickname;
    }

    private static String getHtmlFromUrl(String url) {
        try {
            URL urlObj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", getRandomUserAgent());

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            return response.toString();
        } catch (IOException ex) {
            ex.printStackTrace();
            return "";
        }
    }

    private static final JsonParser parser = new JsonParser();

    public static boolean hasCape(String nick) {
        String decodedSTR = new String(Base64.getDecoder().decode(MojangSkinProvider.getSkin(nick).getValue()));
        JsonObject decodedJSON = parser.parse(decodedSTR).getAsJsonObject();
        return decodedJSON.getAsJsonObject("textures").has("CAPE");
    }

    public static boolean isSlim(String nick) {
        String decodedSTR = new String(Base64.getDecoder().decode(MojangSkinProvider.getSkin(nick).getValue()));
        JsonObject decodedJSON = parser.parse(decodedSTR).getAsJsonObject();
        return decodedJSON.getAsJsonObject("textures").getAsJsonObject("SKIN").has("metadata");
    }
}