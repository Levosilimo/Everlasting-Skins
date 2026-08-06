/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.integration.placeholderapi;

import levosilimo.everlastingskins.forge21.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.skinchanger.SkinStorage;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link EverlastingSkinsExpansion} placeholder expansion.
 * Covers lib-39 scenarios PAPI-1 through PAPI-5:
 * <ul>
 *   <li>PAPI-1: skin_source expansion with a stored custom skin</li>
 *   <li>PAPI-2: skin_source fallback to "default" when no skin exists</li>
 *   <li>PAPI-3: has_custom_skin returns "true" when a custom skin is set</li>
 *   <li>PAPI-4: has_custom_skin returns "false" when no custom skin exists</li>
 *   <li>PAPI-5: unknown placeholder params return null</li>
 * </ul>
 *
 * <p>Uses Mockito static mocking for {@link SkinRestorer#getSkinStorage()}
 * since {@code SkinRestorer.skinStorage} is only initialised during
 * server lifecycle events. No MockBukkit or server bootstrap needed.</p>
 */
class EverlastingSkinsExpansionTest {

    private static final UUID TEST_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private EverlastingSkinsExpansion expansion;
    private SkinStorage skinStorage;
    private OfflinePlayer player;
    private MockedStatic<SkinRestorer> skinRestorerMock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        expansion = new EverlastingSkinsExpansion();

        // Mock the OfflinePlayer so hasPlayedBefore() and getUniqueId() work
        player = mock(OfflinePlayer.class);
        lenient().when(player.hasPlayedBefore()).thenReturn(true);
        lenient().when(player.getUniqueId()).thenReturn(TEST_UUID);

        // Mock SkinStorage so we can control getSource / hasDefaultSkin
        skinStorage = mock(SkinStorage.class);

        // Mock the static SkinRestorer.getSkinStorage() to return our mock
        skinRestorerMock = mockStatic(SkinRestorer.class);
        skinRestorerMock.when(SkinRestorer::getSkinStorage).thenReturn(skinStorage);
    }

    @AfterEach
    void tearDown() {
        if (skinRestorerMock != null) {
            skinRestorerMock.close();
        }
    }

    /* ------------------------------------------------------------------ */
    /*  skin_source expansion                                              */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("skin_source returns source name when storage has a custom skin (PAPI-1)")
    void skinSource_returnsSourceNameWhenStorageHasCustomSkin() {
        when(skinStorage.getSource(TEST_UUID)).thenReturn("Notch");

        String result = expansion.onRequest(player, "skin_source");

        assertEquals("Notch", result);
    }

    @Test
    @DisplayName("skin_source returns 'default' when no custom skin is set (PAPI-2)")
    void skinSource_defaultsWhenNoSkinSet() {
        when(skinStorage.getSource(TEST_UUID)).thenReturn(null);

        String result = expansion.onRequest(player, "skin_source");

        assertEquals("default", result);
    }

    /* ------------------------------------------------------------------ */
    /*  has_custom_skin expansion                                          */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("has_custom_skin returns 'true' when a custom skin is set (PAPI-3)")
    void hasCustomSkin_returnsTrueWhenSkinSet() {
        when(skinStorage.hasDefaultSkin(TEST_UUID)).thenReturn(false);

        String result = expansion.onRequest(player, "has_custom_skin");

        assertEquals("true", result);
    }

    @Test
    @DisplayName("has_custom_skin returns 'false' when no custom skin exists (PAPI-4)")
    void hasCustomSkin_returnsFalseWhenNoCustomSkin() {
        when(skinStorage.hasDefaultSkin(TEST_UUID)).thenReturn(true);

        String result = expansion.onRequest(player, "has_custom_skin");

        assertEquals("false", result);
    }

    /* ------------------------------------------------------------------ */
    /*  unknown / edge params                                              */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("unknown placeholder params return null (PAPI-5)")
    void unknownParam_returnsNull() {
        String result = expansion.onRequest(player, "unknown_param");

        assertNull(result);
    }

    @Test
    @DisplayName("onRequest returns null when player is null")
    void onRequest_returnsNullForNullPlayer() {
        String result = expansion.onRequest(null, "skin_source");

        assertNull(result);
    }

    @Test
    @DisplayName("onRequest returns null when player has not played before")
    void onRequest_returnsNullForPlayerNeverPlayed() {
        OfflinePlayer neverPlayed = mock(OfflinePlayer.class);
        when(neverPlayed.hasPlayedBefore()).thenReturn(false);

        String result = expansion.onRequest(neverPlayed, "skin_source");

        assertNull(result);
    }
}
