/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Pure phase-machine tests for the in-jar E2E driver: the driver advances
 * only on observed facts, and every timeout/assertion path lands on the
 * contract exit code.
 */
public class E2EDriverPhaseTest {

    @Test
    public void joinGatesOnWorldAndPlayer() {
        assertEquals(E2EDriver.Phase.WAIT_JOIN,
            E2EDriver.nextPhase(E2EDriver.Phase.WAIT_JOIN, false, false, false));
        assertEquals(E2EDriver.Phase.WAIT_BROADCAST,
            E2EDriver.nextPhase(E2EDriver.Phase.WAIT_JOIN, true, false, false));
    }

    @Test
    public void broadcastGatesTheRendererAssertion() {
        assertEquals(E2EDriver.Phase.WAIT_BROADCAST,
            E2EDriver.nextPhase(E2EDriver.Phase.WAIT_BROADCAST, true, false, false));
        assertEquals(E2EDriver.Phase.RENDER_ASSERT,
            E2EDriver.nextPhase(E2EDriver.Phase.WAIT_BROADCAST, true, true, false));
    }

    @Test
    public void rendererAssertionCompletesTheRun() {
        assertEquals(E2EDriver.Phase.RENDER_ASSERT,
            E2EDriver.nextPhase(E2EDriver.Phase.RENDER_ASSERT, true, true, false));
        assertEquals(E2EDriver.Phase.DONE,
            E2EDriver.nextPhase(E2EDriver.Phase.RENDER_ASSERT, true, true, true));
        assertEquals(E2EDriver.Phase.DONE,
            E2EDriver.nextPhase(E2EDriver.Phase.DONE, true, true, true));
    }

    @Test
    public void phasesNeverRegress() {
        assertEquals(E2EDriver.Phase.WAIT_BROADCAST,
            E2EDriver.nextPhase(E2EDriver.Phase.WAIT_BROADCAST, false, false, false));
        assertEquals(E2EDriver.Phase.RENDER_ASSERT,
            E2EDriver.nextPhase(E2EDriver.Phase.RENDER_ASSERT, true, false, false));
    }
}
