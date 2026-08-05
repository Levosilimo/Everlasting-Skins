/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
