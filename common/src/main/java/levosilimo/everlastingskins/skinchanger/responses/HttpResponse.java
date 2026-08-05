/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger.responses;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HttpResponse {
    private static final Gson GSON = new Gson();

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

    public Map<String, List<String>> headers() {
        return headers;
    }

    public <T> T getBodyAs(Class<T> clazz) {
        try {
            return GSON.fromJson(body, clazz);
        } catch (JsonSyntaxException ignored) {
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HttpResponse that = (HttpResponse) o;
        return statusCode == that.statusCode && Objects.equals(body, that.body) && Objects.equals(headers, that.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statusCode, body, headers);
    }

    @Override
    public String toString() {
        return "HttpResponse[statusCode=" + statusCode + ", body=" + body + ", headers=" + headers + "]";
    }
}
