/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.permission;

import levosilimo.everlastingskins.forge26_1.permission.LuckPermsPermissionService;
import levosilimo.everlastingskins.forge26_1.permission.VanillaPermissionService;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.User;
import net.luckperms.api.StubUserManager;
import net.luckperms.api.UserManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Permission-backend coverage for the forge-26.2 binding (memory #1123:
 * everything is UUID-keyed — no player objects cross into :common).
 *
 * <p>The LuckPerms API stubs under {@code net.luckperms.api} (test sources)
 * make the API reflectively visible; the user is deliberately NOT loaded so
 * the checks exercise the vanilla fallback path. Hand-rolled stubs, no
 * Mockito (Java 25 inline-mock instrumentation unsupported on Mockito 5.12).
 */
class SkinPermissionServiceTest {

    private static final UUID PLAYER = UUID.randomUUID();

    @BeforeAll
    static void loadConfig() {
        TestConfigSupport.loadDefaults();
    }

    @BeforeEach
    void resetManager() {
        PermissionServiceManager.reset();
    }

    @Test
    @DisplayName("Vanilla backend grants op-level nodes by required level")
    void permissionService_vanilla_grantsOpsNodes() {
        PermissionServiceManager.registerService(new VanillaPermissionService());
        assertTrue(PermissionServiceManager.hasPermission(PLAYER, 0, "everlastingskins.command.skin"));
        assertTrue(PermissionServiceManager.hasPermission(PLAYER, 2, "everlastingskins.command.metrics"));
        assertFalse(PermissionServiceManager.hasPermission(PLAYER, 0, "everlastingskins.command.metrics"));
        assertTrue(PermissionServiceManager.hasPermission(PLAYER, 0, "everlastingskins.command.skin.source"));
        assertEquals("Vanilla (per-command op levels)", PermissionServiceManager.getActiveBackendName());
    }

    @Test
    @DisplayName("LuckPerms service falls back to vanilla per-node levels when the user is not pre-loaded")
    void permissionService_luckperms_fallback() {
        LuckPermsPermissionService lp = registeredLuckPerms();
        assertNotNull(lp, "LuckPerms API stubs should let tryCreate() succeed");
        PermissionServiceManager.registerService(lp);
        // User not loaded => vanillaFallback path (op level 0 is enough for the
        // base skin node; metrics needs level 2).
        assertTrue(PermissionServiceManager.hasPermission(PLAYER, 0, "everlastingskins.command.skin"));
        assertFalse(PermissionServiceManager.hasPermission(PLAYER, 0, "everlastingskins.command.metrics"));
        assertTrue(PermissionServiceManager.hasPermission(PLAYER, 2, "everlastingskins.command.metrics"));
    }

    @Test
    @DisplayName("Manager is fail-closed before any backend registers")
    void permissionService_managerFailClosedBeforeInit() {
        assertFalse(PermissionServiceManager.hasPermission(PLAYER, 4, "everlastingskins.command.skin"),
                "No registered backend must never grant a permission");
        assertEquals("Not initialized", PermissionServiceManager.getActiveBackendName());
    }

    @Test
    @DisplayName("Highest-priority backend wins (LuckPerms over Vanilla)")
    void permissionService_highestPriorityWins() {
        PermissionServiceManager.registerService(new VanillaPermissionService());
        PermissionServiceManager.registerService(registeredLuckPerms());
        assertTrue(PermissionServiceManager.getActiveBackendName().startsWith("LuckPerms"));
    }

    /** Registers a stub LuckPerms API and builds the service, then unregisters. */
    private static LuckPermsPermissionService registeredLuckPerms() {
        UserManager userManager = new StubUserManager(false);
        LuckPerms luckPerms = new LuckPerms() {
            @Override
            public UserManager getUserManager() {
                return userManager;
            }

            @Override
            public String getAPIVersion() {
                return "5.5-test";
            }
        };
        LuckPermsProvider.register(luckPerms);
        try {
            return LuckPermsPermissionService.tryCreate();
        } finally {
            LuckPermsProvider.unregister();
        }
    }
}
