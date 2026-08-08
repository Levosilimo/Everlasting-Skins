/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission;

import levosilimo.everlastingskins.permission.forge.ForgePermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JUnit tests for the 1.10.2 {@link ForgePermissionService} adapter
 * (memory #1115: deterministic fakes only — the op-level source is stubbed
 * via the protected {@code resolveOpLevel} seam; no live server, no HTTP).
 */
class IPermissionServiceAdapterTest {

    private static final UUID PLAYER = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

    /** Stub backend with a fixed player op level. */
    private static final class StubForgePermissionService extends ForgePermissionService {
        private final int opLevel;

        StubForgePermissionService(int opLevel) {
            this.opLevel = opLevel;
        }

        @Override
        protected int resolveOpLevel(UUID uuid) {
            return opLevel;
        }
    }

    @BeforeEach
    void resetManager() {
        PermissionServiceManager.reset();
    }

    @Test
    void grantsWhenPlayerOpLevelMeetsRequirement() {
        ForgePermissionService service = new StubForgePermissionService(4);
        assertTrue(service.hasPermission(PLAYER, 2, ForgePermissionService.SKIN_OTHER_NODE));
        assertTrue(service.hasPermission(PLAYER, 4, ForgePermissionService.SKIN_OTHER_NODE));
    }

    @Test
    void deniesWhenPlayerOpLevelBelowRequirement() {
        ForgePermissionService service = new StubForgePermissionService(1);
        assertFalse(service.hasPermission(PLAYER, 2, ForgePermissionService.SKIN_OTHER_NODE));
    }

    @Test
    void sourceNodeAlwaysGrantedRegardlessOfOpLevel() {
        ForgePermissionService service = new StubForgePermissionService(0);
        assertTrue(service.hasPermission(PLAYER, 2, ForgePermissionService.SKIN_SOURCE_NODE));
    }

    @Test
    void noServerContextFallsBackToVanillaOpLevelGate() {
        // resolveOpLevel() == -1 means no server context (unit tests, pre-boot).
        ForgePermissionService service = new StubForgePermissionService(-1);
        assertTrue(service.hasPermission(PLAYER, 0, ForgePermissionService.SKIN_NODE));
        assertFalse(service.hasPermission(PLAYER, 2, ForgePermissionService.SKIN_NODE));
    }

    @Test
    void backendNameAndPriority() {
        ForgePermissionService service = new StubForgePermissionService(4);
        assertEquals("Forge ops (1.10.2)", service.getActiveBackendName());
        assertEquals(10, service.getPriority());
    }

    @Test
    void failClosedWhenNoBackendRegistered() {
        // PermissionServiceManager without any registered backend denies
        // everything (mirror of the :common manager contract).
        assertFalse(PermissionServiceManager.hasPermission(PLAYER, 0, ForgePermissionService.SKIN_NODE));
        assertEquals("Not initialized", PermissionServiceManager.getActiveBackendName());
    }

    @Test
    void registeredBackendBecomesActive() {
        ForgePermissionService.register();
        assertTrue(PermissionServiceManager.hasPermission(PLAYER, 0, ForgePermissionService.SKIN_NODE));
        assertEquals("Forge ops (1.10.2)", PermissionServiceManager.getActiveBackendName());
    }
}
