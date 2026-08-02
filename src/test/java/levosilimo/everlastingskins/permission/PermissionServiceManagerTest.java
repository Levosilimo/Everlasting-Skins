/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.permission;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionServiceManagerTest {

    @BeforeEach
    @AfterEach
    void resetManager() {
        PermissionServiceManager.reset();
    }

    /* ================================================================== */
    /*  Basic init / backend name                                         */
    /* ================================================================== */

    @Test
    @DisplayName("Init selects Vanilla backend when no LuckPerms available")
    void initSelectsVanilla() {
        PermissionServiceManager.init();
        assertTrue(PermissionServiceManager.getActiveBackendName().startsWith("Vanilla"));
    }

    @Test
    @DisplayName("Backend name is not empty after init")
    void backendNameNotEmpty() {
        PermissionServiceManager.init();
        assertNotNull(PermissionServiceManager.getActiveBackendName());
        assertFalse(PermissionServiceManager.getActiveBackendName().isEmpty());
    }

    /* ================================================================== */
    /*  Priority ordering                                                  */
    /* ================================================================== */

    @Nested
    @DisplayName("Priority ordering")
    class PriorityOrdering {

        @Test
        @DisplayName("Registering higher-priority service replaces active backend")
        void registerService_higherPriority_replacesActive() {
            PermissionServiceManager.init();
            IPermissionService mockForge = mock(IPermissionService.class);
            when(mockForge.getPriority()).thenReturn(10);
            when(mockForge.getActiveBackendName()).thenReturn("Forge");
            PermissionServiceManager.registerService(mockForge);
            assertEquals("Forge", PermissionServiceManager.getActiveBackendName());
        }

        @Test
        @DisplayName("Registering lower-priority service does not replace active")
        void registerService_lowerPriority_doesNotReplace() {
            PermissionServiceManager.init();
            IPermissionService mockLow = mock(IPermissionService.class);
            when(mockLow.getPriority()).thenReturn(-1);
            when(mockLow.getActiveBackendName()).thenReturn("LowPriority");
            PermissionServiceManager.registerService(mockLow);
            assertTrue(PermissionServiceManager.getActiveBackendName().startsWith("Vanilla"));
        }

        @Test
        @DisplayName("Multiple registrations: highest priority wins")
        void multipleRegistrations_highestWins() {
            PermissionServiceManager.init();

            IPermissionService svc10 = mock(IPermissionService.class);
            when(svc10.getPriority()).thenReturn(10);
            when(svc10.getActiveBackendName()).thenReturn("svc10");

            IPermissionService svc20 = mock(IPermissionService.class);
            when(svc20.getPriority()).thenReturn(20);
            when(svc20.getActiveBackendName()).thenReturn("svc20");

            IPermissionService svc5 = mock(IPermissionService.class);
            when(svc5.getPriority()).thenReturn(5);
            when(svc5.getActiveBackendName()).thenReturn("svc5");

            PermissionServiceManager.registerService(svc10);
            assertEquals("svc10", PermissionServiceManager.getActiveBackendName());

            PermissionServiceManager.registerService(svc20);
            assertEquals("svc20", PermissionServiceManager.getActiveBackendName());

            PermissionServiceManager.registerService(svc5);
            assertEquals("svc20", PermissionServiceManager.getActiveBackendName());
        }

        @Test
        @DisplayName("Same-priority service does not replace (strict gt)")
        void samePriority_doesNotReplace() {
            PermissionServiceManager.init();

            // Upgrade to priority 10 first
            IPermissionService prio10 = mock(IPermissionService.class);
            when(prio10.getPriority()).thenReturn(10);
            when(prio10.getActiveBackendName()).thenReturn("prio10");
            PermissionServiceManager.registerService(prio10);
            assertEquals("prio10", PermissionServiceManager.getActiveBackendName());

            // Register another service with same priority — should NOT replace
            IPermissionService same = mock(IPermissionService.class);
            when(same.getPriority()).thenReturn(10);
            when(same.getActiveBackendName()).thenReturn("replacement");
            PermissionServiceManager.registerService(same);

            assertEquals("prio10", PermissionServiceManager.getActiveBackendName());
        }
    }

    /* ================================================================== */
    /*  Reset clears all registered services                               */
    /* ================================================================== */

    @Test
    @DisplayName("Reset clears registered services and initialized flag")
    void reset_clearsState() {
        PermissionServiceManager.init();
        IPermissionService mockSvc = mock(IPermissionService.class);
        when(mockSvc.getPriority()).thenReturn(10);
        when(mockSvc.getActiveBackendName()).thenReturn("MockSvc");
        PermissionServiceManager.registerService(mockSvc);
        assertEquals("MockSvc", PermissionServiceManager.getActiveBackendName());

        PermissionServiceManager.reset();

        assertEquals("Not initialized", PermissionServiceManager.getActiveBackendName());
        PermissionServiceManager.init();
        assertTrue(PermissionServiceManager.getActiveBackendName().startsWith("Vanilla"));
    }

    /* ================================================================== */
    /*  Plugin unload limitation (no downgrade API)                        */
    /* ================================================================== */

    @Nested
    @DisplayName("Plugin unload limitation")
    class PluginUnloadLimitation {

        @Test
        @DisplayName("No unregister API: downgrade not supported")
        void noUnregister_noDowngrade() {
            PermissionServiceManager.init();
            IPermissionService high = mock(IPermissionService.class);
            when(high.getPriority()).thenReturn(30);
            when(high.getActiveBackendName()).thenReturn("high");
            PermissionServiceManager.registerService(high);
            assertEquals("high", PermissionServiceManager.getActiveBackendName());

            // Verify there is no unregisterService method on PermissionServiceManager
            // (This is a documentation test — the API does not expose unregister)
            assertThrows(NoSuchMethodException.class, () -> {
                PermissionServiceManager.class.getMethod("unregisterService", IPermissionService.class);
            });
        }
    }

    /* ================================================================== */
    /*  hasPermission before init (Vanilla fallback)                       */
    /* ================================================================== */

    @Test
    @DisplayName("hasPermission before init falls back to Vanilla")
    void hasPermission_beforeInit_returnsFallback() {
        PermissionServiceManager.reset();
        PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), true);
        assertTrue(PermissionServiceManager.hasPermission(ctx, "any.node"));
    }

    @Test
    @DisplayName("registerService throws before init")
    void registerService_beforeInit_throws() {
        PermissionServiceManager.reset();
        assertThrows(IllegalStateException.class,
            () -> PermissionServiceManager.registerService(mock(IPermissionService.class)));
    }

    @Test
    @DisplayName("getActiveBackendName returns placeholder before init")
    void backendNameBeforeInit() {
        PermissionServiceManager.reset();
        assertEquals("Not initialized", PermissionServiceManager.getActiveBackendName());
    }

    /* ================================================================== */
    /*  Single-player (no external permissions plugin)                     */
    /* ================================================================== */

    @Nested
    @DisplayName("Single-player (no permissions plugin)")
    class SinglePlayer {

        @Test
        @DisplayName("Non-op has no permission on non-source node")
        void nonOp_nonSource_denied() {
            PermissionServiceManager.init();
            PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), false);
            assertFalse(PermissionServiceManager.hasPermission(ctx, "everlastingskins.command.skin"));
        }

        @Test
        @DisplayName("Op has permission on non-source node")
        void op_nonSource_granted() {
            PermissionServiceManager.init();
            PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), true);
            assertTrue(PermissionServiceManager.hasPermission(ctx, "everlastingskins.command.skin"));
        }

        @Test
        @DisplayName("Source node always granted regardless of op status")
        void sourceNode_alwaysGranted() {
            PermissionServiceManager.init();
            PermissionContext nonOp = PermissionContext.of(UUID.randomUUID(), false);
            PermissionContext op = PermissionContext.of(UUID.randomUUID(), true);
            assertTrue(PermissionServiceManager.hasPermission(nonOp, "everlastingskins.command.skin.source"));
            assertTrue(PermissionServiceManager.hasPermission(op, "everlastingskins.command.skin.source"));
        }
    }
}
