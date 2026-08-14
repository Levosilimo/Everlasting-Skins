/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * FIX-3c regression: DiscordSRV and PlaceholderAPI ship no Forge build for
 * MC 26.1/26.2 (RES-3), so the 26.x config surface must not advertise inert
 * integration keys. Guards against re-adding discordsrv_* /
 * messages_discord_* entries to the spec.
 */
class ConfigSurfaceTest {
    @Test
    void configSpecHasNoInertDiscordIntegrationKeys() {
        for (String key : Config.COMMON_CONFIG.getValues().valueMap().keySet()) {
            assertFalse(key.toLowerCase().contains("discord"),
                "inert DiscordSRV config key present: " + key);
        }
    }
}
