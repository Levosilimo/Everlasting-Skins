package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.EverlastingSkins;
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
import levosilimo.everlastingskins.util.SRHelpers;
import levosilimo.everlastingskins.util.UUIDUtils;
import net.minecraft.util.Tuple;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.function.Supplier;

public class MojangAPIImpl {
    private static final String UUID_ECLIPSE = "https://eclipse.skinsrestorer.net/mojang/uuid/%playerName%";
    private static final String UUID_MOJANG = "https://api.mojang.com/users/profiles/minecraft/%playerName%";
    private static final String UUID_MINETOOLS = "https://api.minetools.eu/uuid/%playerName%";
    private static final String PROFILE_ECLIPSE = "https://eclipse.skinsrestorer.net/mojang/skin/%uuid%";
    private static final String PROFILE_MOJANG = "https://sessionserver.mojang.com/session/minecraft/profile/%uuid%?unsigned=false";
    private static final String PROFILE_MINETOOLS = "https://api.minetools.eu/profile/%uuid%";

    private final Logger logger = EverlastingSkins.logger;
    private final HttpClient httpClient = new HttpClient();

    public MojangSkinDataResult getSkin(String nameOrUniqueId) {
        Optional<UUID> uuidParseResult = UUIDUtils.tryParseUniqueId(nameOrUniqueId);
        if (SRHelpers.invalidMinecraftUsername(nameOrUniqueId) && !uuidParseResult.isPresent()) {
            return null;
        }

        Optional<UUID> uuidResult = !uuidParseResult.isPresent()
                ? getUUID(nameOrUniqueId) : uuidParseResult;
        if (!uuidResult.isPresent()) {
            return null;
        }

        return getProfile(new Tuple<>(nameOrUniqueId, uuidResult)).flatMap(propertyResponse ->
                Optional.of(new MojangSkinDataResult(uuidResult.get(), propertyResponse))).orElse(null);
    }

    /**
     * Get the uuid from a player playerName
     *
     * @param playerName Mojang username of the player
     * @return String uuid trimmed (without dashes)
     */
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

    public Optional<UUID> getUUIDEclipse(String playerName)  {
        HttpResponse httpResponse = readURL(URI.create(UUID_ECLIPSE.replace("%playerName%", playerName)));
        if (httpResponse.statusCode() != 200) {
            return Optional.empty();
        }

        EclipseUUIDResponse response = httpResponse.getBodyAs(EclipseUUIDResponse.class);
        return Optional.ofNullable(response.uuid());
    }

    public Optional<UUID> getUUIDMojang(String playerName)  {
        HttpResponse httpResponse = readURL(URI.create(UUID_MOJANG.replace("%playerName%", playerName)));

        // Not found
        if (httpResponse.statusCode() == 204 || httpResponse.statusCode() == 404 || httpResponse.body().isEmpty()) {
            return Optional.empty();
        }

        // Rate limited
        if (httpResponse.statusCode() == 429) {
            // TODO: Return http code to api and translate internally
            return Optional.empty();
        }

        MojangUUIDResponse response = httpResponse.getBodyAs(MojangUUIDResponse.class);
        if (response.error() != null) {
            return Optional.empty();
        }

        return Optional.ofNullable(response.id())
                .map(UUIDUtils::convertToDashed);
    }

    protected Optional<UUID> getUUIDMineTools(String playerName)  {
        HttpResponse httpResponse = readURL(URI.create(UUID_MINETOOLS.replace("%playerName%", playerName)), 10_000);
        MineToolsUUIDResponse response = httpResponse.getBodyAs(MineToolsUUIDResponse.class);

        if (response.status() != null && response.status().equals("ERR")) {
            return Optional.empty();
        }

        return Optional.ofNullable(response.id())
                .map(UUIDUtils::convertToDashed);
    }

    public Optional<CustomSkinProperty> getProfile(Tuple<String, Optional<UUID>> usernameUUIDPair)  {

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

    public Optional<CustomSkinProperty> getProfileEclipse(Tuple<String, Optional<UUID>> usernameUUIDPair)  {
        HttpResponse httpResponse = readURL(URI.create(PROFILE_ECLIPSE.replace("%uuid%", usernameUUIDPair.getB().get().toString())));
        if (httpResponse.statusCode() != 200) {
            return Optional.empty();
        }

        EclipseProfileResponse response = httpResponse.getBodyAs(EclipseProfileResponse.class);
        if (response.isPropertyNull()) {
            return Optional.empty();
        }

        return Optional.of(new CustomSkinProperty(response.skinProperty.value(), response.skinProperty.signature(), usernameUUIDPair.getA()));
    }

    public Optional<CustomSkinProperty> getProfileMojang(Tuple<String, Optional<UUID>> usernameUUIDPair)  {
        HttpResponse httpResponse = readURL(URI.create(PROFILE_MOJANG.replace("%uuid%", UUIDUtils.convertToNoDashes(usernameUUIDPair.getB().get()))));
        MojangProfileResponse response = httpResponse.getBodyAs(MojangProfileResponse.class);
        if (response.properties() == null) {
            return Optional.empty();
        }

        PropertyResponse property = response.properties()[0];
        if (property.value().isEmpty() || property.signature().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new CustomSkinProperty(property.value(), property.signature(), usernameUUIDPair.getA()));
    }

    protected Optional<CustomSkinProperty> getProfileMineTools(Tuple<String, Optional<UUID>> usernameUUIDPair)  {
        HttpResponse httpResponse = readURL(URI.create(PROFILE_MINETOOLS.replace("%uuid%", UUIDUtils.convertToNoDashes(usernameUUIDPair.getB().get()))), 10_000);
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

        return Optional.of(new CustomSkinProperty(property.value(), property.signature(), usernameUUIDPair.getA()));
    }

    private HttpResponse readURL(URI uri) {
        return readURL(uri, 5_000);
    }

    private HttpResponse readURL(URI uri, int timeout) {
        try {
            return httpClient.execute(
                    uri,
                    null,
                    HttpClient.HttpType.JSON,
                    "SkinRestorer",
                    HttpClient.HttpMethod.GET,
                    Collections.emptyMap(),
                    timeout
            );
        } catch (IOException e) {
            logger.debug("Error while reading URL: " + uri, e);
            return null;
        }
    }
}