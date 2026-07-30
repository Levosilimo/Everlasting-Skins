package levosilimo.everlastingskins.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VanillaPermissionServiceTest {

    private static final UUID TEST_UUID = UUID.randomUUID();
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
        assertTrue(service instanceof IPermissionService);
    }

    @Test
    @DisplayName("OP player has permission")
    void hasPermission_opPlayer_returnsTrue() {
        PermissionContext ctx = PermissionContext.of(TEST_UUID, true);
        assertTrue(service.hasPermission(ctx, "any.node"));
    }

    @Test
    @DisplayName("Non-OP player lacks permission")
    void hasPermission_nonOpPlayer_returnsFalse() {
        PermissionContext ctx = PermissionContext.of(TEST_UUID, false);
        assertFalse(service.hasPermission(ctx, "any.node"));
    }

    @Test
    @DisplayName("OP level 1 is insufficient")
    void hasPermission_opLevel1_returnsFalse() {
        PermissionContext ctx = PermissionContext.of(TEST_UUID, false);
        assertFalse(service.hasPermission(ctx, "any.node"));
    }
}
