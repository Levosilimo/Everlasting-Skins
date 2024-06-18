package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonObject;
import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.JsonUtils;
import levosilimo.everlastingskins.util.WebUtils;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MojangSkinProvider {

    private static final String API = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_SERVER = "https://sessionserver.mojang.com/session/minecraft/profile/";

    public static CustomSkinProperty getSkin(String username) {
        try {
            UUID uuid = getUUID(username);
            JsonObject texture = JsonUtils.parseJson(WebUtils.GETRequest(new URL(SESSION_SERVER + uuid + "?unsigned=false")))
                    .getAsJsonArray("properties").get(0).getAsJsonObject();
            return new CustomSkinProperty("textures", texture.get("value").getAsString(), texture.get("signature").getAsString(), username);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static UUID getUUID(String name) throws IOException {
        return UUID.fromString(JsonUtils.parseJson(WebUtils.GETRequest(new URL(API + name))).get("id").getAsString()
                .replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
    }
}
