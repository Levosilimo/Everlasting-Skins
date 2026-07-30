package levosilimo.everlastingskins.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PermissionServiceManagerTest {

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
}
