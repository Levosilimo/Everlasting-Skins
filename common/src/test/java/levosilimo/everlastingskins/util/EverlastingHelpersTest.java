/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * URL sanitization, username validation, and utility helpers from
 * {@link EverlastingHelpers}.
 */
class EverlastingHelpersTest {
    @Nested
    class SanitizeSkinInput {

        @Test
        @DisplayName("Plain username passes through")
        void plainUsername() {
            assertEquals("Notch", EverlastingHelpers.sanitizeSkinInput("Notch"));
        }

        @Test
        @DisplayName("NameMC profile URL extracts username")
        void namemcUrl() {
            String result = EverlastingHelpers.sanitizeSkinInput("https://namemc.com/profile/Notch.1");
            assertEquals("Notch", result);
        }

        @Test
        @DisplayName("NameMC subdomain URL extracts username")
        void namemcSubdomainUrl() {
            String result = EverlastingHelpers.sanitizeSkinInput("https://skins.namemc.com/profile/TestPlayer.2");
            assertEquals("TestPlayer", result);
        }

        @Test
        @DisplayName("Non-NameMC URL passes through")
        void nonNamemcUrl() {
            String url = "https://example.com/skin.png";
            assertEquals(url, EverlastingHelpers.sanitizeSkinInput(url));
        }

        @Test
        @DisplayName("Invalid URL passes through as-is")
        void invalidUrl() {
            assertEquals("not a url at all", EverlastingHelpers.sanitizeSkinInput("not a url at all"));
        }
    }
    @Nested
    class SanitizeImageUrl {

        @Test
        @DisplayName("NameMC skin URL rewrites to img URL")
        void namemcSkinUrl() {
            String result = EverlastingHelpers.sanitizeImageURL("https://namemc.com/skin/550e8400-e29b-41d4-a716-446655440000");
            assertTrue(result.contains("s.namemc.com"));
        }

        @Test
        @DisplayName("Regular image URL passes through")
        void regularUrl() {
            String url = "https://example.com/skin.png";
            assertEquals(url, EverlastingHelpers.sanitizeImageURL(url));
        }
    }
    @Nested
    class InvalidUsername {

        @ParameterizedTest
        @ValueSource(strings = {"Notch", "Steve", "a", "abc123", "Test_Player", " hyphen-name", ""})
        @DisplayName("Valid usernames -> false")
        void validUsernames(String name) {
            assertFalse(EverlastingHelpers.invalidMinecraftUsername(name));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "thisnameiswaytoolong16",
                "name with spaces",
                "special!chars",
                "$$$invalid"
        })
        @DisplayName("Invalid usernames -> true")
        void invalidUsernames(String name) {
            assertTrue(EverlastingHelpers.invalidMinecraftUsername(name));
        }
    }
    @Nested
    class ValidSkinUrl {

        @ParameterizedTest
        @ValueSource(strings = {
                "https://example.com/skin.png",
                "http://example.com/skin.png",
                "https://namemc.com/skin/abc"
        })
        @DisplayName("Valid URLs -> true")
        void validUrls(String url) {
            assertTrue(EverlastingHelpers.validSkinUrl(url));
        }

        @ParameterizedTest
        @ValueSource(strings = {"not-a-url", "ftp://bad.com", "", "   "})
        @DisplayName("Invalid URLs -> false")
        void invalidUrls(String url) {
            assertFalse(EverlastingHelpers.validSkinUrl(url));
        }
    }
    @Nested
    class ParseURL {

        @Test
        @DisplayName("Valid URL returns Optional with URL")
        void validUrl() {
            Optional<URL> result = EverlastingHelpers.parseURL("https://example.com");
            assertTrue(result.isPresent());
            assertEquals("example.com", result.get().getHost());
        }

        @Test
        @DisplayName("Invalid string returns Optional.empty()")
        void invalidUrl() {
            assertFalse(EverlastingHelpers.parseURL("").isPresent());
            assertFalse(EverlastingHelpers.parseURL(null).isPresent());
        }
    }
    @Nested
    class OtherUtilities {

        @Test
        @DisplayName("getEpochSecond returns positive value")
        void epochSecond() {
            assertTrue(EverlastingHelpers.getEpochSecond() > 1_500_000_000L);
        }

        @Test
        @DisplayName("lowerCaseCapitalize works")
        void lowerCaseCapitalize() {
            assertEquals("Notch", EverlastingHelpers.lowerCaseCapitalize("NOTCH"));
            assertEquals("Test", EverlastingHelpers.lowerCaseCapitalize("test"));
            assertNull(EverlastingHelpers.lowerCaseCapitalize(null));
        }

        @Test
        @DisplayName("capitalize works")
        void capitalize() {
            assertEquals("Notch", EverlastingHelpers.capitalize("notch"));
            assertNull(EverlastingHelpers.capitalize(null));
        }

        @Test
        @DisplayName("getJavaVersion returns 8+")
        void javaVersion() {
            assertTrue(EverlastingHelpers.getJavaVersion() >= 8);
        }
    }
}
