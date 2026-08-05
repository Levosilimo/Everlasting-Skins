/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinData;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinErrorDelayResponse;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinTexture;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinUrlResponse;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.EndpointsConfig;
import levosilimo.everlastingskins.util.EverlastingHelpers;
import levosilimo.everlastingskins.util.HttpClient;
import levosilimo.everlastingskins.util.HttpsUrlConnectionHttpClient;
import levosilimo.everlastingskins.util.JsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import levosilimo.everlastingskins.util.UrlAllowlist;
import javax.annotation.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Skin generation via the MineSkin API.
 * <p>
 * Uploads an image URL to the generate endpoint and extracts the returned
 * texture property. Rate-limited responses wait out the reported delay;
 * terminal failures (rejected payload) abort the retry loop immediately.
 */
public class MineSkinApiHttpImpl implements MineSkinAPI {

    private static final Logger LOGGER = LogManager.getLogger(MineSkinApiHttpImpl.class);

    private static final String USER_AGENT = "EverlastingSkins/MineSkinAPI";
    private static final int MAX_RETRIES = 5;
    // 10s (the 1.21 branch's battle-tested value; the 1.12.2 branch used 30s
    // before the module merge).
    private static final int REQUEST_TIMEOUT = 10000;
    private static final long RETRY_DELAY_MS = 1000;
    private static final URI API_URI = EndpointsConfig.getURI("endpoint.mineskin.generate");

    /**
     * Default allowlist domains — mirrors the per-version Config default
     * {@code urlAllowlistDomains}. Only consulted when {@code allowlistEnabled}
     * is true.
     */
    private static final List<String> DEFAULT_ALLOWLIST_DOMAINS = Arrays.asList(
            "imgur.com", "storage.googleapis.com", "cdn.discordapp.com",
            "textures.minecraft.net", "namemc.com", "crafatar.com",
            "mc-heads.net", "githubusercontent.com", "minecraftskins.com"
    );

    private final HttpClient httpClient;
    private final String apiKey;
    private final boolean allowlistEnabled;
    private final List<String> allowlistDomains;
    private boolean apiKeyWarningEmitted;

    /**
     * Emits MineSkin configuration warnings. Production logs via the mod
     * logger; tests swap this sink to capture messages without a log backend.
     */
    static Consumer<String> warningSink = MineSkinApiHttpImpl::logConfigWarning;

    /**
     * Decoupled settings injection: {@code apiKey}, {@code allowlistEnabled}
     * and {@code allowlistDomains} replace the per-version
     * {@code Config.MINESKIN_API_KEY} / {@code urlAllowlistEnabled} /
     * {@code urlAllowlistDomains} reads. The defaults below match the
     * per-version Config defaults (empty key, allowlist off).
     */
    public MineSkinApiHttpImpl() {
        this(new HttpsUrlConnectionHttpClient(), "", false, DEFAULT_ALLOWLIST_DOMAINS);
    }

    public MineSkinApiHttpImpl(HttpClient httpClient) {
        this(httpClient, "", false, DEFAULT_ALLOWLIST_DOMAINS);
    }

    public MineSkinApiHttpImpl(HttpClient httpClient, String apiKey) {
        this(httpClient, apiKey, false, DEFAULT_ALLOWLIST_DOMAINS);
    }

