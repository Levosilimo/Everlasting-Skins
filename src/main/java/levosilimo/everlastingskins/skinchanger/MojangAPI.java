package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;

import java.util.Optional;
import java.util.UUID;

/**
 * Interface for Mojang profile and UUID lookups.
 * <p>
 * Implementations provide a fallback chain: Eclipse → Mojang → MineTools.
 * Consumers depend on this interface rather than the concrete HTTP implementation
 * so they can be tested with a deterministic fake.
 */
public interface MojangAPI {

    /**
     * Resolve a player name or UUID string to skin data.
     *
     * @param nameOrUniqueId Mojang username or UUID string
     * @return skin data if resolved, or empty if not found or on error
     */
    Optional<MojangSkinDataResult> getSkin(String nameOrUniqueId);

    /**
     * Resolve a Mojang username to a UUID.
     *
     * @param playerName Mojang username
     * @return the player's UUID, or empty if not found
     */
    Optional<UUID> getUUID(String playerName);

    /**
     * Fetch profile properties (skin texture) for a player identified by a
     * username/UUID pair.
     *
     * @param lookup resolved username and UUID (never partial)
     * @return the skin property if found, or empty on error
     */
    Optional<CustomSkinProperty> getProfile(ProfileLookup lookup);
}
