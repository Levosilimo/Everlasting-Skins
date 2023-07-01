package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import levosilimo.everlastingskins.enums.SkinVariant;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

import static levosilimo.everlastingskins.util.RandomUserAgent.getRandomUserAgent;

public class RandomMojangSkin {
    private static final List<String> BLACK_LIST = List.of("ad");
    private static final int MIN_SKIN_INDEX = 9000;
    private static final int MAX_SKIN_INDEX = 40000;
    private static final String SPAN_TEXT = "<span class=\"card-title green-text truncate\">";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    public static String randomNickname(boolean needCape, SkinVariant variant, boolean latest) {
        String url;
        if (latest) url = "https://mskins.net/ru/skins/latest";
        else if (needCape) {
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
            if (needCape && !hasCape(nickname)) {
                continue;
            }
            if ((variant == SkinVariant.slim && !isSlim(nickname))
                    || (variant == SkinVariant.classic && isSlim(nickname))) {
                continue;
            }
            break;
        }

        return nickname;
    }

    private static String getHtmlFromUrl(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(new URI(url)).header("User-Agent", getRandomUserAgent()).GET().build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | InterruptedException | URISyntaxException ex) {
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