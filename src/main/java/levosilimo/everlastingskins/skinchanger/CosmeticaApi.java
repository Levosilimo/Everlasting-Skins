/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonParseException;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import levosilimo.everlastingskins.util.EndpointsConfig;
import levosilimo.everlastingskins.util.HttpClient;
import levosilimo.everlastingskins.util.HttpsUrlConnectionHttpClient;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;

/**
 * Cape lookups against the Cosmetica API (api.cosmetica.cc), the public cape
 * directory that backs mskins.net's "with capes" skin listing.
 * <p>
 * {@code GET /players/{name-or-uuid}} returns the player's known capes
 * (OptiFine, MinecraftCapes, LabyMod, ...) as an {@code externalCape} object
 * with a direct {@code texture} URL and a {@code hasElytra} flag. The schema
 * is documented in the Cosmetica OpenAPI at
 * <a href="https://api.cosmetica.cc/docs-json">api.cosmetica.cc/docs-json</a>
 * (operation {@code getPlayer}).
 * <p>
 * Every failure mode fails closed: non-200 status, transport error and
 * malformed JSON all yield {@code null}, so callers never see an exception
 * escape the lookup path.
 */
public class CosmeticaApi {

    private static final String USER_AGENT = "EverlastingSkins/1.0";
    private static final int REQUEST_TIMEOUT = 10_000;

    private final HttpClient httpClient;
    private final String playerEndpointTemplate;

    public CosmeticaApi() {
        this(new HttpsUrlConnectionHttpClient());
    }

    public CosmeticaApi(HttpClient httpClient) {
        this(httpClient, EndpointsConfig.getString("endpoint.cosmetica.player"));
    }

    /** Test seam: fixed endpoint template instead of the configured one. */
    public CosmeticaApi(HttpClient httpClient, String playerEndpointTemplate) {
        this.httpClient = httpClient;
        this.playerEndpointTemplate = playerEndpointTemplate;
    }

    /**
     * Cape state of a player by name or UUID. Returns {@code null} when the
     * player is unknown to Cosmetica or any failure occurred; callers treat
     * null as "keep the candidate, decide elsewhere".
     */
    @Nullable
    public CosmeticaPlayer getPlayer(String nameOrUuid) {
        String url = playerEndpointTemplate.replace("%player%", nameOrUuid);
        try {
            HttpResponse response = httpClient.execute(
                    URI.create(url),
                    null,
                    HttpClient.HttpType.JSON,
                    USER_AGENT,
                    HttpClient.HttpMethod.GET,
                    Collections.emptyMap(),
                    REQUEST_TIMEOUT
            );
            if (response.statusCode() != 200) {
                return null;
            }
            return parseBodyOrNull(response);
        } catch (IOException e) {
            EverlastingSkins.logger.warn("Cosmetica lookup failed for {}: {}", nameOrUuid, e);
            return null;
        }
    }

    /**
     * Parsed subset of {@code GET /players/{id}} (PlayerResponse). The account
     * is nested under {@code player} for non-Cosmetica accounts and under
     * {@code user} for Cosmetica accounts; both carry the same cape fields.
     */
    public record CosmeticaPlayer(boolean isUser, @Nullable Account player, @Nullable Account user) {

        /** The populated account variant, or null when neither was returned. */
        @Nullable
        public Account account() {
            return player != null ? player : user;
        }

        /** Whether either account variant carries an external or internal cape. */
        public boolean hasCape() {
            Account account = account();
            return account != null
                    && (account.externalCape() != null || account.internalCape() != null);
        }

        /** Direct cape texture URL (external preferred, then internal), or null. */
        @Nullable
        public String capeTextureUrl() {
            Account account = account();
            if (account == null) {
                return null;
            }
            if (account.externalCape() != null && account.externalCape().texture() != null) {
                return account.externalCape().texture();
            }
            if (account.internalCape() != null && account.internalCape().texture() != null) {
                return account.internalCape().texture();
            }
            return null;
        }
    }

    /** Account payload shared by the player and user response variants. */
    public record Account(String uuid, String username, @Nullable ExternalCape externalCape,
                          @Nullable InternalCape internalCape) {
    }

    /** Third-party cape (OptiFine, MinecraftCapes, LabyMod, ...). */
    public record ExternalCape(String id, String service, @Nullable String texture, boolean hasElytra) {
    }

    /** Cape hosted by Cosmetica itself. */
    public record InternalCape(String id, @Nullable String texture, boolean hasElytra) {
    }

    /**
     * Gson's strict {@code fromJson} (inside {@link HttpResponse#getBodyAs})
     * throws raw NumberFormatException on truncated {@code \\uXXXX} escapes
     * and IllegalStateException on valid non-object roots. Every parse failure
     * must fail closed to null, never an exception escaping the lookup path.
     */
    @Nullable
    private static CosmeticaPlayer parseBodyOrNull(HttpResponse response) {
        try {
            return response.getBodyAs(CosmeticaPlayer.class);
        } catch (JsonParseException | NumberFormatException | IllegalStateException e) {
            EverlastingSkins.logger.warn("Failed to parse Cosmetica response: " + e);
            return null;
        } catch (RuntimeException e) {
            EverlastingSkins.logger.warn("Failed to parse Cosmetica response: " + e);
            return null;
        }
    }
}
