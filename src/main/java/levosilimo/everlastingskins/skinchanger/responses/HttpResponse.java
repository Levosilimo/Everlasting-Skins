package levosilimo.everlastingskins.skinchanger.responses;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.List;
import java.util.Map;

public record HttpResponse(int statusCode, String body, Map<String, List<String>> headers) {
    private static final Gson GSON = new Gson();

    public <T> T getBodyAs(Class<T> clazz) {
        try {
            return GSON.fromJson(body, clazz);
        } catch (JsonSyntaxException ignored) {
        }
        return null;
    }
}
