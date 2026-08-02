/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.util;

import levosilimo.everlastingskins.EverlastingSkins;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Typed config loaded from {@code /endpoints.properties} on the classpath.
 * <p>
 * All external service URLs (Mojang sessionserver, Eclipse, MineTools,
 * MineSkin, Minecraft textures, NameMC, mskins.net) are defined in that
 * resource file rather than hardcoded as Java string literals.
 */
public class EndpointsConfig {
    private static final Properties props = new Properties();

    static {
        try (InputStream is = EndpointsConfig.class.getClassLoader()
                .getResourceAsStream("endpoints.properties")) {
            if (is != null) {
                props.load(is);
            } else {
                throw new RuntimeException("endpoints.properties not found on classpath");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load endpoints.properties", e);
        }
    }

    public static String getString(String key) {
        // System property override (for E2E tests with WireMock)
        // Example: -Deverlastingskins.endpoint.uuid.eclipse=http://localhost:8080/...
        String sysProp = System.getProperty("everlastingskins." + key);
        if (sysProp != null && !sysProp.isEmpty()) {
            return sysProp;
        }
        String value = props.getProperty(key);
        if (value == null || value.isEmpty()) {
            EverlastingSkins.logger.warn("Missing endpoint property: {}, returning empty fallback", key);
            return "";
        }
        return value;
    }

    public static URI getURI(String key) {
        return URI.create(getString(key));
    }

    public static Pattern getUrlPattern(String key) {
        return Pattern.compile(getString(key));
    }

    private EndpointsConfig() {
    }
}
