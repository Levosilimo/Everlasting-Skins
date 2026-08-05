/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.util.EndpointsConfig;

import java.util.Objects;

/**
 * Centralized endpoint URLs for Mojang profile and UUID lookups.
 * <p>
 * Values are loaded from {@code /endpoints.properties} on the classpath
 * via {@link EndpointsConfig} rather than hardcoded as Java string literals.
 */
public final class MojangEndpoints {
    private final String uuidMojang;
    private final String uuidMineTools;
    private final String profileMojang;
    private final String profileMineTools;

    public MojangEndpoints(
            String uuidMojang,
            String uuidMineTools,
            String profileMojang,
            String profileMineTools
    ) {
        this.uuidMojang = uuidMojang;
        this.uuidMineTools = uuidMineTools;
        this.profileMojang = profileMojang;
        this.profileMineTools = profileMineTools;
    }

    public static final MojangEndpoints DEFAULT = new MojangEndpoints(
            EndpointsConfig.getString("endpoint.uuid.mojang"),
            EndpointsConfig.getString("endpoint.uuid.minetools"),
            EndpointsConfig.getString("endpoint.profile.mojang"),
            EndpointsConfig.getString("endpoint.profile.minetools")
    );

    public String uuidMojang() {
        return uuidMojang;
    }

    public String uuidMineTools() {
        return uuidMineTools;
    }

    public String profileMojang() {
        return profileMojang;
    }

    public String profileMineTools() {
        return profileMineTools;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MojangEndpoints that = (MojangEndpoints) o;
        return Objects.equals(uuidMojang, that.uuidMojang) && Objects.equals(uuidMineTools, that.uuidMineTools)
            && Objects.equals(profileMojang, that.profileMojang) && Objects.equals(profileMineTools, that.profileMineTools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuidMojang, uuidMineTools, profileMojang, profileMineTools);
    }

    @Override
    public String toString() {
        return "MojangEndpoints[uuidMojang=" + uuidMojang + ", uuidMineTools=" + uuidMineTools
            + ", profileMojang=" + profileMojang + ", profileMineTools=" + profileMineTools + "]";
    }
}
