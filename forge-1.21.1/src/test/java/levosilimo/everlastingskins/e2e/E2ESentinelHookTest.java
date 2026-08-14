/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import levosilimo.everlastingskins.skinchanger.command.SkinActionCommand;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure sentinel-value tests: the seeded textures property must be a
 * well-formed vanilla textures JSON carrying the shared marker, so the
 * client-side assert and the server-side seed agree without any server.
 */
class E2ESentinelHookTest {

    @Test
    void propertyValueIsBase64AndCarriesTheMarker() {
        String value = E2ESentinelHook.buildPropertyValue(E2E.offlineUuid("TestPlayer"));
        String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        assertTrue(json.contains(E2E.MARKER));
        assertTrue(json.contains("\"textures\":{\"SKIN\":{\"url\":\""
            + E2ESentinelHook.SKIN_URL + "\"}}"));
        assertTrue(json.contains("\"profileName\":\"" + E2EDriver.TEST_PLAYER + "\""));
    }

    @Test
    void propertyValueKeysOnTheOfflineProfileId() {
        String value = E2ESentinelHook.buildPropertyValue(E2E.offlineUuid("TestPlayer"));
        String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"profileId\":\"bb77495aa7403169a23869654c8bd2c1\""));
    }

    @Test
    void storedSourceMatchesTheMojangCommandSurface() {
        // The seed must make SkinActionCommand.storedSourceMatches true for
        // "/skin set mojang TestPlayer" (source MojangAPI + username
        // TestPlayer) so the command skips the Mojang HTTP fetch and
        // re-applies the stored sentinel — the deterministic offline path.
        assertEquals("MojangAPI", SkinActionCommand.SOURCE_MOJANG);
        assertEquals(E2EDriver.TEST_PLAYER, E2ESentinelHook.TEST_PLAYER);
    }
}
