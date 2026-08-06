/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import net.minecraft.entity.player.EntityPlayerMP;

public final class PlayerLanguage {
    private PlayerLanguage() {}

    /**
     * Returns the player's client locale (e.g., "en_us") via the AT-exposed
     * EntityPlayerMP.language field (MCP) / field_71148_cg (SRG).
     *
     * Returns null if the player is null or the field cannot be read
     * (e.g., pre-AT-applied environment — fail-safe fallback to Config.LANGUAGE).
     */
    public static String get(EntityPlayerMP player) {
        if (player == null) return null;
        return player.language;
    }
}
