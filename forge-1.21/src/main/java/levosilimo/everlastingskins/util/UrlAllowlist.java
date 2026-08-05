/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * eTLD+1 domain matching for /skin set web URLs (FabricTailor-style).
 * A single entry covers the domain itself and all subdomains.
 */
public final class UrlAllowlist {
    private UrlAllowlist() {}

    public static boolean isAllowed(String url, boolean enabled, List<? extends String> domains) {
        if (!enabled) {
            return true;
        }
        if (url == null) {
            return false;
        }
        if (domains == null || domains.isEmpty()) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            for (String domain : domains) {
                String normalized = domain.toLowerCase(Locale.ROOT);
                if (host.equals(normalized) || host.endsWith("." + normalized)) {
                    return true;
                }
            }
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
