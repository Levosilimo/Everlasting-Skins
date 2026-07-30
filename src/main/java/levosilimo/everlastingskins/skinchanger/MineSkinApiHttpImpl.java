/*
 * SkinsRestorer
 * Copyright (C) 2024  SkinsRestorer Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package levosilimo.everlastingskins.skinchanger;

import com.google.gson.Gson;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.skinchanger.requests.mineskin.MineSkinUrlRequest;
import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.*;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.EndpointsConfig;
import levosilimo.everlastingskins.util.HttpClient;
import levosilimo.everlastingskins.util.HttpsUrlConnectionHttpClient;
import levosilimo.everlastingskins.util.PropertyUtils;
import levosilimo.everlastingskins.util.SRHelpers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class MineSkinApiHttpImpl implements MineSkinAPI {
    private static final int MAX_RETRIES = 5;
    private static final String MINESKIN_USER_AGENT = "SkinsRestorer/MineSkinAPI";
    private static final URI MINESKIN_ENDPOINT = EndpointsConfig.getURI("endpoint.mineskin.generate");
    private final ReentrantLock lock = new ReentrantLock();
    private final Gson gson = new Gson();
    private final Logger logger = LogManager.getLogger();
    private final HttpClient httpClient;
    private final String apiKey;

    public MineSkinApiHttpImpl() {
        this(new HttpsUrlConnectionHttpClient(), Config.MINESKIN_API_KEY.get());
    }

    public MineSkinApiHttpImpl(HttpClient httpClient) {
        this(httpClient, Config.MINESKIN_API_KEY.get());
    }

    public MineSkinApiHttpImpl(HttpClient httpClient, String apiKey) {
        this.httpClient = httpClient;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public MineSkinResponse genSkin(String imageUrl, @Nullable SkinVariant skinVariant) {
        imageUrl = SRHelpers.sanitizeImageURL(imageUrl);
        int retryAttempts = 0;
        do {
            Optional<MineSkinResponse> optional;
            lock.lock();
            try {
                optional = genSkinInternal(imageUrl, skinVariant);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                logger.debug("[ERROR] MineSkin Failed! IOException (connection/disk): (" + imageUrl + ")", e);
                optional = null;
            } finally {
                lock.unlock();
            }

            if (optional == null) {
                return null;
            }

            if (optional.isPresent()) {
                return optional.get();
            }
        } while (++retryAttempts < MAX_RETRIES);
        return null;
    }

    Optional<MineSkinResponse> genSkinInternal(String imageUrl, @Nullable SkinVariant skinVariant) throws IOException, InterruptedException {
        HttpResponse httpResponse = queryURL(imageUrl, skinVariant);
        logger.debug("MineSkinAPI: Response: " + httpResponse);

        switch (httpResponse.statusCode()) {
            case 200 -> {
                MineSkinUrlResponse response = httpResponse.getBodyAs(MineSkinUrlResponse.class);
                MineSkinTexture texture = response.data().texture();
                CustomSkinProperty property = new CustomSkinProperty(texture.value(), texture.signature(), texture.url());
                SkinVariant generatedVariant;
                try {
                    generatedVariant = PropertyUtils.getSkinVariant(property);
                } catch (Exception e) {
                    generatedVariant = null;
                }
                return Optional.of(new MineSkinResponse(property, response.idStr(),
                        skinVariant, generatedVariant));
            }
            case 500, 400 -> {
                MineSkinErrorResponse response = httpResponse.getBodyAs(MineSkinErrorResponse.class);
                String error = response.errorCode();
                logger.debug(String.format("[ERROR] MineSkin Failed! Reason: %s Image URL: %s", error, imageUrl));
                // try again
                return switch (error) {
                    case "failed_to_create_id", "skin_change_failed" -> {
                        logger.debug("Trying again in 6 seconds...");
                        TimeUnit.SECONDS.sleep(6);
                        yield Optional.empty();
                    }
                    default -> null;
                };
            }
            case 403 -> {
                MineSkinErrorResponse response = httpResponse.getBodyAs(MineSkinErrorResponse.class);
                String errorCode = response.errorCode();
                String error = response.error();
                if (errorCode.equals("invalid_api_key")) {
                    logger.error("[ERROR] MineSkin API key is invalid! Reason: " + error);
                    switch (error) {
                        case "Invalid API Key" ->
                                logger.error(String.format("The API Key provided is not registered on MineSkin! Please empty \"%s\" in plugins/SkinsRestorer/config.yml and run /sr reload", Config.MINESKIN_API_KEY.getPath()));
                        case "Client not allowed" ->
                                logger.error("This server ip is not on the api key allowed IPs list!");
                        case "Origin not allowed" ->
                                logger.error("This server Origin is not on the api key allowed Origins list!");
                        case "Agent not allowed" ->
                                logger.error(String.format("SkinsRestorer's agent \"%s\" is not on the api key allowed agents list!", MINESKIN_USER_AGENT));
                        default -> logger.error("Unknown error, please report this to SkinsRestorer's discord!");
                    }

                }
                return Optional.empty();
            }
            case 429 -> {
                MineSkinErrorDelayResponse response = httpResponse.getBodyAs(MineSkinErrorDelayResponse.class);

                // If "Too many requests"
                if (response.delay() != null) {
                    TimeUnit.SECONDS.sleep(response.delay());
                } else if (response.nextRequest() != null) {
                    Instant nextRequestInstant = Instant.ofEpochSecond(response.nextRequest());
                    int delay = (int) Duration.between(Instant.now(), nextRequestInstant).getSeconds();

                    if (delay > 0) {
                        TimeUnit.SECONDS.sleep(delay);
                    }
                } else { // Should normally not happen
                    TimeUnit.SECONDS.sleep(6);
                }

                return Optional.empty(); // try again after nextRequest
            }
            default -> {
                logger.debug("[ERROR] MineSkin Failed! Unknown error: (Image URL: " + imageUrl + ") " + httpResponse.statusCode());
                return Optional.empty();
                //throw new MineSkinExceptionShared(Message.ERROR_MS_API_FAILED);
            }
        }
    }

    private HttpResponse queryURL(String url, @Nullable SkinVariant skinVariant) throws IOException {
        for (int i = 0; true; i++) { // try 3 times if server not responding
            try {

                Map<String, String> headers = new HashMap<>();
                Optional<String> apiKey = getApiKey();
                if (apiKey.isPresent()) {
                    headers.put("Authorization", String.format("Bearer %s", apiKey));
                }

                return httpClient.execute(
                        MINESKIN_ENDPOINT,
                        new HttpClient.RequestBody(gson.toJson(new MineSkinUrlRequest(
                                skinVariant,
                                null,
                                null,
                                url
                        )), HttpClient.HttpType.JSON),
                        HttpClient.HttpType.JSON,
                        MINESKIN_USER_AGENT,
                        HttpClient.HttpMethod.POST,
                        headers,
                        90_000
                );
            } catch (IOException e) {
                if (i >= 2) {
                    throw new IOException(e);
                }
            }
        }
    }

    private Optional<String> getApiKey() {
        if (apiKey.isEmpty() || apiKey.equals("key")) {
            return Optional.empty();
        }

        return Optional.of(apiKey);
    }
}