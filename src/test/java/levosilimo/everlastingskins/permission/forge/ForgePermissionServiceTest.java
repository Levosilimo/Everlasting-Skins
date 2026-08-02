package levosilimo.everlastingskins.permission.forge;

import levosilimo.everlastingskins.permission.PermissionContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForgePermissionServiceTest {

    /* ================================================================== */
    /*  Basic behaviour                                                    */
    /* ================================================================== */
    private static final UUID TEST_UUID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    @DisplayName("hasPermission returns true when PermissionAPI grants it")
    void hasPermission_granted_returnsTrue() {
        try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            UUID uuid = UUID.randomUUID();
            PermissionContext ctx = PermissionContext.of(uuid, false);
            api.when(() -> PermissionAPI.getOfflinePermission(eq(uuid), eq(ForgePermissionService.SKIN_NODE)))
               .thenReturn(true);
            ForgePermissionService service = new ForgePermissionService();
            assertTrue(service.hasPermission(ctx, "everlastingskins.command.skin"));
        }
    }

    @Test
    @DisplayName("hasPermission returns false when PermissionAPI denies it")
    void hasPermission_denied_returnsFalse() {
        try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            UUID uuid = UUID.randomUUID();
            PermissionContext ctx = PermissionContext.of(uuid, false);
            api.when(() -> PermissionAPI.getOfflinePermission(eq(uuid), eq(ForgePermissionService.SKIN_NODE)))
               .thenReturn(false);
            ForgePermissionService service = new ForgePermissionService();
            assertFalse(service.hasPermission(ctx, "everlastingskins.command.skin"));
        }
    }

    @Test
    @DisplayName("hasPermission maps .skin.other to SKIN_OTHER_NODE")
    void hasPermission_otherNode_mapsCorrectly() {
        try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            UUID uuid = UUID.randomUUID();
            PermissionContext ctx = PermissionContext.of(uuid, false);
            api.when(() -> PermissionAPI.getOfflinePermission(eq(uuid), eq(ForgePermissionService.SKIN_OTHER_NODE)))
               .thenReturn(true);
            ForgePermissionService service = new ForgePermissionService();
            assertTrue(service.hasPermission(ctx, "everlastingskins.command.skin.other"));
        }
    }

    @Test
    @DisplayName("hasPermission .skin.source always returns true")
    void hasPermission_sourceNode_returnsTrue() {
        ForgePermissionService service = new ForgePermissionService();
        PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), false);
        assertTrue(service.hasPermission(ctx, "everlastingskins.command.skin.source"));
    }

    @Test
    @DisplayName("permissionGatherEvent registers all skin and metrics nodes")
    void permissionGatherEvent_registersNodes() {
        PermissionGatherEvent.Nodes event = mock(PermissionGatherEvent.Nodes.class);
        ForgePermissionService.onPermissionGather(event);
        verify(event).addNodes(
            ForgePermissionService.SKIN_NODE,
            ForgePermissionService.SKIN_OTHER_NODE,
            ForgePermissionService.SKIN_URL_NODE,
            ForgePermissionService.SKIN_CLEAR_NODE,
            ForgePermissionService.METRICS_NODE,
            ForgePermissionService.METRICS_RESET_NODE
        );
    }

    @Test
    @DisplayName("hasPermission falls back to context.isOp() when no server context exists")
    void hasPermission_noServer_usesContextIsOp() {
        try (MockedStatic<ServerLifecycleHooks> hooks = mockStatic(ServerLifecycleHooks.class)) {
            hooks.when(ServerLifecycleHooks::getCurrentServer).thenReturn(null);
            ForgePermissionService service = new ForgePermissionService();
            assertTrue(service.hasPermission(PermissionContext.of(TEST_UUID, true), "everlastingskins.command.skin"));
            assertFalse(service.hasPermission(PermissionContext.of(TEST_UUID, false), "everlastingskins.command.skin"));
        }
    }

    @Test
    @DisplayName("hasPermission queries the live PermissionAPI when the player is online")
    void hasPermission_usesLivePermissionApi_whenPlayerOnline() {
        MinecraftServer server = mock(MinecraftServer.class);
        PlayerList list = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(list);
        ServerPlayer player = mock(ServerPlayer.class);
        when(list.getPlayer(TEST_UUID)).thenReturn(player);

        try (MockedStatic<ServerLifecycleHooks> hooks = mockStatic(ServerLifecycleHooks.class);
             MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            hooks.when(ServerLifecycleHooks::getCurrentServer).thenReturn(server);
            // A non-op player granted the node via the registered handler:
            // the live check must honor it instead of the op-only fallback.
            api.when(() -> PermissionAPI.getPermission(player, ForgePermissionService.SKIN_NODE))
               .thenReturn(true);
            ForgePermissionService service = new ForgePermissionService();
            assertTrue(service.hasPermission(PermissionContext.of(TEST_UUID, false),
                    "everlastingskins.command.skin"),
                "a non-op granted the node via the live PermissionAPI must be allowed");
        }
    }

    @Test
    @DisplayName("Backend name is correct")
    void getActiveBackendName() {
        assertEquals("Forge PermissionAPI (1.21)", new ForgePermissionService().getActiveBackendName());
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
        @DisplayName("PermissionAPI.getOfflinePermission throws → falls back to isOp")
        void permissionApi_throws_fallbackToOp() {
            try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
                UUID uuid = UUID.randomUUID();
                api.when(() -> PermissionAPI.getOfflinePermission(eq(uuid), eq(ForgePermissionService.SKIN_NODE)))
                   .thenThrow(new RuntimeException("PermissionAPI unavailable"));

                ForgePermissionService service = new ForgePermissionService();

                PermissionContext opCtx = PermissionContext.of(uuid, true);
                assertTrue(service.hasPermission(opCtx, "everlastingskins.command.skin"));

                PermissionContext nonOpCtx = PermissionContext.of(uuid, false);
                assertFalse(service.hasPermission(nonOpCtx, "everlastingskins.command.skin"));
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

                PermissionContext opCtx = PermissionContext.of(uuid, true);
                assertTrue(service.hasPermission(opCtx, "everlastingskins.command.skin.other"));

                PermissionContext nonOpCtx = PermissionContext.of(uuid, false);
                assertFalse(service.hasPermission(nonOpCtx, "everlastingskins.command.skin.other"));
            }
        }

        @Test
        @DisplayName(".skin.source always true even if API would throw")
        void sourceNode_alwaysTrue_regardlessOfApi() {
            ForgePermissionService service = new ForgePermissionService();
            PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), false);
            assertTrue(service.hasPermission(ctx, "everlastingskins.command.skin.source"));
        }
    }
}
