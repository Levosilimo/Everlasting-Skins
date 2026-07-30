package levosilimo.everlastingskins.permission.forge;

import levosilimo.everlastingskins.permission.PermissionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ForgePermissionServiceTest {

    private static final UUID TEST_UUID = UUID.randomUUID();

    @Test
    @DisplayName("hasPermission returns true for .source nodes")
    void hasPermission_sourceNode_returnsTrue() {
        PermissionContext ctx = PermissionContext.of(TEST_UUID, false);
        ForgePermissionService service = new ForgePermissionService();
        assertTrue(service.hasPermission(ctx, "everlastingskins.command.skin.source"));
    }

    @Test
    @DisplayName("hasPermission returns context.isOp() for non-source nodes")
    void hasPermission_nonSource_usesContextIsOp() {
        ForgePermissionService service = new ForgePermissionService();
        assertTrue(service.hasPermission(PermissionContext.of(TEST_UUID, true), "everlastingskins.command.skin"));
        assertFalse(service.hasPermission(PermissionContext.of(TEST_UUID, false), "everlastingskins.command.skin"));
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
