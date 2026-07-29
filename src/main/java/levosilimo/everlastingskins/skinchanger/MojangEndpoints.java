package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.util.EndpointsConfig;

/**
 * Centralized endpoint URLs for Mojang profile and UUID lookups.
 * <p>
 * Values are loaded from {@code /endpoints.properties} on the classpath
 * via {@link EndpointsConfig} rather than hardcoded as Java string literals.
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
            EndpointsConfig.getString("endpoint.uuid.eclipse"),
            EndpointsConfig.getString("endpoint.uuid.mojang"),
            EndpointsConfig.getString("endpoint.uuid.minetools"),
            EndpointsConfig.getString("endpoint.profile.eclipse"),
            EndpointsConfig.getString("endpoint.profile.mojang"),
            EndpointsConfig.getString("endpoint.profile.minetools")
    );
}
