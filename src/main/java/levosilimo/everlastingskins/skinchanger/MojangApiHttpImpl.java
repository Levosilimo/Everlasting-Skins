/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangProfileResponse;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.skinchanger.responses.profile.MineToolsProfileResponse;
import levosilimo.everlastingskins.skinchanger.responses.profile.PropertyResponse;
import levosilimo.everlastingskins.skinchanger.responses.uuid.MineToolsUUIDResponse;
import levosilimo.everlastingskins.skinchanger.responses.uuid.MojangUUIDResponse;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.EverlastingHelpers;
import levosilimo.everlastingskins.util.HttpClient;
import levosilimo.everlastingskins.util.HttpsUrlConnectionHttpClient;
import levosilimo.everlastingskins.util.UUIDUtils;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.UUID;

/**
 * Mojang profile and UUID lookups over HTTP.
 * <p>
 * Providers are consulted in order (Mojang, MineTools); the first one that
 * returns a usable body wins. Non-200 status codes and transport failures
 * simply advance the chain to the next provider.
 */
public class MojangApiHttpImpl implements MojangAPI {

    private static final String USER_AGENT = "EverlastingSkins/1.0";
    private static final int REQUEST_TIMEOUT = 10000;

    private final MojangEndpoints endpoints;
    private final HttpClient httpClient;
    private final MojangProfileCache cache = new MojangProfileCache();

    public MojangApiHttpImpl() {
        this(MojangEndpoints.DEFAULT, new HttpsUrlConnectionHttpClient());
    }

    public MojangApiHttpImpl(MojangEndpoints endpoints) {
        this(endpoints, new HttpsUrlConnectionHttpClient());
    }

    public MojangApiHttpImpl(MojangEndpoints endpoints, HttpClient httpClient) {
        this.endpoints = endpoints;
        this.httpClient = httpClient;
    }

    @Override
    public Optional<MojangSkinDataResult> getSkin(String nameOrUniqueId) {
        Optional<UUID> parsedUuid = UUIDUtils.tryParseUniqueId(nameOrUniqueId);
        boolean isUsername = !parsedUuid.isPresent();

        if (isUsername && EverlastingHelpers.invalidMinecraftUsername(nameOrUniqueId)) {
            return Optional.empty();
        }

        if (isUsername && Config.mojangProfileCacheEnabled) {
            // A cached profile skips the profile chain; the UUID lookup still
            // runs (2 requests instead of 4).
            CustomSkinProperty cached = cache.get(nameOrUniqueId);
            if (cached != null) {
                return getUUID(nameOrUniqueId).map(uuid -> new MojangSkinDataResult(uuid, cached));
            }
        }

        UUID playerUuid;
        if (parsedUuid.isPresent()) {
            playerUuid = parsedUuid.get();
        } else {
            Optional<UUID> resolved = getUUID(nameOrUniqueId);
            if (!resolved.isPresent()) {
                return Optional.empty();
            }
            playerUuid = resolved.get();
        }

        Optional<CustomSkinProperty> property = getProfile(new ProfileLookup(nameOrUniqueId, playerUuid));
        if (!property.isPresent()) {
            return Optional.empty();
        }

        if (isUsername && Config.mojangProfileCacheEnabled) {
            cache.put(nameOrUniqueId, property.get());
        }

        return Optional.of(new MojangSkinDataResult(playerUuid, property.get()));
    }

    @Override
    public Optional<UUID> getUUID(String playerName) {
        return firstPresent(
                () -> tryMojangUuid(playerName),
                () -> tryMineToolsUuid(playerName));
    }

    @Override
    public Optional<CustomSkinProperty> getProfile(ProfileLookup lookup) {
        return firstPresent(
                () -> tryMojangProfile(lookup),
                () -> tryMineToolsProfile(lookup));
    }

    /**
     * GET a JSON document, mapping the HTTP outcome to an Optional:
     * non-200 status codes and transport failures yield empty.
     */
    private <T> Optional<T> fetchJson(URI uri, Class<T> bodyType) {
        try {
            HttpResponse response = httpClient.execute(
                    uri,
                    null,
                    HttpClient.HttpType.JSON,
                    USER_AGENT,
                    HttpClient.HttpMethod.GET,
                    Collections.<String, String>emptyMap(),
                    REQUEST_TIMEOUT
            );
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return Optional.ofNullable(response.getBodyAs(bodyType));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Resolve the first present candidate, short-circuiting: the remaining
     * providers are only consulted when the preceding ones came up empty, so
     * a successful Mojang lookup costs exactly one HTTP request.
     */
    @SafeVarargs
    private static <T> Optional<T> firstPresent(Supplier<Optional<T>>... candidates) {
        for (Supplier<Optional<T>> candidate : candidates) {
            Optional<T> result = candidate.get();
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    private Optional<UUID> tryMojangUuid(String playerName) {
        URI uri = URI.create(endpoints.uuidMojang().replace("%playerName%", playerName));
        return fetchJson(uri, MojangUUIDResponse.class)
                .filter(mojang -> mojang.id() != null && !mojang.id().isEmpty())
                .flatMap(mojang -> UUIDUtils.tryParseUniqueId(mojang.id()));
    }

    private Optional<UUID> tryMineToolsUuid(String playerName) {
        URI uri = URI.create(endpoints.uuidMineTools().replace("%playerName%", playerName));
        return fetchJson(uri, MineToolsUUIDResponse.class)
                .filter(mineTools -> "OK".equalsIgnoreCase(mineTools.status()))
                .filter(mineTools -> mineTools.id() != null && !mineTools.id().isEmpty())
                .flatMap(mineTools -> UUIDUtils.tryParseUniqueId(mineTools.id()));
    }

    private Optional<CustomSkinProperty> tryMojangProfile(ProfileLookup lookup) {
        URI uri = URI.create(endpoints.profileMojang().replace("%uuid%", UUIDUtils.convertToNoDashes(lookup.getUuid())));
        return fetchJson(uri, MojangProfileResponse.class)
                .map(MojangProfileResponse::properties)
                .flatMap(MojangApiHttpImpl::extractTexturesProperty);
    }

    private Optional<CustomSkinProperty> tryMineToolsProfile(ProfileLookup lookup) {
        URI uri = URI.create(endpoints.profileMineTools().replace("%uuid%", UUIDUtils.convertToNoDashes(lookup.getUuid())));
        return fetchJson(uri, MineToolsProfileResponse.class)
                .map(MineToolsProfileResponse::raw)
                .filter(raw -> "OK".equalsIgnoreCase(raw.status()))
                .flatMap(raw -> extractTexturesProperty(raw.properties()));
    }

    /**
     * Find the {@code textures} property with a non-empty value, matching the
     * shape of the session server profile response.
     */
    private static Optional<CustomSkinProperty> extractTexturesProperty(PropertyResponse[] properties) {
        if (properties == null) {
            return Optional.empty();
        }
        for (PropertyResponse property : properties) {
            if ("textures".equals(property.name())
                    && property.value() != null
                    && !property.value().isEmpty()) {
                return Optional.of(new CustomSkinProperty(
                        "textures", property.value(), property.signature(), SkinAction.SOURCE_MOJANG));
            }
        }
        return Optional.empty();
    }
}
