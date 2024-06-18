package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonObject;
import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.JsonUtils;
import levosilimo.everlastingskins.util.WebUtils;

import java.io.IOException;
import java.net.URL;

public class MineskinSkinProvider {

    private static final String API = "https://api.mineskin.org/generate/url";
    private static final String USER_AGENT = "SkinRestorer";
    private static final String TYPE = "application/json";

    public static CustomSkinProperty getSkin(String url, SkinVariant variant) {
        try {
            String input = ("{\"variant\":\"%s\",\"name\":\"%s\",\"visibility\":%d,\"url\":\"%s\"}");
            input = String.format(input, variant.toString(), "none", 1, url);
            JsonObject texture = JsonUtils.parseJson(WebUtils.POSTRequest(new URL(API), USER_AGENT, TYPE, TYPE, input))
                    .getAsJsonObject("data").getAsJsonObject("texture");
            return new CustomSkinProperty("textures", texture.get("value").getAsString(), texture.get("signature").getAsString(), url);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
