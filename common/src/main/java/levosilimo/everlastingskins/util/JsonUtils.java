/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public class JsonUtils {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final JsonParser JSON_PARSER = new JsonParser();

    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    /**
     * Serializes a {@link JsonElement} through the JsonElement adapter.
     *
     * <p>Do NOT pass a {@link JsonObject} to {@link #toJson(Object)}: the
     * reflective Object path on gson 2.2.x serializes {@code JsonObject}'s
     * backing {@code members} field instead of its content (the record lands
     * on disk as {@code {"members": {...}}} and no longer deserializes),
     * which the 1.6.4 lane's real file round-trip exposed (all sibling lanes
     * mock SkinIO and never hit it). The JsonElement overload dispatches to
     * the JsonElement adapter, which is correct on every gson version.
     */
    public static String toJson(JsonElement element) {
        return GSON.toJson(element);
    }

    /**
     * Parses a JSON document, failing closed: every parse failure surfaces
     * as {@link JsonParseException}. Gson internals raise raw
     * NumberFormatException on truncated {@code \\uXXXX} escapes and
     * IllegalStateException on non-object roots; both are normalized so
     * callers can rely on the documented exception class.
     */
    public static JsonObject parseJson(String json) {
        try {
            return JSON_PARSER.parse(json).getAsJsonObject();
        } catch (JsonParseException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JsonParseException("Malformed JSON", e);
        }
    }
}
