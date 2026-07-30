package levosilimo.everlastingskins.permission.forge;

import levosilimo.everlastingskins.permission.PermissionContext;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ForgePermissionServiceTest {

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
    @DisplayName("permissionGatherEvent registers all skin nodes")
    void permissionGatherEvent_registersNodes() {
        PermissionGatherEvent.Nodes event = mock(PermissionGatherEvent.Nodes.class);
        ForgePermissionService.onPermissionGather(event);
        verify(event).addNodes(
            ForgePermissionService.SKIN_NODE,
            ForgePermissionService.SKIN_OTHER_NODE,
            ForgePermissionService.SKIN_URL_NODE,
            ForgePermissionService.SKIN_CLEAR_NODE
        );
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
}
