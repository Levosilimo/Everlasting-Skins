package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.skinchanger.MojangAPI;
import levosilimo.everlastingskins.skinchanger.ProfileLookup;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic Mojang API stub. {@code getSkin} resolves only names registered
 * via {@link #addSkin} and counts lookups so async tests can await completion.
 */
public class FakeMojangAPI implements MojangAPI {

    private final Map<String, CustomSkinProperty> skins = new HashMap<String, CustomSkinProperty>();
    private final Map<String, Integer> lookups = new HashMap<String, Integer>();

    public FakeMojangAPI(CustomSkinProperty... fixtures) {
        for (CustomSkinProperty skin : fixtures) {
            if (skin.getSource() != null) {
                addSkin(skin.getSource(), skin);
            }
        }
    }

    public FakeMojangAPI addSkin(String name, CustomSkinProperty skin) {
        skins.put(name.toLowerCase(Locale.ROOT), skin);
        return this;
    }

    public int lookupCount(String name) {
        Integer count = lookups.get(name.toLowerCase(Locale.ROOT));
        return count != null ? count : 0;
    }

    @Override
    public Optional<MojangSkinDataResult> getSkin(String nameOrUniqueId) {
        String key = nameOrUniqueId.toLowerCase(Locale.ROOT);
        lookups.merge(key, 1, Integer::sum);
        CustomSkinProperty skin = skins.get(key);
        if (skin == null) {
            return Optional.empty();
        }
        UUID uuid = UUID.nameUUIDFromBytes(nameOrUniqueId.getBytes(StandardCharsets.UTF_8));
        return Optional.of(new MojangSkinDataResult(uuid, skin));
    }

    @Override
    public Optional<UUID> getUUID(String playerName) {
        return Optional.empty();
    }

    @Override
    public Optional<CustomSkinProperty> getProfile(ProfileLookup lookup) {
        return Optional.empty();
    }
}
