/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * M2 step 5: PermissionServiceManager now comes from /common as a
 * registration-based, fail-closed registry (no init() candidate discovery —
 * the per-version bootstrap registers its own services).
 */
class PermissionServiceManagerTest {

    private static final UUID TEST_UUID = UUID.randomUUID();

    @BeforeEach
    @AfterEach
    void resetManager() {
        PermissionServiceManager.reset();
    }

    @Test
    @DisplayName("Registering Vanilla selects it as the active backend")
    void registerVanilla_becomesActive() {
        PermissionServiceManager.registerService(new VanillaPermissionService());
        assertTrue(PermissionServiceManager.getActiveBackendName().startsWith("Vanilla"));
    }

    @Test
    @DisplayName("Backend name is not empty after registration")
    void backendNameNotEmpty() {
        PermissionServiceManager.registerService(new VanillaPermissionService());
        assertNotNull(PermissionServiceManager.getActiveBackendName());
        assertFalse(PermissionServiceManager.getActiveBackendName().isEmpty());
    }

    @Test
    @DisplayName("Registering higher-priority service replaces active backend")
    void registerService_higherPriority_replacesActive() {
        PermissionServiceManager.registerService(new VanillaPermissionService());
        IPermissionService mockForge = mock(IPermissionService.class);
        when(mockForge.getPriority()).thenReturn(10);
        when(mockForge.getActiveBackendName()).thenReturn("Forge");
        PermissionServiceManager.registerService(mockForge);
        assertEquals("Forge", PermissionServiceManager.getActiveBackendName());
    }

    @Test
    @DisplayName("Registering lower-priority service does not replace active")
    void registerService_lowerPriority_doesNotReplace() {
        PermissionServiceManager.registerService(new VanillaPermissionService());
        IPermissionService mockLow = mock(IPermissionService.class);
        when(mockLow.getPriority()).thenReturn(-1);
        when(mockLow.getActiveBackendName()).thenReturn("LowPriority");
        PermissionServiceManager.registerService(mockLow);
        assertTrue(PermissionServiceManager.getActiveBackendName().startsWith("Vanilla"));
    }

    @Test
    @DisplayName("hasPermission before any registration fails closed")
    void hasPermission_beforeRegistration_failsClosed() {
        PermissionServiceManager.reset();
        assertFalse(PermissionServiceManager.hasPermission(TEST_UUID, 2, "any.node"));
    }

    @Test
    @DisplayName("hasPermission delegates to the registered backend")
    void hasPermission_delegatesToRegisteredBackend() {
        PermissionServiceManager.registerService(new VanillaPermissionService());
        assertTrue(PermissionServiceManager.hasPermission(TEST_UUID, 0, "any.node"));
        assertFalse(PermissionServiceManager.hasPermission(TEST_UUID, 0,
            "everlastingskins.command.metrics"));
    }

    @Test
    @DisplayName("registerService ignores null")
    void registerService_null_isIgnored() {
        PermissionServiceManager.registerService(null);
        assertEquals("Not initialized", PermissionServiceManager.getActiveBackendName());
    }

    @Test
    @DisplayName("getActiveBackendName returns placeholder before registration")
    void backendNameBeforeInit() {
        PermissionServiceManager.reset();
        assertEquals("Not initialized", PermissionServiceManager.getActiveBackendName());
    }
}
