package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangProfileResponse;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.skinchanger.responses.profile.EclipseProfileResponse;
import levosilimo.everlastingskins.skinchanger.responses.profile.MineToolsProfileResponse;
import levosilimo.everlastingskins.skinchanger.responses.profile.PropertyResponse;
import levosilimo.everlastingskins.skinchanger.responses.uuid.EclipseUUIDResponse;
import levosilimo.everlastingskins.skinchanger.responses.uuid.MineToolsUUIDResponse;
import levosilimo.everlastingskins.skinchanger.responses.uuid.MojangUUIDResponse;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.HttpClient;
import levosilimo.everlastingskins.util.HttpsUrlConnectionHttpClient;
import levosilimo.everlastingskins.util.SRHelpers;
import levosilimo.everlastingskins.util.UUIDUtils;
import net.minecraft.util.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MojangApiHttpImpl implements MojangAPI {

    private final MojangEndpoints endpoints;
    private final Logger logger = LogManager.getLogger(MojangApiHttpImpl.class);
    private final HttpClient httpClient;

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
        Optional<UUID> uuidParseResult = UUIDUtils.tryParseUniqueId(nameOrUniqueId);
        if (SRHelpers.invalidMinecraftUsername(nameOrUniqueId) && !uuidParseResult.isPresent()) {
            return Optional.empty();
        }

        Optional<UUID> uuidResult = !uuidParseResult.isPresent()
                ? getUUID(nameOrUniqueId) : uuidParseResult;
        if (!uuidResult.isPresent()) {
            return Optional.empty();
        }

        return getProfile(new Tuple<>(nameOrUniqueId, uuidResult)).map(propertyResponse ->
                new MojangSkinDataResult(uuidResult.get(), propertyResponse));
    }

    @Override
    public Optional<UUID> getUUID(String playerName) {
        if (SRHelpers.invalidMinecraftUsername(playerName)) {
            return Optional.empty();
        }

        Optional<UUID> uuid = Optional.empty();

        List<Supplier<Optional<UUID>>> uuidSources = Arrays.asList(
                () -> getUUIDEclipse(playerName),
                () -> getUUIDMojang(playerName),
                () -> getUUIDMineTools(playerName)
        );

        for (Supplier<Optional<UUID>> source : uuidSources) {
            uuid = source.get();
            if (uuid.isPresent()) {
                break;
            }
        }

        return uuid;
    }

    public Optional<UUID> getUUIDEclipse(String playerName) {
        HttpResult result = readURL(URI.create(endpoints.uuidEclipse().replace("%playerName%", playerName)));
        if (!result.isSuccess()) {
            return Optional.empty();
        }
        HttpResponse httpResponse = result.get();
        if (httpResponse.statusCode() != 200) {
            return Optional.empty();
        }

        EclipseUUIDResponse response = httpResponse.getBodyAs(EclipseUUIDResponse.class);
        if (response == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(response.uuid());
    }

    public Optional<UUID> getUUIDMojang(String playerName) {
        HttpResult result = readURL(URI.create(endpoints.uuidMojang().replace("%playerName%", playerName)));
        if (!result.isSuccess()) {
            return Optional.empty();
        }
        HttpResponse httpResponse = result.get();

        // Not found
        if (httpResponse.statusCode() == 204 || httpResponse.statusCode() == 404 || httpResponse.body().isEmpty()) {
            return Optional.empty();
        }

        // Rate limited — return empty so the fallback chain in getUUID()
        // tries MineTools instead. Propagating the HTTP code to callers
        // doesn't add value here: all callers treat empty as "try next source."
        if (httpResponse.statusCode() == 429) {
            return Optional.empty();
        }

        MojangUUIDResponse response = httpResponse.getBodyAs(MojangUUIDResponse.class);
        if (response == null || response.error() != null) {
            return Optional.empty();
        }

        return Optional.ofNullable(response.id())
                .map(UUIDUtils::convertToDashed);
    }

    protected Optional<UUID> getUUIDMineTools(String playerName) {
        HttpResult result = readURL(URI.create(endpoints.uuidMineTools().replace("%playerName%", playerName)), 10_000);
        if (!result.isSuccess()) {
            return Optional.empty();
        }
        HttpResponse httpResponse = result.get();
        MineToolsUUIDResponse response = httpResponse.getBodyAs(MineToolsUUIDResponse.class);
        if (response == null) {
            return Optional.empty();
        }

        if (response.status() != null && response.status().equals("ERR")) {
            return Optional.empty();
        }

        return Optional.ofNullable(response.id())
                .map(UUIDUtils::convertToDashed);
    }

    @Override
    public Optional<CustomSkinProperty> getProfile(Tuple<String, Optional<UUID>> usernameUUIDPair) {
        Optional<CustomSkinProperty> customSkinProperty = Optional.empty();

        List<Supplier<Optional<CustomSkinProperty>>> profileSources = Arrays.asList(
                () -> getProfileEclipse(usernameUUIDPair),
                () -> getProfileMojang(usernameUUIDPair),
                () -> getProfileMineTools(usernameUUIDPair)
        );

        for (Supplier<Optional<CustomSkinProperty>> source : profileSources) {
            customSkinProperty = source.get();
            if (customSkinProperty.isPresent()) {
                break;
            }
        }

        return customSkinProperty;
    }

    public Optional<CustomSkinProperty> getProfileEclipse(Tuple<String, Optional<UUID>> usernameUUIDPair) {
        HttpResult result = readURL(URI.create(endpoints.profileEclipse().replace("%uuid%", usernameUUIDPair.getSecond().get().toString())));
        if (!result.isSuccess()) {
            return Optional.empty();
        }
        HttpResponse httpResponse = result.get();
        if (httpResponse.statusCode() != 200) {
            return Optional.empty();
        }

        EclipseProfileResponse response = httpResponse.getBodyAs(EclipseProfileResponse.class);
        if (response == null || response.isPropertyNull()) {
            return Optional.empty();
        }

        return Optional.of(new CustomSkinProperty(response.skinProperty().value(), response.skinProperty().signature(), usernameUUIDPair.getFirst()));
    }

    public Optional<CustomSkinProperty> getProfileMojang(Tuple<String, Optional<UUID>> usernameUUIDPair) {
        HttpResult result = readURL(URI.create(endpoints.profileMojang().replace("%uuid%", UUIDUtils.convertToNoDashes(usernameUUIDPair.getSecond().get()))));
        if (!result.isSuccess()) {
            return Optional.empty();
        }
        HttpResponse httpResponse = result.get();
        if (httpResponse.statusCode() != 200) {
            return Optional.empty();
        }
        MojangProfileResponse response = httpResponse.getBodyAs(MojangProfileResponse.class);
        if (response.properties() == null) {
            return Optional.empty();
        }

        PropertyResponse property = response.properties()[0];
        if (property.value().isEmpty() || property.signature().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new CustomSkinProperty(property.value(), property.signature(), usernameUUIDPair.getFirst()));
    }

    protected Optional<CustomSkinProperty> getProfileMineTools(Tuple<String, Optional<UUID>> usernameUUIDPair) {
        HttpResult result = readURL(URI.create(endpoints.profileMineTools().replace("%uuid%", UUIDUtils.convertToNoDashes(usernameUUIDPair.getSecond().get()))), 10_000);
        if (!result.isSuccess()) {
            return Optional.empty();
        }
        HttpResponse httpResponse = result.get();
        MineToolsProfileResponse response = httpResponse.getBodyAs(MineToolsProfileResponse.class);
        if (response.raw() == null) {
            return Optional.empty();
        }

        MineToolsProfileResponse.Raw raw = response.raw();
        if (raw.status() != null && raw.status().equals("ERR")) {
            return Optional.empty();
        }

        PropertyResponse property = raw.properties()[0];
        if (property.value().isEmpty() || property.signature().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new CustomSkinProperty(property.value(), property.signature(), usernameUUIDPair.getFirst()));
    }

    private HttpResult readURL(URI uri) {
        return readURL(uri, 5_000);
    }

    private HttpResult readURL(URI uri, int timeout) {
        try {
            return new HttpResult.Success(httpClient.execute(
                    uri,
                    null,
                    HttpClient.HttpType.JSON,
                    "SkinRestorer",
                    HttpClient.HttpMethod.GET,
                    Collections.emptyMap(),
                    timeout
            ));
        } catch (IOException e) {
            logger.debug("Error while reading URL: " + uri, e);
            return new HttpResult.Failure(e);
        }
    }
}
