/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge26_1.e2e;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure phase-machine tests for the in-jar E2E driver (26.x port): the
 * driver advances only on observed facts, and every wait path is covered by
 * the contract exit codes in the driver itself.
 */
class E2EDriverPhaseTest {

    @Test
    void joinGatesOnWorldPlayerAndConnection() {
        assertEquals(E2EDriver.Phase.WAIT_JOIN,
            E2EDriver.nextPhase(E2EDriver.Phase.WAIT_JOIN, false, false));
        assertEquals(E2EDriver.Phase.WAIT_JOIN,
            E2EDriver.nextPhase(E2EDriver.Phase.WAIT_JOIN, false, true));
        assertEquals(E2EDriver.Phase.WAIT_TEXTURES,
            E2EDriver.nextPhase(E2EDriver.Phase.WAIT_JOIN, true, false));
    }

    @Test
    void texturesObservationCompletesTheRun() {
        assertEquals(E2EDriver.Phase.WAIT_TEXTURES,
            E2EDriver.nextPhase(E2EDriver.Phase.WAIT_TEXTURES, true, false));
        assertEquals(E2EDriver.Phase.DONE,
            E2EDriver.nextPhase(E2EDriver.Phase.WAIT_TEXTURES, true, true));
    }

    @Test
    void phasesNeverRegress() {
        assertEquals(E2EDriver.Phase.WAIT_TEXTURES,
            E2EDriver.nextPhase(E2EDriver.Phase.WAIT_TEXTURES, false, false));
        assertEquals(E2EDriver.Phase.DONE,
            E2EDriver.nextPhase(E2EDriver.Phase.DONE, false, false));
    }

    @Test
    void offlineUuidMatchesVanillaAlgorithm() {
        // UUID.nameUUIDFromBytes("OfflinePlayer:TestPlayer") — the exact
        // UUID the e2e wrapper computes in python (--uuid arg) and the
        // server derives at login; seed + session + client assert must all
        // key on it.
        assertEquals("bb77495a-a740-3169-a238-69654c8bd2c1",
            E2E.offlineUuid("TestPlayer").toString());
        // Determinism: same input, same UUID; different name, different UUID.
        assertEquals(E2E.offlineUuid("TestPlayer"), E2E.offlineUuid("TestPlayer"));
        org.junit.jupiter.api.Assertions.assertNotEquals(
            E2E.offlineUuid("TestPlayer"), E2E.offlineUuid("ObserverPlayer"));
    }
}
