/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.permission.forge;

import levosilimo.everlastingskins.forge26.permission.PermissionContext;
import levosilimo.everlastingskins.permission.TestConfigSupport;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForgePermissionServiceTest {

    /* ================================================================== */
    /*  Basic behaviour                                                    */
    /* ================================================================== */
    private static final UUID TEST_UUID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @BeforeAll
    static void loadConfig() {
        TestConfigSupport.loadDefaults();
    }

    @Test
    @DisplayName("hasPermission returns true when PermissionAPI grants it")
    void hasPermission_granted_returnsTrue() {
        try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            UUID uuid = UUID.randomUUID();
            PermissionContext ctx = PermissionContext.of(uuid, 0);
            api.when(() -> PermissionAPI.getOfflinePermission(eq(uuid), eq(ForgePermissionService.SKIN_NODE)))
               .thenReturn(true);
            ForgePermissionService service = new ForgePermissionService();
            assertTrue(service.hasPermission(ctx.uuid(), ctx.opLevel(), "everlastingskins.command.skin"));
        }
    }

    @Test
    @DisplayName("hasPermission returns false when PermissionAPI denies it")
    void hasPermission_denied_returnsFalse() {
        try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            UUID uuid = UUID.randomUUID();
            PermissionContext ctx = PermissionContext.of(uuid, 0);
            api.when(() -> PermissionAPI.getOfflinePermission(eq(uuid), eq(ForgePermissionService.SKIN_NODE)))
               .thenReturn(false);
            ForgePermissionService service = new ForgePermissionService();
            assertFalse(service.hasPermission(ctx.uuid(), ctx.opLevel(), "everlastingskins.command.skin"));
        }
    }

    @Test
    @DisplayName("hasPermission maps .skin.other to SKIN_OTHER_NODE")
    void hasPermission_otherNode_mapsCorrectly() {
        try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            UUID uuid = UUID.randomUUID();
            PermissionContext ctx = PermissionContext.of(uuid, 0);
            api.when(() -> PermissionAPI.getOfflinePermission(eq(uuid), eq(ForgePermissionService.SKIN_OTHER_NODE)))
               .thenReturn(true);
            ForgePermissionService service = new ForgePermissionService();
            assertTrue(service.hasPermission(ctx.uuid(), ctx.opLevel(), "everlastingskins.command.skin.other"));
        }
    }

    @Test
    @DisplayName("hasPermission .skin.source always returns true")
    void hasPermission_sourceNode_returnsTrue() {
        ForgePermissionService service = new ForgePermissionService();
        PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), 0);
        assertTrue(service.hasPermission(ctx.uuid(), ctx.opLevel(), "everlastingskins.command.skin.source"));
    }

    @Test
    @DisplayName("hasPermission maps .bypass.cooldown to BYPASS_COOLDOWN_NODE")
    void hasPermission_bypassNode_mapsCorrectly() {
        try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            UUID uuid = UUID.randomUUID();
            api.when(() -> PermissionAPI.getOfflinePermission(eq(uuid), eq(ForgePermissionService.BYPASS_COOLDOWN_NODE)))
               .thenReturn(true);
            ForgePermissionService service = new ForgePermissionService();
            assertTrue(service.hasPermission(PermissionContext.of(uuid, 0).uuid(), PermissionContext.of(uuid, 0).opLevel(), "everlastingskins.bypass.cooldown"));
        }
    }

    @Test
    @DisplayName("permissionGatherEvent registers all skin, source, bypass and metrics nodes")
    void permissionGatherEvent_registersNodes() {
        PermissionGatherEvent.Nodes event = mock(PermissionGatherEvent.Nodes.class);
        ForgePermissionService.onPermissionGather(event);
        verify(event).addNodes(
            ForgePermissionService.SKIN_NODE,
            ForgePermissionService.SKIN_OTHER_NODE,
            ForgePermissionService.SKIN_URL_NODE,
            ForgePermissionService.SKIN_CLEAR_NODE,
            ForgePermissionService.METRICS_NODE,
            ForgePermissionService.METRICS_RESET_NODE,
            ForgePermissionService.SOURCE_NODE,
            ForgePermissionService.BYPASS_COOLDOWN_NODE
        );
    }

    @Test
    @DisplayName("hasPermission falls back to per-node op levels when no server context exists")
    void hasPermission_noServer_usesContextIsOp() {
        try (MockedStatic<ServerLifecycleHooks> hooks = mockStatic(ServerLifecycleHooks.class)) {
            hooks.when(ServerLifecycleHooks::getCurrentServer).thenReturn(null);
            ForgePermissionService service = new ForgePermissionService();
            assertTrue(service.hasPermission(PermissionContext.of(TEST_UUID, 2).uuid(), PermissionContext.of(TEST_UUID, 2).opLevel(), "everlastingskins.command.metrics"));
            assertFalse(service.hasPermission(PermissionContext.of(TEST_UUID, 0).uuid(), PermissionContext.of(TEST_UUID, 0).opLevel(), "everlastingskins.command.metrics"));
        }
    }

    @Test
    @DisplayName("hasPermission never consults the live API without a server context")
    void hasPermission_offline_neverCallsLiveApi() {
        try (MockedStatic<ServerLifecycleHooks> hooks = mockStatic(ServerLifecycleHooks.class);
             MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            hooks.when(ServerLifecycleHooks::getCurrentServer).thenReturn(null);
            api.when(() -> PermissionAPI.getOfflinePermission(eq(TEST_UUID), eq(ForgePermissionService.SKIN_NODE)))
               .thenReturn(false);
            ForgePermissionService service = new ForgePermissionService();
            assertFalse(service.hasPermission(PermissionContext.of(TEST_UUID, 0).uuid(), PermissionContext.of(TEST_UUID, 0).opLevel(), "everlastingskins.command.skin"));
            api.verify(() -> PermissionAPI.getPermission(any(), any()), never());
        }
    }

    @Test
    @DisplayName("hasPermission falls back to per-node op levels when the PermissionAPI lookup throws")
    void hasPermission_lookupThrows_usesContextIsOp() {
        try (MockedStatic<ServerLifecycleHooks> hooks = mockStatic(ServerLifecycleHooks.class);
             MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            hooks.when(ServerLifecycleHooks::getCurrentServer).thenReturn(null);
            api.when(() -> PermissionAPI.getOfflinePermission(eq(TEST_UUID), eq(ForgePermissionService.METRICS_NODE)))
               .thenThrow(new RuntimeException("api unavailable"));
            ForgePermissionService service = new ForgePermissionService();
            assertTrue(service.hasPermission(PermissionContext.of(TEST_UUID, 2).uuid(), PermissionContext.of(TEST_UUID, 2).opLevel(), "everlastingskins.command.metrics"));
            assertFalse(service.hasPermission(PermissionContext.of(TEST_UUID, 0).uuid(), PermissionContext.of(TEST_UUID, 0).opLevel(), "everlastingskins.command.metrics"));
        }
    }

    @Test
    @DisplayName("Backend name is correct")
    void getActiveBackendName() {
        assertTrue(new ForgePermissionService().getActiveBackendName().startsWith("Forge PermissionAPI"),
            "backend name must identify the Forge PermissionAPI backend");
    }

    @Test
    @DisplayName("Priority is 10")
    void getPriority() {
        assertEquals(10, new ForgePermissionService().getPriority());
    }

    /* ================================================================== */
    /*  Exception resilience                                               */
    /* ================================================================== */

    @Nested
    @DisplayName("Exception resilience")
    class ExceptionResilience {

        @Test
        @DisplayName("PermissionAPI.getOfflinePermission throws → falls back to per-node op levels")
        void permissionApi_throws_fallbackToOp() {
            try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
                UUID uuid = UUID.randomUUID();
                api.when(() -> PermissionAPI.getOfflinePermission(eq(uuid), eq(ForgePermissionService.METRICS_NODE)))
                   .thenThrow(new RuntimeException("PermissionAPI unavailable"));

                ForgePermissionService service = new ForgePermissionService();

                PermissionContext opCtx = PermissionContext.of(uuid, 2);
                assertTrue(service.hasPermission(opCtx.uuid(), opCtx.opLevel(), "everlastingskins.command.metrics"));

                PermissionContext nonOpCtx = PermissionContext.of(uuid, 0);
                assertFalse(service.hasPermission(nonOpCtx.uuid(), nonOpCtx.opLevel(), "everlastingskins.command.metrics"));
            }
        }

        @Test
        @DisplayName("PermissionAPI throws for .skin.other → falls back to isOp")
        void permissionApi_throwsOnOtherNode_fallbackToOp() {
            try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
                UUID uuid = UUID.randomUUID();
                api.when(() -> PermissionAPI.getOfflinePermission(eq(uuid), eq(ForgePermissionService.SKIN_OTHER_NODE)))
                   .thenThrow(new RuntimeException("node not registered"));

                ForgePermissionService service = new ForgePermissionService();

                PermissionContext opCtx = PermissionContext.of(uuid, 2);
                assertTrue(service.hasPermission(opCtx.uuid(), opCtx.opLevel(), "everlastingskins.command.skin.other"));

                PermissionContext nonOpCtx = PermissionContext.of(uuid, 0);
                assertFalse(service.hasPermission(nonOpCtx.uuid(), nonOpCtx.opLevel(), "everlastingskins.command.skin.other"));
            }
        }

        @Test
        @DisplayName(".skin.source always true even if API would throw")
        void sourceNode_alwaysTrue_regardlessOfApi() {
            ForgePermissionService service = new ForgePermissionService();
            PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), 0);
            assertTrue(service.hasPermission(ctx.uuid(), ctx.opLevel(), "everlastingskins.command.skin.source"));
        }
    }
}
