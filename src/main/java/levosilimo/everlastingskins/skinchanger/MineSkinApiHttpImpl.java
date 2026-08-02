/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonObject;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.*;
import levosilimo.everlastingskins.util.*;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MineSkinApiHttpImpl implements MineSkinAPI {

    private static final String USER_AGENT = "EverlastingSkins/MineSkinAPI";
    private static final int MAX_RETRIES = 5;
    private static final int REQUEST_TIMEOUT = 10000;
    private static final URI API_URI = EndpointsConfig.getURI("endpoint.mineskin.generate");

    private final HttpClient httpClient;
    private final String apiKey;

    public MineSkinApiHttpImpl() {
        this(new JavaHttpClient(), Config.MINESKIN_API_KEY.get());
    }

    public MineSkinApiHttpImpl(HttpClient httpClient) {
        this(httpClient, Config.MINESKIN_API_KEY.get());
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
            sleepBeforeRetry();
        }
        return null;
    }

    Optional<MineSkinResponse> genSkinInternal(String url, @Nullable SkinVariant variant) {
        String processedUrl = EverlastingHelpers.sanitizeImageURL(url);

        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("variant", variant != null ? variant.toString() : "auto");
            requestBody.addProperty("name", UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            requestBody.addProperty("visibility", 0);
            requestBody.addProperty("url", processedUrl);

            HttpResponse response = httpClient.execute(
                    API_URI,
                    new HttpClient.RequestBody(requestBody.toString(), HttpClient.HttpType.JSON),
                    HttpClient.HttpType.JSON,
                    USER_AGENT,
                    HttpClient.HttpMethod.POST,
                    buildHeaders(),
                    REQUEST_TIMEOUT
            );

            int statusCode = response.statusCode();

            if (statusCode == 200) {
                return handleSuccessResponse(response, variant);
            }

            if (statusCode == 429) {
                return handleRateLimit(response);
            }

            if (statusCode == 403) {
                return Optional.empty();
            }

            if (statusCode == 400) {
                return null;
            }

            if (statusCode == 500) {
                return null;
            }

            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<MineSkinResponse> handleSuccessResponse(HttpResponse response, @Nullable SkinVariant requestedVariant) {
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

        SkinVariant generatedVariant = resolveVariant(urlResponse.variant());
        String skinId = urlResponse.idStr() != null ? urlResponse.idStr() : String.valueOf(urlResponse.id());

        return Optional.of(new MineSkinResponse(
                property,
                skinId,
                requestedVariant,
                generatedVariant
        ));
    }

    private Optional<MineSkinResponse> handleRateLimit(HttpResponse response) {
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
        return Optional.empty();
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        return headers;
    }

    private static SkinVariant resolveVariant(String variantStr) {
        if (variantStr == null) {
            return SkinVariant.CLASSIC;
        }
        if (variantStr.equalsIgnoreCase("slim")) {
            return SkinVariant.SLIM;
        }
        return SkinVariant.CLASSIC;
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
