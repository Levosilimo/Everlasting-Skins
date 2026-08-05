/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomSkinPropertyTest {

    @Nested
    @DisplayName("isEmpty")
    class IsEmpty {

        @BeforeEach
        void setUp() {
            CustomSkinProperty.setDefaultSkinValue("defaultValue");
        }

        @AfterEach
        void tearDown() {
            CustomSkinProperty.setDefaultSkinValue(null);
        }

        @Test
        @DisplayName("returns true when value is null")
        void nullValue() {
            CustomSkinProperty skin = new CustomSkinProperty("name", null, "sig", "src");
            assertTrue(skin.isEmpty());
        }

        @Test
        @DisplayName("returns true when value is empty string")
        void emptyValue() {
            CustomSkinProperty skin = new CustomSkinProperty("name", "", "sig", "src");
            assertTrue(skin.isEmpty());
        }

        @Test
        @DisplayName("returns true when value is whitespace only")
        void blankValue() {
            CustomSkinProperty skin = new CustomSkinProperty("name", "   ", "sig", "src");
            assertTrue(skin.isEmpty());
        }

        @Test
        @DisplayName("returns true when value matches default skin value")
        void matchesDefaultValue() {
            CustomSkinProperty skin = new CustomSkinProperty("name", "defaultValue", "sig", "legacy");
            assertTrue(skin.isEmpty());
        }

        @Test
        @DisplayName("returns false for valid non-default value")
        void validValue() {
            CustomSkinProperty skin = new CustomSkinProperty("name", "realValue", "sig", "mojang");
            assertFalse(skin.isEmpty());
        }
    }
}
