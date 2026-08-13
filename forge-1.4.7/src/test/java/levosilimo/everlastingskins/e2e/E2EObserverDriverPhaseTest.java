/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import levosilimo.everlastingskins.broadcast.SkinMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure phase-machine tests for the in-jar OBSERVER driver (lib-23 gap (d)):
 * the observer advances only on observed facts — join (NO commands), a
 * PNG-carrying broadcast for the target player, then the REAL handler's
 * wire-injection verify. Mirrors {@link E2EDriverPhaseTest}.
 */
public class E2EObserverDriverPhaseTest {

    @Test
    public void joinGatesOnWorldAndPlayer() {
        assertEquals(E2EObserverDriver.Phase.WAIT_JOIN,
            E2EObserverDriver.nextPhase(E2EObserverDriver.Phase.WAIT_JOIN, false, false, false));
        assertEquals(E2EObserverDriver.Phase.WAIT_BROADCAST,
            E2EObserverDriver.nextPhase(E2EObserverDriver.Phase.WAIT_JOIN, true, false, false));
    }

    @Test
    public void pngBroadcastGatesTheObserverAssertion() {
        assertEquals(E2EObserverDriver.Phase.WAIT_BROADCAST,
            E2EObserverDriver.nextPhase(E2EObserverDriver.Phase.WAIT_BROADCAST, true, false, false));
        assertEquals(E2EObserverDriver.Phase.RENDER_ASSERT,
            E2EObserverDriver.nextPhase(E2EObserverDriver.Phase.WAIT_BROADCAST, true, true, false));
    }

    @Test
    public void observerAssertionCompletesTheRun() {
        assertEquals(E2EObserverDriver.Phase.RENDER_ASSERT,
            E2EObserverDriver.nextPhase(E2EObserverDriver.Phase.RENDER_ASSERT, true, true, false));
        assertEquals(E2EObserverDriver.Phase.DONE,
            E2EObserverDriver.nextPhase(E2EObserverDriver.Phase.RENDER_ASSERT, true, true, true));
        assertEquals(E2EObserverDriver.Phase.DONE,
            E2EObserverDriver.nextPhase(E2EObserverDriver.Phase.DONE, true, true, true));
    }

    @Test
    public void phasesNeverRegress() {
        assertEquals(E2EObserverDriver.Phase.WAIT_BROADCAST,
            E2EObserverDriver.nextPhase(E2EObserverDriver.Phase.WAIT_BROADCAST, false, false, false));
        assertEquals(E2EObserverDriver.Phase.RENDER_ASSERT,
            E2EObserverDriver.nextPhase(E2EObserverDriver.Phase.RENDER_ASSERT, true, false, false));
    }

    @Test
    public void acceptsRequiresTargetPlayerWithInlinePng() {
        byte[] png = {1, 2, 3};
        assertTrue(E2EObserverDriver.accepts(new SkinMessage("TestPlayer", png)));
        // Notification-only broadcast (no pixels to inject) is not the proof.
        assertFalse(E2EObserverDriver.accepts(new SkinMessage("TestPlayer", null)));
        assertFalse(E2EObserverDriver.accepts(new SkinMessage("TestPlayer", new byte[0])));
        // Any other player's broadcast is irrelevant to the observer.
        assertFalse(E2EObserverDriver.accepts(new SkinMessage("SomeoneElse", png)));
        assertFalse(E2EObserverDriver.accepts(null));
    }
}
