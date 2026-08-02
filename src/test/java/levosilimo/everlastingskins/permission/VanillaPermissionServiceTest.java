/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VanillaPermissionServiceTest {

    private final VanillaPermissionService service = new VanillaPermissionService();

    @Test
    @DisplayName("Backend name is correct")
    void backendName() {
        assertEquals("Vanilla (op level 2)", service.getActiveBackendName());
    }

    @Test
    @DisplayName("Priority is 0")
    void priority() {
        assertEquals(0, service.getPriority());
    }

    @Test
    @DisplayName("Service is an IPermissionService")
    void implementsInterface() {
        assertInstanceOf(IPermissionService.class, service);
    }

    @Test
    @DisplayName("OP player has permission")
    void hasPermission_opPlayer_returnsTrue() {
        PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), true);
        assertTrue(service.hasPermission(ctx, "any.node"));
    }

    @Test
    @DisplayName("Non-OP player lacks permission")
    void hasPermission_nonOpPlayer_returnsFalse() {
        PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), false);
        assertFalse(service.hasPermission(ctx, "any.node"));
    }

    @Test
    @DisplayName("Source node is always granted regardless of op status")
    void hasPermission_sourceNode_returnsTrueForNonOp() {
        PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), false);
        assertTrue(service.hasPermission(ctx, "everlastingskins.command.skin.source"));
    }
}
