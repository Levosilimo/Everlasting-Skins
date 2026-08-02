/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UUIDUtilsTest {

    private static final UUID DASHED = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final String NO_DASH = "12345678123412341234123456789abc";

    @Test
    @DisplayName("tryParseUniqueId with dashed UUID string")
    void parseDashed() {
        Optional<UUID> result = UUIDUtils.tryParseUniqueId(DASHED.toString());
        assertTrue(result.isPresent());
        assertEquals(DASHED, result.get());
    }

    @Test
    @DisplayName("tryParseUniqueId with non-dashed 32-char string")
    void parseNoDash() {
        Optional<UUID> result = UUIDUtils.tryParseUniqueId(NO_DASH);
        assertTrue(result.isPresent());
        assertEquals(DASHED, result.get());
    }

    @Test
    @DisplayName("tryParseUniqueId with invalid string")
    void parseInvalid() {
        assertFalse(UUIDUtils.tryParseUniqueId("not-a-uuid").isPresent());
        assertFalse(UUIDUtils.tryParseUniqueId("").isPresent());
        assertFalse(UUIDUtils.tryParseUniqueId("short").isPresent());
    }

    @Test
    @DisplayName("convertToDashed inserts hyphens")
    void toDashed() {
        UUID result = UUIDUtils.convertToDashed(NO_DASH);
        assertEquals(DASHED, result);
    }

    @Test
    @DisplayName("convertToNoDashes removes hyphens")
    void toNoDashes() {
        String result = UUIDUtils.convertToNoDashes(DASHED);
        assertEquals(NO_DASH, result);
    }
}
