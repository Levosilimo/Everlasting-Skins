package levosilimo.everlastingskins.permission;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.PermissionData;
import net.luckperms.api.User;
import net.luckperms.api.UserManager;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LuckPermsPermissionServiceTest {

    private UUID uuid;
    private User fakeUser;

    @BeforeEach
    void setUp() {
        uuid = UUID.randomUUID();
        LuckPerms fakeLP = mock(LuckPerms.class);
        UserManager fakeUM = mock(UserManager.class);
        fakeUser = mock(User.class);
        CachedPermissionData fakeCPD = mock(CachedPermissionData.class);
        PermissionData fakePD = mock(PermissionData.class);

        when(fakeLP.getUserManager()).thenReturn(fakeUM);
        when(fakeLP.getAPIVersion()).thenReturn("5.5-test");
        when(fakeUM.isLoaded(uuid)).thenReturn(true);
        when(fakeUM.getUser(uuid)).thenReturn(fakeUser);
        when(fakeUM.loadUser(uuid)).thenReturn(CompletableFuture.completedFuture(fakeUser));
        when(fakeUser.getCachedData()).thenReturn(fakeCPD);
        when(fakeCPD.getPermissionData(any(QueryOptions.class))).thenReturn(fakePD);
        when(fakePD.checkPermission("everlastingskins.command.skin")).thenReturn(Tristate.TRUE);
        when(fakePD.checkPermission("other.node")).thenReturn(Tristate.FALSE);

        LuckPermsProvider.register(fakeLP);
    }

    @AfterEach
    void tearDown() {
        LuckPermsProvider.unregister();
    }

    @Test
    @DisplayName("tryCreate returns service when LuckPerms shadow is available")
    void tryCreate_withShadow_returnsService() {
        assertNotNull(LuckPermsPermissionService.tryCreate());
    }

    @Test
    @DisplayName("hasPermission returns true for granted node")
    void hasPermission_granted_returnsTrue() {
        LuckPermsPermissionService service = LuckPermsPermissionService.tryCreate();
        EntityPlayerMP mockPlayer = mock(EntityPlayerMP.class);
        when(mockPlayer.getUniqueID()).thenReturn(uuid);
        assertTrue(service.hasPermission(mockPlayer, "everlastingskins.command.skin"));
    }

    @Test
    @DisplayName("hasPermission returns false for denied node")
    void hasPermission_denied_returnsFalse() {
        LuckPermsPermissionService service = LuckPermsPermissionService.tryCreate();
        EntityPlayerMP mockPlayer = mock(EntityPlayerMP.class);
        when(mockPlayer.getUniqueID()).thenReturn(uuid);
        assertFalse(service.hasPermission(mockPlayer, "other.node"));
    }

    @Test
    @DisplayName("getActiveBackendName includes version")
    void getActiveBackendName_includesVersion() {
        LuckPermsPermissionService service = LuckPermsPermissionService.tryCreate();
        assertTrue(service.getActiveBackendName().startsWith("LuckPerms"));
        assertTrue(service.getActiveBackendName().contains("5.5-test"));
    }

    @Test
    @DisplayName("getPriority returns 20")
    void getPriority_isHighest() {
        assertEquals(20, LuckPermsPermissionService.tryCreate().getPriority());
    }
}
