package levosilimo.everlastingskins.skinchanger;

import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.enums.SkinVariant;
import net.minecraft.util.math.MathHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RandomMojangSkin {
    private static final ArrayList<String> blackList=Lists.newArrayList("ad");
    public static String randomNick(boolean needCape,SkinVariant variant) {
        StringBuilder html = new StringBuilder();
        InputStream response = null;
        int min = 0;
        int max = 999999;
        try {
            String url;
            if (needCape) {
                Random rand = new Random();
                int year = MathHelper.nextInt(rand, 2011, 2016);
                while (year == 2014) {
                    year = MathHelper.nextInt(rand, 2011, 2016);
                }
                int page = MathHelper.nextInt(rand, 1, 24) + ((year % 10) * 5);
                url = "https://mskins.net/en/cape/minecon_" + MathHelper.nextInt(new Random(), 2011, 2016) + "?page=" + page;
                min = 11000;
                max = 65000;
            } else {
                url = "https://mskins.net/en/skins/random";
                min = 11000;
                max = 38000;
            }
            response = new URL(url).openStream();


            Scanner scanner = new Scanner(response);
            String responseLine = scanner.useDelimiter("</body>").next();
            html.append(responseLine);

        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (response != null) response.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        int RandomSkinIndex = html.indexOf("<span class=\"card-title green-text truncate\">", MathHelper.nextInt(new Random(), min, max)) + 45;
        String nickname = "";
        if (RandomSkinIndex > 44) {
            int RandomSkinIndexStop = html.indexOf("<", RandomSkinIndex);
            nickname = html.substring(RandomSkinIndex, RandomSkinIndexStop);
            while (nickname.isEmpty()) {
                nickname = RandomMojangSkin.randomNick(needCape, variant);
            }
            if(blackList.contains(nickname))nickname = RandomMojangSkin.newNick(variant);
            if (needCape && !hasCape(nickname)) nickname = RandomMojangSkin.randomNick(true, variant);
            if ((variant.equals(SkinVariant.slim) && !isSlim(nickname)) || ((variant.equals(SkinVariant.classic) && isSlim(nickname))))
                nickname = RandomMojangSkin.randomNick(needCape, variant);
        } else {
            nickname = RandomMojangSkin.randomNick(needCape, variant);
        }
        return nickname;
    }
    public static String newNick(SkinVariant variant) {
        StringBuilder html = new StringBuilder();
        try {
            URL url = new URL("https://mskins.net/ru/skins/latest");
            HttpURLConnection connection = ((HttpURLConnection)url.openConnection());
            connection.addRequestProperty("User-Agent", "Mozilla/4.0");
            InputStream input;
            if (connection.getResponseCode() == 200)  // this must be called before 'getErrorStream()' works
                input = connection.getInputStream();
            else input = connection.getErrorStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String msg;
            while ((msg =reader.readLine()) != null)
                html.append(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int RandomSkinIndex = html.indexOf("<span class=\"card-title green-text truncate\">", MathHelper.nextInt(new Random(),11000,65000)) + 45;
        if(RandomSkinIndex>44){
            int RandomSkinIndexStop = html.indexOf("<", RandomSkinIndex);
            String nickname = html.substring(RandomSkinIndex, RandomSkinIndexStop);
            if(blackList.contains(nickname))nickname = RandomMojangSkin.newNick(variant);
            if((variant.equals(SkinVariant.slim)&&!isSlim(nickname))||((variant.equals(SkinVariant.classic)&&isSlim(nickname)))) nickname = RandomMojangSkin.newNick(variant);
            return nickname;
        }
        else return "Notch";
    }
    private static final JsonParser parser = new JsonParser();
    public static boolean hasCape(String nick){
        String decodedSTR = new String(Base64.getDecoder().decode(MojangSkinProvider.getSkin(nick).getValue()));
        JsonObject decodedJSON = parser.parse(decodedSTR).getAsJsonObject();
        return decodedJSON.getAsJsonObject("textures").has("CAPE");
        //return decodedJSON.getAsJsonObject("textures").getAsJsonObject("CAPE").getAsJsonPrimitive("url").getAsString().contains("http");
    }
    public static boolean isSlim(String nick){
        String decodedSTR = new String(Base64.getDecoder().decode(MojangSkinProvider.getSkin(nick).getValue()));
        JsonObject decodedJSON = parser.parse(decodedSTR).getAsJsonObject();
        return decodedJSON.getAsJsonObject("textures").getAsJsonObject("SKIN").has("metadata");
    }
}
