/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlAllowlistTest {

    private static final List<String> DOMAINS = Arrays.asList(
            "imgur.com", "storage.googleapis.com", "cdn.discordapp.com");

    @Test
    void disabledAllowsEverything() {
        assertTrue(UrlAllowlist.isAllowed("https://evil.example.org/x.png", false, DOMAINS));
        assertTrue(UrlAllowlist.isAllowed("https://i.imgur.com/x.png", false, DOMAINS));
        assertTrue(UrlAllowlist.isAllowed(null, false, DOMAINS));
        assertTrue(UrlAllowlist.isAllowed("https://imgur.com/x.png", false, Collections.emptyList()));
    }

    @Test
    void enabledWithEmptyListDeniesAll() {
        assertFalse(UrlAllowlist.isAllowed("https://i.imgur.com/x.png", true, Collections.emptyList()));
        assertFalse(UrlAllowlist.isAllowed("https://imgur.com/x.png", true, Collections.emptyList()));
        assertFalse(UrlAllowlist.isAllowed(null, true, Collections.emptyList()));
    }

    @Test
    void allowsListedSubdomain() {
        assertTrue(UrlAllowlist.isAllowed("https://i.imgur.com/skin.png", true, DOMAINS));
        assertTrue(UrlAllowlist.isAllowed("https://a.b.c.imgur.com/skin.png", true, DOMAINS));
    }

    @Test
    void allowsParentDomainItself() {
        assertTrue(UrlAllowlist.isAllowed("https://imgur.com/skin.png", true, DOMAINS));
        assertTrue(UrlAllowlist.isAllowed("http://imgur.com", true, DOMAINS));
    }

    @Test
    void allowsSchemePortAndCaseVariants() {
        assertTrue(UrlAllowlist.isAllowed("http://imgur.com/skin.png", true, DOMAINS));
        assertTrue(UrlAllowlist.isAllowed("https://imgur.com:8443/skin.png", true, DOMAINS));
        assertTrue(UrlAllowlist.isAllowed("https://I.IMGUR.COM/skin.png", true, DOMAINS));
    }

    @Test
    void deniesSuffixLookalikeDomain() {
        assertFalse(UrlAllowlist.isAllowed("https://evil-imgur.com/skin.png", true, DOMAINS));
        assertFalse(UrlAllowlist.isAllowed("https://imgur.com.evil.net/skin.png", true, DOMAINS));
    }

    @Test
    void deniesUnlistedDomain() {
        assertFalse(UrlAllowlist.isAllowed("https://randomcdn.net/skin.png", true, DOMAINS));
        assertFalse(UrlAllowlist.isAllowed("https://cdn.discordapp.net/skin.png", true, DOMAINS));
    }

    @Test
    void deniesNullOrMalformedUrl() {
        assertFalse(UrlAllowlist.isAllowed(null, true, DOMAINS));
        assertFalse(UrlAllowlist.isAllowed("", true, DOMAINS));
        assertFalse(UrlAllowlist.isAllowed("not a url", true, DOMAINS));
        assertFalse(UrlAllowlist.isAllowed("https:///no-host", true, DOMAINS));
    }
}
