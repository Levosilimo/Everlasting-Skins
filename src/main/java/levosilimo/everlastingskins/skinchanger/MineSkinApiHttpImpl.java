/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.Config;
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
import levosilimo.everlastingskins.util.UrlAllowlist;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Skin generation via the MineSkin API.
 * <p>
 * Uploads an image URL to the generate endpoint and extracts the returned
 * texture property. Rate-limited responses wait out the reported delay;
 * terminal failures (rejected payload) abort the retry loop immediately.
 */
public class MineSkinApiHttpImpl implements MineSkinAPI {

    private static final String USER_AGENT = "EverlastingSkins/MineSkinAPI";
    private static final int MAX_RETRIES = 5;
    private static final int REQUEST_TIMEOUT = 30000;
    private static final long RETRY_DELAY_MS = 1000;
    private static final URI API_URI = EndpointsConfig.getURI("endpoint.mineskin.generate");

    private final HttpClient httpClient;
    private final String apiKey;

    public MineSkinApiHttpImpl() {
        this(new HttpsUrlConnectionHttpClient(), Config.MINESKIN_API_KEY);
    }

    public MineSkinApiHttpImpl(HttpClient httpClient) {
        this(httpClient, Config.MINESKIN_API_KEY);
    }

    public MineSkinApiHttpImpl(HttpClient httpClient, String apiKey) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
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
        if (!UrlAllowlist.isAllowed(processedUrl, Config.urlAllowlistEnabled, Arrays.asList(Config.urlAllowlistDomains))) {
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
        if (statusCode == 400 || statusCode == 500) {
            return null;
        }
        return Optional.empty();
    }

    private static Optional<MineSkinResponse> toSkinResponse(HttpResponse response, @Nullable SkinVariant requestedVariant) {
        MineSkinUrlResponse urlResponse = response.getBodyAs(MineSkinUrlResponse.class);
        if (urlResponse == null) {
            return Optional.empty();
        }
        MineSkinData data = urlResponse.data();
        if (data == null) {
            return Optional.empty();
        }
        MineSkinTexture texture = data.texture();
        if (texture == null || texture.value() == null || texture.value().isEmpty()) {
            return Optional.empty();
        }

        CustomSkinProperty property = new CustomSkinProperty(
                texture.value(),
                texture.signature(),
                "MineSkin"
        );
        String skinId = urlResponse.idStr() != null ? urlResponse.idStr() : String.valueOf(urlResponse.id());

        return Optional.of(new MineSkinResponse(
                property,
                skinId,
                requestedVariant,
                resolveVariant(urlResponse.variant())
        ));
    }

    private static void sleepForRateLimit(HttpResponse response) {
        MineSkinErrorDelayResponse delay = response.getBodyAs(MineSkinErrorDelayResponse.class);
        if (delay != null) {
            int waitMs = 0;
            if (delay.nextRequest() != null && delay.nextRequest() > 0) {
                waitMs = delay.nextRequest();
            } else if (delay.delay() != null && delay.delay() > 0) {
                waitMs = delay.delay() * 1000;
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
}
