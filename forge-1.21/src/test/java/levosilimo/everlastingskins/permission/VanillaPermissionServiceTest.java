/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.permission;

import levosilimo.everlastingskins.forge21.permission.PermissionContext;
import levosilimo.everlastingskins.forge21.permission.VanillaPermissionService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VanillaPermissionServiceTest {

    private final VanillaPermissionService service = new VanillaPermissionService();

    @BeforeAll
    static void loadConfig() {
        TestConfigSupport.loadDefaults();
    }

    @Test
    @DisplayName("Backend name is correct")
    void backendName() {
        assertEquals("Vanilla (per-command op levels)", service.getActiveBackendName());
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
    @DisplayName("opLevel 0 meets required level 0 (mojang node default)")
    void opLevel0_required0_granted() {
        PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), 0);
        assertTrue(service.hasPermission(ctx.uuid(), ctx.opLevel(), "everlastingskins.command.skin"));
    }

    @Test
    @DisplayName("opLevel 0 lacks required level 2 (metrics node default)")
    void opLevel0_required2_denied() {
        PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), 0);
        assertFalse(service.hasPermission(ctx.uuid(), ctx.opLevel(), "everlastingskins.command.metrics"));
    }

    @Test
    @DisplayName("opLevel 2 meets required level 2")
    void opLevel2_required2_granted() {
        PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), 2);
        assertTrue(service.hasPermission(ctx.uuid(), ctx.opLevel(), "everlastingskins.command.metrics"));
    }

    @Test
    @DisplayName("opLevel 4 meets required level 2")
    void opLevel4_required2_granted() {
        PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), 4);
        assertTrue(service.hasPermission(ctx.uuid(), ctx.opLevel(), "everlastingskins.command.skin.other"));
    }

    @Test
    @DisplayName("Source node is always granted regardless of op level")
    void hasPermission_sourceNode_returnsTrueForNonOp() {
        PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), 0);
        assertTrue(service.hasPermission(ctx.uuid(), ctx.opLevel(), "everlastingskins.command.skin.source"));
    }

    @Test
    @DisplayName("Bypass cooldown node requires op level 2")
    void bypassCooldown_requiresOpLevel2() {
        assertTrue(service.hasPermission(PermissionContext.of(UUID.randomUUID(), 2).uuid(), PermissionContext.of(UUID.randomUUID(), 2).opLevel(), "everlastingskins.bypass.cooldown"));
        assertFalse(service.hasPermission(PermissionContext.of(UUID.randomUUID(), 0).uuid(), PermissionContext.of(UUID.randomUUID(), 0).opLevel(), "everlastingskins.bypass.cooldown"));
    }

    @Test
    @DisplayName("Unknown nodes default to required level 0")
    void unknownNode_defaultsToAll() {
        PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), 0);
        assertTrue(service.hasPermission(ctx.uuid(), ctx.opLevel(), "any.node"));
    }

    @Test
    @DisplayName("Op level outside 0-4 is rejected")
    void opLevelOutOfRange_rejected() {
        assertThrows(IllegalArgumentException.class,
            () -> PermissionContext.of(UUID.randomUUID(), -1));
        assertThrows(IllegalArgumentException.class,
            () -> PermissionContext.of(UUID.randomUUID(), 5));
    }
}
