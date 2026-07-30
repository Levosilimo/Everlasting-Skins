package levosilimo.everlastingskins.permission;

import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionServiceManagerTest {

    @BeforeEach
    @AfterEach
    void resetManager() {
        PermissionServiceManager.reset();
    }

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
    @DisplayName("hasPermission before init falls back to Vanilla")
    void hasPermission_beforeInit_returnsFallback() {
        PermissionServiceManager.reset();
        ServerPlayer mockPlayer = mock(ServerPlayer.class);
        when(mockPlayer.hasPermissions(2)).thenReturn(true);
        assertTrue(PermissionServiceManager.hasPermission(mockPlayer, "any.node"));
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
}
