package levosilimo.everlastingskins.skinchanger.responses;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HttpResponse {
    private final int statusCode;
    private final String body;
    private final Map<String, List<String>> headers;

    public HttpResponse(int statusCode, String body, Map<String, List<String>> headers) {
        this.statusCode = statusCode;
        this.body = body;
        this.headers = headers;
    }

    public int statusCode() {
        return statusCode;
    }

    public String body() {
        return body;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        HttpResponse that = (HttpResponse) obj;
        return this.statusCode == that.statusCode &&
                Objects.equals(this.body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statusCode, body);
    }

    @Override
    public String toString() {
        return "HttpResponse[" +
                "statusCode=" + statusCode + ", " +
                "body=" + body + ']';
    }

    private static final Gson GSON = new Gson();

    public <T> T getBodyAs(Class<T> clazz) {
        try {
            return GSON.fromJson(body, clazz);
        } catch (JsonSyntaxException ignored) {
        }
        return null;
    }
}