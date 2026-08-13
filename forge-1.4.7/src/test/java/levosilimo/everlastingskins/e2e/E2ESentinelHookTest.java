/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * E2ESentinelHook re-broadcast burst unit tests (the observer fan-out
 * vector): the thread factory seam is daemon/named/burst-counted, and
 * scheduling is a no-op before the seed lands. The offline-UUID seed key
 * convention is pinned so the seed always lands on the actor's storage key.
 */
public class E2ESentinelHookTest {

    @Test
    public void rebroadcastThreadIsDaemonNamedAndBursts() throws Exception {
        Thread t = E2ESentinelHook.rebroadcastThread(60_000L, 40);
        assertTrue(t.isDaemon());
        assertEquals("ES-E2E-rebroadcast", t.getName());
        // A short-period burst must run to completion (count shots) and
        // finish; interruption aborts the burst early.
        Thread t2 = E2ESentinelHook.rebroadcastThread(10L, 3);
        t2.start();
        t2.join(5_000L);
        assertFalse(t2.isAlive());
        // The broadcast action itself is exercised on the wire by the live
        // E2E (the observer's fan-out assertion is the end-to-end proof).
    }

    @Test
    public void scheduleRebroadcastIsNoopBeforeSeed() {
        // No cached seed yet — scheduling must not throw or spawn threads.
        E2ESentinelHook.scheduleRebroadcast();
    }

    @Test
    public void offlineUuidMatchesVanillaV3Convention() {
        // The seed key the hook stores under (SkinRestorer.uuidOf bridge);
        // UUID.nameUUIDFromBytes("OfflinePlayer:TestPlayer") — the vanilla
        // offline-mode v3 convention, verified against Java 8.
        UUID expected = UUID.nameUUIDFromBytes(
            "OfflinePlayer:TestPlayer".getBytes(StandardCharsets.UTF_8));
        assertEquals("bb77495a-a740-3169-a238-69654c8bd2c1", expected.toString());
        assertEquals(expected, SkinRestorer.uuidOf(E2ESentinelHook.TEST_PLAYER));
    }
}
