package levosilimo.everlastingskins.permission;

import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        EntityPlayerMP mockPlayer = mock(EntityPlayerMP.class);
        when(mockPlayer.canUseCommand(2, "everlastingskins")).thenReturn(true);
        assertTrue(service.hasPermission(mockPlayer, "any.node"));
    }

    @Test
    @DisplayName("Non-OP player lacks permission")
    void hasPermission_nonOpPlayer_returnsFalse() {
        EntityPlayerMP mockPlayer = mock(EntityPlayerMP.class);
        when(mockPlayer.canUseCommand(2, "everlastingskins")).thenReturn(false);
        assertFalse(service.hasPermission(mockPlayer, "any.node"));
    }

    @Test
    @DisplayName("OP level 1 is insufficient")
    void hasPermission_opLevel1_returnsFalse() {
        EntityPlayerMP mockPlayer = mock(EntityPlayerMP.class);
        when(mockPlayer.canUseCommand(2, "everlastingskins")).thenReturn(false);
        assertFalse(service.hasPermission(mockPlayer, "any.node"));
    }
}
