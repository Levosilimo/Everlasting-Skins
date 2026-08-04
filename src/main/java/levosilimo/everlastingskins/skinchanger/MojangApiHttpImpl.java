/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonParseException;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.skinchanger.command.SkinActionCommand;
import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangProfileResponse;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.skinchanger.responses.profile.*;
import levosilimo.everlastingskins.skinchanger.responses.uuid.*;
import levosilimo.everlastingskins.util.*;

import java.io.IOException;
import java.net.URI;
import java.util.*;

public class MojangApiHttpImpl implements MojangAPI {

    private static final String USER_AGENT = "EverlastingSkins/1.0";
    private static final int REQUEST_TIMEOUT = 10000;

    private final MojangEndpoints endpoints;
    private final HttpClient httpClient;
    private final MojangProfileCache cache = new MojangProfileCache();

    public MojangApiHttpImpl() {
        this(MojangEndpoints.DEFAULT, new JavaHttpClient());
    }

    public MojangApiHttpImpl(MojangEndpoints endpoints) {
        this(endpoints, new JavaHttpClient());
    }

    public MojangApiHttpImpl(MojangEndpoints endpoints, HttpClient httpClient) {
        this.endpoints = endpoints;
        this.httpClient = httpClient;
    }

    @Override
    public Optional<MojangSkinDataResult> getSkin(String nameOrUniqueId) {
        Optional<UUID> uuidParseResult = UUIDUtils.tryParseUniqueId(nameOrUniqueId);

        if (!uuidParseResult.isPresent()) {
            if (EverlastingHelpers.invalidMinecraftUsername(nameOrUniqueId)) {
                return Optional.empty();
            }
            // Username path: the cached profile property avoids the 3-provider
            // profile chain; the UUID lookup still runs (1 request instead of 6).
            CustomSkinProperty cached = cache.get(nameOrUniqueId);
            if (cached != null) {
                return getUUID(nameOrUniqueId).map(uuid -> new MojangSkinDataResult(uuid, cached));
            }
        }

        UUID playerUuid;
        if (uuidParseResult.isPresent()) {
            playerUuid = uuidParseResult.get();
        } else {
            Optional<UUID> resolved = getUUID(nameOrUniqueId);
            if (!resolved.isPresent()) {
                return Optional.empty();
            }
            playerUuid = resolved.get();
        }

        ProfileLookup lookup = new ProfileLookup(nameOrUniqueId, playerUuid);
        Optional<CustomSkinProperty> property = getProfile(lookup);
        if (!property.isPresent()) {
            return Optional.empty();
        }
        if (uuidParseResult.isEmpty()) {
            cache.put(nameOrUniqueId, property.get());
        }

        return Optional.of(new MojangSkinDataResult(playerUuid, property.get()));
    }

    @Override
    public Optional<UUID> getUUID(String playerName) {
        return tryMojangUuid(playerName)
                .or(() -> tryMineToolsUuid(playerName));
    }

    @Override
    public Optional<CustomSkinProperty> getProfile(ProfileLookup lookup) {
        return tryMojangProfile(lookup)
                .or(() -> tryMineToolsProfile(lookup));
    }

    private Optional<UUID> tryMojangUuid(String playerName) {
        try {
            String url = endpoints.uuidMojang().replace("%playerName%", playerName);
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
                return Optional.empty();
            }
            MojangUUIDResponse mojang = parseBodyOrNull(response, MojangUUIDResponse.class);
            if (mojang == null) {
                return Optional.empty();
            }
            if (mojang.id() == null || mojang.id().isEmpty()) {
                return Optional.empty();
            }
            // id from Mojang is a 32-char hex string (no dashes)
            Optional<UUID> parsed = UUIDUtils.tryParseUniqueId(mojang.id());
            return parsed;
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<UUID> tryMineToolsUuid(String playerName) {
        try {
            String url = endpoints.uuidMineTools().replace("%playerName%", playerName);
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
                return Optional.empty();
            }
            MineToolsUUIDResponse mineTools = parseBodyOrNull(response, MineToolsUUIDResponse.class);
            if (mineTools == null) {
                return Optional.empty();
            }
            if (!"OK".equalsIgnoreCase(mineTools.status())) {
                return Optional.empty();
            }
            if (mineTools.id() == null || mineTools.id().isEmpty()) {
                return Optional.empty();
            }
            return UUIDUtils.tryParseUniqueId(mineTools.id());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<CustomSkinProperty> tryMojangProfile(ProfileLookup lookup) {
        try {
            String uuidNoDash = lookup.uuid().toString().replace("-", "");
            String url = endpoints.profileMojang().replace("%uuid%", uuidNoDash);
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
                return Optional.empty();
            }
            MojangProfileResponse profile = parseBodyOrNull(response, MojangProfileResponse.class);
            if (profile == null) {
                return Optional.empty();
            }
            PropertyResponse[] properties = profile.properties();
            if (properties == null || properties.length == 0) {
                return Optional.empty();
            }
            for (PropertyResponse prop : properties) {
                if ("textures".equals(prop.name()) && prop.value() != null && !prop.value().isEmpty()) {
                    return Optional.of(new CustomSkinProperty("textures", prop.value(), prop.signature(),
                            SkinActionCommand.SOURCE_MOJANG, requestedUsername(lookup)));
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<CustomSkinProperty> tryMineToolsProfile(ProfileLookup lookup) {
        try {
            String uuidNoDash = lookup.uuid().toString().replace("-", "");
            String url = endpoints.profileMineTools().replace("%uuid%", uuidNoDash);
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
                return Optional.empty();
            }
            MineToolsProfileResponse mineTools = parseBodyOrNull(response, MineToolsProfileResponse.class);
            if (mineTools == null) {
                return Optional.empty();
            }
            MineToolsProfileResponse.Raw raw = mineTools.raw();
            if (raw == null) {
                return Optional.empty();
            }
            if (!"OK".equalsIgnoreCase(raw.status())) {
                return Optional.empty();
            }
            PropertyResponse[] properties = raw.properties();
            if (properties == null || properties.length == 0) {
                return Optional.empty();
            }
            for (PropertyResponse prop : properties) {
                if ("textures".equals(prop.name()) && prop.value() != null && !prop.value().isEmpty()) {
                    return Optional.of(new CustomSkinProperty("textures", prop.value(), prop.signature(),
                            SkinActionCommand.SOURCE_MOJANG, requestedUsername(lookup)));
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * The username the lookup was asked for, or null when the lookup was keyed
     * by UUID (no username exists to persist). The A5 skip compares this stored
     * username against the next request, so a skin fetched for "Notch" is only
     * skipped when "Notch" is requested again.
     */
    private static String requestedUsername(ProfileLookup lookup) {
        return UUIDUtils.tryParseUniqueId(lookup.username()).isPresent() ? null : lookup.username();
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
        EverlastingSkins.logger.warn("Failed to parse Mojang API response: " + e);
    }
}
