/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class PlayerLanguageTest {

    @Nested
    @DisplayName("get(EntityPlayerMP)")
    class Get {

        @Test
        @DisplayName("null player returns null")
        void get_nullPlayer_returnsNull() {
            assertNull(PlayerLanguage.get(null));
        }

        @Test
        @DisplayName("returns the AT-exposed language field")
        void get_validPlayer_returnsLanguageField() {
            EntityPlayerMP player = mock(EntityPlayerMP.class);
            player.language = "de_de";
            assertEquals("de_de", PlayerLanguage.get(player));
        }

        @Test
        @DisplayName("empty language returns empty string (caller falls back to Config.LANGUAGE)")
        void get_emptyLanguage_returnsEmptyString() {
            EntityPlayerMP player = mock(EntityPlayerMP.class);
            player.language = "";
            assertEquals("", PlayerLanguage.get(player));
        }
    }
}
