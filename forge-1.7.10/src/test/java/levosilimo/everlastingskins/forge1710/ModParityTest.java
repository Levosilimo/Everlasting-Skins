/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge1710;

import cpw.mods.fml.common.Mod;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * FML 7.x handshake-parity regression (FIX-6).
 *
 * <p>FML 7 enforces mod-list parity at join: a server mod absent from the
 * client's mod list is rejected via {@code NetworkModHolder}'s
 * DefaultNetworkChecker unless the mod opts out. The opt-out is the
 * {@code @Mod} attribute {@code acceptableRemoteVersions = "*"} — the
 * holder constructor special-cases exactly {@code "*"} to IgnoredChecker
 * (always accepts, including vanilla clients). This test pins the
 * annotation attribute so a future edit cannot silently re-introduce the
 * vanilla-client rejection.
 */
public class ModParityTest {

    @Test
    public void modAnnotationPresent() {
        assertNotNull(EverlastingSkins.class.getAnnotation(Mod.class));
    }

    @Test
    public void acceptableRemoteVersionsOptsOutOfClientParity() {
        Mod mod = EverlastingSkins.class.getAnnotation(Mod.class);
        assertEquals("client-join parity opt-out", "*", mod.acceptableRemoteVersions());
    }

    @Test
    public void modIdMatchesRegistryKey() {
        Mod mod = EverlastingSkins.class.getAnnotation(Mod.class);
        assertEquals(EverlastingSkins.MOD_ID, mod.modid());
    }
}