    public MineSkinApiHttpImpl(HttpClient httpClient, String apiKey,
            boolean allowlistEnabled, List<String> allowlistDomains) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
        this.allowlistEnabled = allowlistEnabled;
        this.allowlistDomains = allowlistDomains;
    }

    @Nullable
    @Override
    public MineSkinResponse genSkin(String url, @Nullable SkinVariant variant) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            Optional<MineSkinResponse> result = genSkinInternal(url, variant);
            if (result == null) {
                return null;
            }
            if (result.isPresent()) {
                return result.get();
            }
            sleepQuietly(RETRY_DELAY_MS);
        }
        return null;
    }

    Optional<MineSkinResponse> genSkinInternal(String url, @Nullable SkinVariant variant) {
        String processedUrl = EverlastingHelpers.sanitizeImageURL(url);
        if (!UrlAllowlist.isAllowed(processedUrl, allowlistEnabled, allowlistDomains)) {
            return Optional.empty();
        }

        try {
            HttpResponse response = httpClient.execute(
                    API_URI,
                    new HttpClient.RequestBody(buildRequestJson(processedUrl, variant), HttpClient.HttpType.JSON),
                    HttpClient.HttpType.JSON,
                    USER_AGENT,
                    HttpClient.HttpMethod.POST,
                    buildHeaders(),
                    REQUEST_TIMEOUT
            );
            return classifyResponse(response, variant);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String buildRequestJson(String imageUrl, @Nullable SkinVariant variant) {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("variant", variant != null ? variant.toString() : "auto");
        payload.put("name", UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        payload.put("visibility", 0);
        payload.put("url", imageUrl);
        return JsonUtils.toJson(payload);
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<String, String>();
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        return headers;
    }

    /**
     * Classify the HTTP outcome into retry semantics:
     * <ul>
     *   <li>{@code null} - terminal, the request itself was rejected</li>
     *   <li>{@code Optional.empty()} - transient, safe to retry</li>
     *   <li>{@code Optional.of(...)} - success</li>
     * </ul>
     */
    private Optional<MineSkinResponse> classifyResponse(HttpResponse response, @Nullable SkinVariant requestedVariant) {
        int statusCode = response.statusCode();
        if (statusCode == 200) {
            return toSkinResponse(response, requestedVariant);
        }
        if (statusCode == 429) {
            sleepForRateLimit(response);
            return Optional.empty();
        }
        if (statusCode == 401 || statusCode == 403 || statusCode == 404) {
            warnIfMissingApiKey(statusCode);
            return Optional.empty();
        }
        if (statusCode == 400 || statusCode == 500) {
            return null;
        }
        return Optional.empty();
    }

    private void warnIfMissingApiKey(int statusCode) {
        if (apiKey != null && !apiKey.isEmpty()) {
            return;
        }
        if (apiKeyWarningEmitted) {
            return;
        }
        apiKeyWarningEmitted = true;
        warningSink.accept("MineSkin API returned HTTP " + statusCode
                + " and no API key is configured - set MINESKIN_API_KEY in the mod config");
    }

    private static void logConfigWarning(String message) {
        LOGGER.warn(message);
    }

    private static Optional<MineSkinResponse> toSkinResponse(HttpResponse response, @Nullable SkinVariant requestedVariant) {
        MineSkinResponse v1 = toV1Response(parseBodyOrNull(response, MineSkinUrlResponse.class), requestedVariant);
        if (v1 != null) {
            return Optional.of(v1);
        }
        MineSkinResponse v2 = toV2Response(response.body(), requestedVariant);
        if (v2 != null) {
            return Optional.of(v2);
        }
        return Optional.empty();
    }

    private static MineSkinResponse toV1Response(MineSkinUrlResponse urlResponse, @Nullable SkinVariant requestedVariant) {
        if (urlResponse == null) {
            return null;
        }
        MineSkinData data = urlResponse.data();
        if (data == null) {
            return null;
        }
        MineSkinTexture texture = data.texture();
        if (texture == null || texture.value() == null || texture.value().isEmpty()) {
            return null;
        }

        CustomSkinProperty property = new CustomSkinProperty(
                texture.value(),
                texture.signature(),
                SOURCE_MINESKIN
        );
        String skinId = urlResponse.idStr() != null ? urlResponse.idStr() : String.valueOf(urlResponse.id());

        return new MineSkinResponse(
                property,
                skinId,
                requestedVariant,
                resolveVariant(urlResponse.variant())
        );
    }

    /**
     * Parses the V2 success shape (api.mineskin.org/v2: skin.texture.data.
     * value/signature). Returns null when the body is not a V2 success
     * response, so empty or unknown bodies still resolve to "no result".
     */
    private static MineSkinResponse toV2Response(String body, @Nullable SkinVariant requestedVariant) {
        JsonObject skin = v2SkinObject(body);
        if (skin == null) {
            return null;
        }
        JsonObject texture = jsonObject(skin, "texture");
        JsonObject data = texture != null ? jsonObject(texture, "data") : null;
        JsonElement valueElement = data != null ? data.get("value") : null;
        if (valueElement == null || !valueElement.isJsonPrimitive() || valueElement.getAsString().isEmpty()) {
            return null;
        }

        String signature = jsonString(data, "signature");
        String skinId = jsonString(skin, "uuid");
        String variant = jsonString(skin, "variant");

        CustomSkinProperty property = new CustomSkinProperty(
                valueElement.getAsString(),
                signature,
                SOURCE_MINESKIN
        );
        return new MineSkinResponse(property, skinId, requestedVariant, resolveVariant(variant));
    }

    private static JsonObject v2SkinObject(String body) {
        try {
            JsonObject json = JsonUtils.parseJson(body);
            return jsonObject(json, "skin");
        } catch (JsonParseException | NumberFormatException | IllegalStateException e) {
            // Gson raises these on truncated escapes and non-object roots; fail closed.
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static JsonObject jsonObject(JsonObject parent, String member) {
        JsonElement element = parent.get(member);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String jsonString(JsonObject parent, String member) {
        JsonElement element = parent.get(member);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static void sleepForRateLimit(HttpResponse response) {
        int waitMs = 0;
        MineSkinErrorDelayResponse delay = parseBodyOrNull(response, MineSkinErrorDelayResponse.class);
        if (delay != null) {
            if (delay.nextRequest() != null && delay.nextRequest() > 0) {
                waitMs = delay.nextRequest();
            } else if (delay.delay() != null && delay.delay() > 0) {
                waitMs = delay.delay() * 1000;
            }
        }
        if (waitMs <= 0) {
            waitMs = v2RateLimitWaitMs(response.body());
        }
        if (waitMs > 0) {
            // The delay is provider-controlled; cap it so a malicious or
            // misconfigured response cannot stall the request thread.
            long capped = Math.min(waitMs, 5000L);
            SkinMetrics.INSTANCE.recordMineSkinDelay(capped);
            try {
                Thread.sleep(capped);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Extracts the wait time from a V2 rate-limit body: rateLimit.next.
     * relative is a millisecond delay; absolute is an epoch-millis timestamp.
     */
    private static int v2RateLimitWaitMs(String body) {
        try {
            JsonObject rateLimit = jsonObject(JsonUtils.parseJson(body), "rateLimit");
            JsonObject next = rateLimit != null ? jsonObject(rateLimit, "next") : null;
            if (next == null) {
                return 0;
            }
            JsonElement relative = next.get("relative");
            if (relative != null && relative.isJsonPrimitive() && relative.getAsLong() > 0) {
                return (int) Math.min(relative.getAsLong(), Integer.MAX_VALUE);
            }
            JsonElement absolute = next.get("absolute");
            if (absolute != null && absolute.isJsonPrimitive()) {
                long wait = absolute.getAsLong() - System.currentTimeMillis();
                return (int) Math.max(0, Math.min(wait, Integer.MAX_VALUE));
            }
            return 0;
        } catch (JsonParseException | NumberFormatException | IllegalStateException e) {
            return 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static SkinVariant resolveVariant(String variantStr) {
        if (variantStr != null && variantStr.equalsIgnoreCase("slim")) {
            return SkinVariant.SLIM;
        }
        return SkinVariant.CLASSIC;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Gson's strict {@code fromJson} (inside {@link HttpResponse#getBodyAs})
     * throws raw NumberFormatException on truncated {@code \\uXXXX} escapes
     * and IllegalStateException on valid non-object roots, neither a
     * JsonSyntaxException. Every parse failure must fail closed: null, never
     * an exception escape out of the HTTP parse paths.
     */
    private static <T> T parseBodyOrNull(HttpResponse response, Class<T> clazz) {
        try {
            return response.getBodyAs(clazz);
        } catch (JsonParseException | NumberFormatException | IllegalStateException e) {
            logParseFailure(e);
            return null;
        } catch (RuntimeException e) {
            logParseFailure(e);
            return null;
        }
    }

    private static void logParseFailure(RuntimeException e) {
        LOGGER.warn("Failed to parse MineSkin API response: " + e);
    }
}
