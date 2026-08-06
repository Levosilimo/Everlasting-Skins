/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration.placeholderapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaceholderApiHookTest {
    @BeforeEach
    void resetState() {
        PlaceholderApiHook.resetForTest();
    }

    @Test
    @DisplayName("tryRegister when PAPI is absent does not throw")
    void tryRegister_whenNotPresent_doesNotThrow() {
        PlaceholderApiHook.tryRegister();
        assertFalse(PlaceholderApiHook.isRegistered());
    }

    @Test
    @DisplayName("isRegistered returns false initially")
    void isRegistered_returnsFalseInitially() {
        assertFalse(PlaceholderApiHook.isRegistered());
    }

    @Test
    @DisplayName("expansion onRequest returns null for unknown params")
    void onRequest_unknownParams_returnsNull() {
        EverlastingSkinsExpansion expansion = new EverlastingSkinsExpansion();
        assertNull(expansion.onRequest(null, "unknown"));
    }

    @Test
    @DisplayName("expansion identifier and metadata are correct")
    void expansion_metadata() {
        EverlastingSkinsExpansion expansion = new EverlastingSkinsExpansion();
        assertNotNull(expansion.getIdentifier());
        assertNotNull(expansion.getAuthor());
        assertNotNull(expansion.getVersion());
    }
}
