package levosilimo.everlastingskins.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JsonUtils {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    public static JsonObject parseJson(String json) {
        return new JsonParser().parse(json).getAsJsonObject();
    }
}
