/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure phase-machine coverage for {@link E2EDriver} (no client classes
 * needed — the transition logic is FML-free by design).
 */
class E2EDriverPhaseTest {

    @Test
    void staysInJoinUntilJoined() {
        assertEquals(E2EDriver.Phase.WAIT_JOIN,
            E2EDriver.nextPhase(E2EDriver.Phase.WAIT_JOIN, false, false));
    }

    @Test
    void advancesToAckOnJoin() {
        assertEquals(E2EDriver.Phase.WAIT_ACK,
            E2EDriver.nextPhase(E2EDriver.Phase.WAIT_JOIN, true, false));
    }

    @Test
    void ackWaitsForElapsedBudget() {
        assertEquals(E2EDriver.Phase.WAIT_ACK,
            E2EDriver.nextPhase(E2EDriver.Phase.WAIT_ACK, true, false));
        assertEquals(E2EDriver.Phase.DONE,
            E2EDriver.nextPhase(E2EDriver.Phase.WAIT_ACK, true, true));
    }

    @Test
    void doneIsTerminal() {
        assertEquals(E2EDriver.Phase.DONE,
            E2EDriver.nextPhase(E2EDriver.Phase.DONE, false, false));
    }
}
