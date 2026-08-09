/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission;

import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Permission backend registry tests against the shared :common manager
 * (identical to the 1.6.4 lane's scaffold).
 *
 * <p>Deterministic fakes only (memory #1115): no live HTTP, no server
 * context — the manager's fail-closed contract is exercised with plain
 * anonymous services.
 */
public class PermissionServiceManagerTest {

    private static final UUID TEST_UUID = UUID.randomUUID();

    @Before
    public void resetManager() {
        PermissionServiceManager.reset();
    }

    @Test
    public void registerBackendMakesItActive() {
        PermissionServiceManager.registerService(new FakeService("Vanilla", 0, true));
        assertTrue(PermissionServiceManager.getActiveBackendName().startsWith("Vanilla"));
    }

    @Test
    public void higherPriorityReplacesActive() {
        PermissionServiceManager.registerService(new FakeService("Vanilla", 0, true));
        PermissionServiceManager.registerService(new FakeService("Forge", 10, true));
        assertEquals("Forge", PermissionServiceManager.getActiveBackendName());
    }

    @Test
    public void lowerPriorityDoesNotReplace() {
        PermissionServiceManager.registerService(new FakeService("Vanilla", 0, true));
        PermissionServiceManager.registerService(new FakeService("LowPriority", -1, true));
        assertTrue(PermissionServiceManager.getActiveBackendName().startsWith("Vanilla"));
    }

    @Test
    public void hasPermissionBeforeInitFailsClosed() {
        PermissionServiceManager.reset();
        assertFalse(PermissionServiceManager.hasPermission(TEST_UUID, 2, "any.node"));
    }

    @Test
    public void hasPermissionDelegatesToActiveBackend() {
        PermissionServiceManager.registerService(new FakeService("AllowAll", 10, true));
        assertTrue(PermissionServiceManager.hasPermission(TEST_UUID, 0, "everlastingskins.command.skin"));
    }

    @Test
    public void hasPermissionDelegatesDenial() {
        PermissionServiceManager.registerService(new FakeService("DenyAll", 10, false));
        assertFalse(PermissionServiceManager.hasPermission(TEST_UUID, 4, "everlastingskins.command.skin"));
    }

    @Test
    public void nullRegistrationIsIgnored() {
        PermissionServiceManager.reset();
        PermissionServiceManager.registerService(null);
        assertEquals("Not initialized", PermissionServiceManager.getActiveBackendName());
    }

    @Test
    public void backendNameBeforeInitIsPlaceholder() {
        PermissionServiceManager.reset();
        assertEquals("Not initialized", PermissionServiceManager.getActiveBackendName());
        assertNotNull(PermissionServiceManager.getActiveBackendName());
    }

    /** Deterministic fake backend (memory #1115). */
    private static final class FakeService implements IPermissionService {
        private final String name;
        private final int priority;
        private final boolean grant;

        FakeService(String name, int priority, boolean grant) {
            this.name = name;
            this.priority = priority;
            this.grant = grant;
        }

        @Override
        public boolean hasPermission(UUID uuid, int opLevel, String permissionNode) {
            return grant;
        }

        @Override
        public String getActiveBackendName() {
            return name;
        }

        @Override
        public int getPriority() {
            return priority;
        }
    }
}
