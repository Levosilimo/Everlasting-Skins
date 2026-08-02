/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration.placeholderapi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import java.util.UUID;

public class EverlastingSkinsExpansion extends PlaceholderExpansion {
    @Override public @NotNull String getAuthor() { return "Levosilimo"; }
    @Override public @NotNull String getIdentifier() { return "everlastingskins"; }
    @Override public @NotNull String getVersion() { return "2.1.0"; }
    @Override public boolean persist() { return true; }
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || !player.hasPlayedBefore()) return null;
        UUID uuid = player.getUniqueId();
        if (params.equalsIgnoreCase("skin_source")) {
            String source = SkinRestorer.getSkinStorage().getSource(uuid);
            return source != null ? source : "default";
        }
        if (params.equalsIgnoreCase("has_custom_skin")) {
            return String.valueOf(!SkinRestorer.getSkinStorage().hasDefaultSkin(uuid));
        }
        return null;
    }
}
