/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Op-level gating per node suffix on the 1.7.10 ops model.
 *
 * <p>Deterministic fakes only (memory #1115): pure op-level arithmetic, no
 * server or player context required.
 */
public class VanillaPermissionServiceTest {

    private static final UUID TEST_UUID = UUID.randomUUID();
    private final VanillaPermissionService service = new VanillaPermissionService();

    @Test
    public void backendNameIsCorrect() {
        assertEquals("Vanilla (per-command op levels)", service.getActiveBackendName());
    }

    @Test
    public void priorityIsZero() {
        assertEquals(0, service.getPriority());
    }

    @Test
    public void opLevel0MeetsRequired0() {
        assertTrue(service.hasPermission(TEST_UUID, 0, "everlastingskins.command.skin"));
    }

    @Test
    public void opLevel0LacksRequired2() {
        assertFalse(service.hasPermission(TEST_UUID, 0, "everlastingskins.command.metrics"));
    }

    @Test
    public void opLevel2MeetsRequired2() {
        assertTrue(service.hasPermission(TEST_UUID, 2, "everlastingskins.command.metrics"));
    }

    @Test
    public void sourceNodeAlwaysGrantedForNonOp() {
        assertTrue(service.hasPermission(TEST_UUID, 0, "everlastingskins.command.skin.source"));
    }

    @Test
    public void bypassCooldownRequiresOpLevel2() {
        assertTrue(service.hasPermission(TEST_UUID, 2, "everlastingskins.bypass.cooldown"));
        assertFalse(service.hasPermission(TEST_UUID, 0, "everlastingskins.bypass.cooldown"));
    }

    @Test
    public void unknownNodeDefaultsToAll() {
        assertTrue(service.hasPermission(TEST_UUID, 0, "any.node"));
    }

    @Test
    public void requiredOpLevelMapping() {
        assertEquals(0, VanillaPermissionService.requiredOpLevel("everlastingskins.command.skin"));
        assertEquals(2, VanillaPermissionService.requiredOpLevel("everlastingskins.command.skin.url"));
        assertEquals(2, VanillaPermissionService.requiredOpLevel("everlastingskins.command.skin.other"));
        assertEquals(0, VanillaPermissionService.requiredOpLevel("unknown.node"));
    }
}
