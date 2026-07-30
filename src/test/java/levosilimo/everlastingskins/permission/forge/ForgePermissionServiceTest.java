package levosilimo.everlastingskins.permission.forge;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.server.permission.PermissionAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ForgePermissionServiceTest {

    @Test
    @DisplayName("hasPermission returns true when PermissionAPI grants it")
    void hasPermission_granted_returnsTrue() {
        try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            EntityPlayerMP mockPlayer = mock(EntityPlayerMP.class);
            api.when(() -> PermissionAPI.hasPermission(eq(mockPlayer), eq("everlastingskins.command.skin")))
               .thenReturn(true);
            ForgePermissionService service = new ForgePermissionService();
            assertTrue(service.hasPermission(mockPlayer, "everlastingskins.command.skin"));
        }
    }

    @Test
    @DisplayName("hasPermission returns false when PermissionAPI denies and not .source")
    void hasPermission_denied_returnsFalse() {
        try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            EntityPlayerMP mockPlayer = mock(EntityPlayerMP.class);
            api.when(() -> PermissionAPI.hasPermission(any(EntityPlayerMP.class), anyString()))
               .thenReturn(false);
            ForgePermissionService service = new ForgePermissionService();
            assertFalse(service.hasPermission(mockPlayer, "everlastingskins.command.skin"));
        }
    }

    @Test
    @DisplayName("hasPermission .skin.source always returns true even when API denies")
    void hasPermission_sourceNode_returnsTrue() {
        try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            EntityPlayerMP mockPlayer = mock(EntityPlayerMP.class);
            api.when(() -> PermissionAPI.hasPermission(any(EntityPlayerMP.class), anyString()))
               .thenReturn(false);
            ForgePermissionService service = new ForgePermissionService();
            assertTrue(service.hasPermission(mockPlayer, "everlastingskins.command.skin.source"));
        }
    }

    @Test
    @DisplayName("Backend name is correct")
    void getActiveBackendName() {
        assertEquals("Forge PermissionAPI (1.12)", new ForgePermissionService().getActiveBackendName());
    }

    @Test
    @DisplayName("Priority is 10")
    void getPriority() {
        assertEquals(10, new ForgePermissionService().getPriority());
    }
}
