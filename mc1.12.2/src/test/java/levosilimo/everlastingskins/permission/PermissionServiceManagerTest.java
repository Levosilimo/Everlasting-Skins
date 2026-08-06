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

class PermissionServiceManagerTest {

    private static final UUID TEST_UUID = UUID.randomUUID();

    @BeforeEach
    @AfterEach
    void resetManager() {
        PermissionServiceManager.reset();
    }

    @Test
    @DisplayName("Registering Vanilla backend makes it active")
    void initSelectsVanilla() {
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
    void hasPermission_beforeInit_returnsFallback() {
        PermissionServiceManager.reset();
        PermissionContext ctx = PermissionContext.of(TEST_UUID, 2);
        assertFalse(PermissionServiceManager.hasPermission(ctx.uuid(), ctx.opLevel(), "any.node"));
    }

    @Test
    @DisplayName("registerService works without init and becomes active")
    void registerService_beforeInit_throws() {
        PermissionServiceManager.reset();
        IPermissionService mockSvc = mock(IPermissionService.class);
        when(mockSvc.getPriority()).thenReturn(5);
        when(mockSvc.getActiveBackendName()).thenReturn("Registered");
        PermissionServiceManager.registerService(mockSvc);
        assertEquals("Registered", PermissionServiceManager.getActiveBackendName());
    }

    @Test
    @DisplayName("Null service registration is ignored")
    void registerService_null_isIgnored() {
        PermissionServiceManager.reset();
        PermissionServiceManager.registerService(null);
        assertEquals("Not initialized", PermissionServiceManager.getActiveBackendName());
    }

    @Test
    @DisplayName("getActiveBackendName returns placeholder before any registration")
    void backendNameBeforeInit() {
        PermissionServiceManager.reset();
        assertEquals("Not initialized", PermissionServiceManager.getActiveBackendName());
    }
}
