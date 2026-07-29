package levosilimo.everlastingskins.skinchanger;

/**
 * Centralized endpoint URLs for Mojang profile and UUID lookups.
 * <p>
 * TODO (Phase 1): Extract into {@code HttpClient} configuration once that interface
 * is created. These are currently consumed directly by {@link MojangApiHttpImpl}.
 */
public record MojangEndpoints(
        String uuidEclipse,
        String uuidMojang,
        String uuidMineTools,
        String profileEclipse,
        String profileMojang,
        String profileMineTools
) {
    public static final MojangEndpoints DEFAULT = new MojangEndpoints(
            "https://eclipse.skinsrestorer.net/mojang/uuid/%playerName%",
            "https://api.mojang.com/users/profiles/minecraft/%playerName%",
            "https://api.minetools.eu/uuid/%playerName%",
            "https://eclipse.skinsrestorer.net/mojang/skin/%uuid%",
            "https://sessionserver.mojang.com/session/minecraft/profile/%uuid%?unsigned=false",
            "https://api.minetools.eu/profile/%uuid%"
    );
}
