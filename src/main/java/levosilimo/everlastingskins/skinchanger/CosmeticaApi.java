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
 * is documented in the Cosmetica OpenAPI at https://api.cosmetica.cc/docs-json
 * (operation {@code getPlayer}).
 * <p>
 * Every failure mode fails closed: non-200 status, transport error and
 * malformed JSON all yield {@code null}, so callers never see an exception
 * escape the lookup path.
 */
public class CosmeticaApi {

    private static final String USER_AGENT = "EverlastingSkins/1.0";
    private static final int REQUEST_TIMEOUT = 10000;

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

    /**
     * Parsed subset of {@code GET /players/{id}} (PlayerResponse). The account
     * is nested under {@code player} for non-Cosmetica accounts and under
     * {@code user} for Cosmetica accounts; both carry the same cape fields.
     */
    public static final class CosmeticaPlayer {

        private final boolean isUser;
        private final Account player;
        private final Account user;

        public CosmeticaPlayer(boolean isUser, Account player, Account user) {
            this.isUser = isUser;
            this.player = player;
            this.user = user;
        }

        public boolean isUser() {
            return isUser;
        }

        public Account player() {
            return player;
        }

        public Account user() {
            return user;
        }

        /** The populated account variant, or null when neither was returned. */
        @Nullable
        public Account account() {
            return player != null ? player : user;
        }

        /**
         * Whether the account carries a Mojang {@code official} cape: only
         * {@code externalCape.service == "official"} maps to a Mojang CAPE key
         * visible in vanilla Forge 1.12.2 (verified against forgeSrc and the
         * OptiFine source in lib-25). Other services (optifine,
         * minecraft-capes, labymod, 5zig, tlauncher, skinmc) are client-mod
         * only and invisible to vanilla players; {@code internalCape} requires
         * the Cosmetica Forge mod on the viewing client and is excluded too.
         */
        public boolean hasCape() {
            Account account = account();
            return account != null
                    && account.externalCape() != null
                    && "official".equalsIgnoreCase(account.externalCape().service());
        }

        /**
         * Direct cape texture URL when the account carries an official Mojang
         * cape, or null otherwise: non-official services and internal capes
         * are not visible to vanilla players, so they yield no texture for
         * our purposes.
         */
        @Nullable
        public String capeTextureUrl() {
            Account account = account();
            if (account == null || account.externalCape() == null
                    || !"official".equalsIgnoreCase(account.externalCape().service())) {
                return null;
            }
            return account.externalCape().texture();
        }
    }

    /** Account payload shared by the player and user response variants. */
    public static final class Account {

        private final String uuid;
        private final String username;
        private final ExternalCape externalCape;
        private final InternalCape internalCape;

        public Account(String uuid, String username, ExternalCape externalCape, InternalCape internalCape) {
            this.uuid = uuid;
            this.username = username;
            this.externalCape = externalCape;
            this.internalCape = internalCape;
        }

        public String uuid() {
            return uuid;
        }

        public String username() {
            return username;
        }

        @Nullable
        public ExternalCape externalCape() {
            return externalCape;
        }

        @Nullable
        public InternalCape internalCape() {
            return internalCape;
        }
    }

    /** Third-party cape (OptiFine, MinecraftCapes, LabyMod, ...). */
    public static final class ExternalCape {

        private final String id;
        private final String service;
        private final String texture;
        private final boolean hasElytra;

        public ExternalCape(String id, String service, String texture, boolean hasElytra) {
            this.id = id;
            this.service = service;
            this.texture = texture;
            this.hasElytra = hasElytra;
        }

        public String id() {
            return id;
        }

        public String service() {
            return service;
        }

        @Nullable
        public String texture() {
            return texture;
        }

        public boolean hasElytra() {
            return hasElytra;
        }
    }

    /** Cape hosted by Cosmetica itself. */
    public static final class InternalCape {

        private final String id;
        private final String texture;
        private final boolean hasElytra;

        public InternalCape(String id, String texture, boolean hasElytra) {
            this.id = id;
            this.texture = texture;
            this.hasElytra = hasElytra;
        }

        public String id() {
            return id;
        }

        @Nullable
        public String texture() {
            return texture;
        }

        public boolean hasElytra() {
            return hasElytra;
        }
    }
}
