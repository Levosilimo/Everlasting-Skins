/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import levosilimo.everlastingskins.util.JsonUtils;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Fuzz corpus for {@link JsonUtils}, the shared Gson wrapper used by every
 * HTTP/JSON parser in the mod (AI-generated code surface, cf. Pearce et al.,
 * S&P 2022). Properties cover the malformed-bytes contract, canonical
 * serialization of arbitrary objects, and the inertness of hostile string
 * payloads (SQL/NoSQL injection, path traversal, prototype-pollution
 * shapes): parsed hostile content must stay plain data, never take on
 * semantic meaning.
 */
class JsonUtilsFuzzTest {

    @Provide
    net.jqwik.api.Arbitrary<String> malformedJson() {
        return MalformedJsonCorpus.malformedJson();
    }

    @Provide
    net.jqwik.api.Arbitrary<String> hostileValues() {
        return MalformedJsonCorpus.hostileValues();
    }

    @Provide
    net.jqwik.api.Arbitrary<Object> arbitraryValues() {
        return MalformedJsonCorpus.arbitraryValues();
    }

    /* ------------------------------------------------------------------ */
    /*  D1: parseJson fail-closed contract                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Malformed bytes must either be rejected with Gson's own parse failure
     * ({@link JsonParseException} — the only exception class allowed to
     * escape the raw parser) or parse to a non-null {@link JsonObject}.
     * Anything else — a different exception class, an {@link Error}, or a
     * half-parsed result — violates the fail-closed contract.
     */
    @Property(tries = 100)
    @Label("D1: parseJson either rejects malformed bytes or returns a JsonObject, never other failures")
    void parseJson_malformedBytes_returnsNullOrThrows(@ForAll @From("malformedJson") String bytes) {
        try {
            JsonObject result = JsonUtils.parseJson(bytes);
            assertNotNull(result, () -> "parseJson returned null for: " + excerpt(bytes));
        } catch (JsonParseException expected) {
            // malformed bytes fail closed with Gson's documented parse failure
        } catch (Throwable unexpected) {
            fail("unexpected failure class " + unexpected.getClass().getName()
                    + " on: " + excerpt(bytes), unexpected);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  D2: toJson canonical serialization                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Arbitrary acyclic object graphs must serialize without throwing, the
     * output must be valid JSON text (re-parseable), and serialization must
     * be deterministic (same object, same bytes every time).
     */
    @Property(tries = 100)
    @Label("D2: toJson canonicalizes arbitrary objects: no throw, valid JSON, deterministic")
    void toJson_canonicalizesArbitraryObject_noThrow(@ForAll @From("arbitraryValues") Object value) {
        String first = JsonUtils.toJson(value);
        assertNotNull(first);
        // canonical JSON text: the output re-parses as a JsonElement
        JsonElement reparsed = new JsonParser().parse(first);
        assertNotNull(reparsed);
        assertEquals(first, JsonUtils.toJson(value),
                "toJson must be deterministic for: " + excerpt(first));
    }

    /* ------------------------------------------------------------------ */
    /*  D3: hostile payloads stay inert data                               */
    /* ------------------------------------------------------------------ */

    /**
     * SQL/NoSQL injection, path-traversal and prototype-pollution payloads
     * embedded in string values must parse as plain data: every string leaf
     * of the parsed tree is byte-identical to one of the corpus literals
     * (nothing decoded, unescaped further, evaluated, or otherwise given
     * semantic meaning), and parsing is repeatable.
     */
    @Property(tries = 100)
    @Label("D3: hostile string payloads parse to inert data, never to semantic effect")
    void parseJson_sqlInjection_noSemanticEffect(@ForAll @From("hostileValues") String doc) {
        JsonObject parsed = JsonUtils.parseJson(doc);
        assertNotNull(parsed, () -> "hostile document did not parse: " + excerpt(doc));

        Set<String> leaves = new HashSet<String>();
        collectStringLeaves(parsed, leaves);
        for (String leaf : leaves) {
            assertTrue(MalformedJsonCorpus.HOSTILE_LITERALS.contains(leaf) || leaf.isEmpty(),
                    () -> "hostile document yielded interpreted value '" + excerpt(leaf)
                            + "' from: " + excerpt(doc));
        }

        assertEquals(parsed.toString(), JsonUtils.parseJson(doc).toString(),
                "parsing a hostile document must be repeatable");
    }

    /* ================================================================== */
    /*  Helpers                                                           */
    /* ================================================================== */

    private static void collectStringLeaves(JsonElement element, Set<String> leaves) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> member : element.getAsJsonObject().entrySet()) {
                collectStringLeaves(member.getValue(), leaves);
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                collectStringLeaves(array.get(i), leaves);
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            leaves.add(element.getAsString());
        }
    }

    private static String excerpt(String s) {
        if (s.length() <= 96) {
            return s;
        }
        return s.substring(0, 96) + "…(" + s.length() + " chars)";
    }
}
