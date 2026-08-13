/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Registration semantics of {@link PermissionServiceManager}: the
 * highest-priority registered backend becomes active, unregistered or null
 * backends fail closed, duplicate registration is a no-op, and {@code reset()}
 * clears the active backend.
 */
class PermissionServiceManagerTest {

    private static final UUID PLAYER = UUID.randomUUID();

    @BeforeEach
    void resetManager() {
        PermissionServiceManager.reset();
    }

    @Test
    @DisplayName("highest-priority backend wins and serves permission checks")
    void highestPriorityBackendWins() {
        PermissionServiceManager.registerService(stub("low", 1, false));
        PermissionServiceManager.registerService(stub("high", 10, true));
        PermissionServiceManager.registerService(stub("mid", 5, false));

        assertEquals("high", PermissionServiceManager.getActiveBackendName(),
                "the highest-priority registration must become active");
        assertTrue(PermissionServiceManager.hasPermission(PLAYER, 0, "node"),
                "permission checks must delegate to the active backend");
    }

    @Test
    @DisplayName("a later lower-priority registration never demotes the active backend")
    void lowerPriorityDoesNotDemote() {
        PermissionServiceManager.registerService(stub("high", 10, true));
        PermissionServiceManager.registerService(stub("low", 1, false));

        assertEquals("high", PermissionServiceManager.getActiveBackendName());
        assertTrue(PermissionServiceManager.hasPermission(PLAYER, 0, "node"));
    }

    @Test
    @DisplayName("equal-priority registration keeps the first backend active")
    void equalPriorityKeepsFirst() {
        PermissionServiceManager.registerService(stub("first", 5, false));
        PermissionServiceManager.registerService(stub("second", 5, true));

        assertEquals("first", PermissionServiceManager.getActiveBackendName(),
                "a tie must not replace the active backend");
        assertFalse(PermissionServiceManager.hasPermission(PLAYER, 0, "node"));
    }

    @Test
    @DisplayName("fail closed when no backend is registered")
    void failClosedWithoutBackend() {
        assertFalse(PermissionServiceManager.hasPermission(PLAYER, 4, "op"),
                "without a backend no permission may be granted");
        assertEquals("Not initialized", PermissionServiceManager.getActiveBackendName());
    }

    @Test
    @DisplayName("registering null is a no-op, not an error")
    void nullRegistrationIgnored() {
        PermissionServiceManager.registerService(null);

        assertFalse(PermissionServiceManager.hasPermission(PLAYER, 0, "node"),
                "null registration must not change the fail-closed state");
        assertEquals("Not initialized", PermissionServiceManager.getActiveBackendName());
    }

    @Test
    @DisplayName("duplicate registration is a no-op")
    void duplicateRegistrationIsNoOp() {
        PermissionServiceManager.registerService(stub("backend", 7, true));
        PermissionServiceManager.registerService(stub("backend", 7, true));

        assertEquals("backend", PermissionServiceManager.getActiveBackendName());
        assertTrue(PermissionServiceManager.hasPermission(PLAYER, 0, "node"));
    }

    @Test
    @DisplayName("reset() clears the active backend back to fail-closed")
    void resetClearsActiveBackend() {
        PermissionServiceManager.registerService(stub("backend", 7, true));
        assertTrue(PermissionServiceManager.hasPermission(PLAYER, 0, "node"));

        PermissionServiceManager.reset();

        assertFalse(PermissionServiceManager.hasPermission(PLAYER, 0, "node"),
                "after reset the manager must fail closed again");
        assertEquals("Not initialized", PermissionServiceManager.getActiveBackendName());
    }

    private static IPermissionService stub(String name, int priority, boolean allowed) {
        return new IPermissionService() {
            @Override
            public boolean hasPermission(UUID uuid, int opLevel, String permissionNode) {
                return allowed;
            }

            @Override
            public String getActiveBackendName() {
                return name;
            }

            @Override
            public int getPriority() {
                return priority;
            }
        };
    }
}
