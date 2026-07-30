package levosilimo.everlastingskins.skinchanger;

import java.util.Objects;
import java.util.UUID;

/**
 * Resolved username + UUID pair for profile lookups.
 * Replaces {@code Tuple<String, Optional<UUID>>} which leaked the Minecraft
 * type and forced unsafe {@code Optional.get()} calls.
 * <p>
 * Java 8-compatible value class (record is Java 14+).
 */
public final class ProfileLookup {
    private final String username;
    private final UUID uuid;

    public ProfileLookup(String username, UUID uuid) {
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.uuid = Objects.requireNonNull(uuid, "uuid must not be null");
    }

    public String getUsername() { return username; }
    public UUID getUuid() { return uuid; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProfileLookup)) return false;
        ProfileLookup that = (ProfileLookup) o;
        return username.equals(that.username) && uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, uuid);
    }

    @Override
    public String toString() {
        return "ProfileLookup{username='" + username + "', uuid=" + uuid + "}";
    }
}
