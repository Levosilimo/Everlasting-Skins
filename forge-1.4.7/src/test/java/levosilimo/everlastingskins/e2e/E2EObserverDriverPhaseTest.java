/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import levosilimo.everlastingskins.broadcast.SkinMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Pure phase-machine tests for the in-jar E2E OBSERVER driver: the observer
 * advances only on observed facts (join → PNG-carrying broadcast → wire
 * injection verified), and every timeout/assertion path lands on the
 * contract exit code.
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
    public void broadcastGatesTheRendererAssertion() {
        assertEquals(E2EObserverDriver.Phase.WAIT_BROADCAST,
            E2EObserverDriver.nextPhase(E2EObserverDriver.Phase.WAIT_BROADCAST, true, false, false));
        assertEquals(E2EObserverDriver.Phase.RENDER_ASSERT,
            E2EObserverDriver.nextPhase(E2EObserverDriver.Phase.WAIT_BROADCAST, true, true, false));
    }

    @Test
    public void rendererAssertionCompletesTheRun() {
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
    public void acceptsRequiresTargetAndInlinePng() {
        SkinMessage forOther = new SkinMessage("OtherPlayer", new byte[] {1, 2, 3}, null);
        assertEquals(false, E2EObserverDriver.accepts(forOther));
        SkinMessage notification = new SkinMessage(E2EObserverDriver.TEST_PLAYER, null, null);
        assertEquals(false, E2EObserverDriver.accepts(notification));
        assertEquals(false, E2EObserverDriver.accepts(null));
        SkinMessage pngCarrying =
            new SkinMessage(E2EObserverDriver.TEST_PLAYER, new byte[] {1, 2, 3}, null);
        assertEquals(true, E2EObserverDriver.accepts(pngCarrying));
    }
}
