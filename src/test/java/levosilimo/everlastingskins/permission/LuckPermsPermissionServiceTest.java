package levosilimo.everlastingskins.permission;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.PermissionData;
import net.luckperms.api.User;
import net.luckperms.api.UserManager;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LuckPermsPermissionServiceTest {

    private UUID uuid;

    @BeforeEach
    void setUp() {
        uuid = UUID.randomUUID();
        LuckPerms fakeLP = mock(LuckPerms.class);
        UserManager fakeUM = mock(UserManager.class);
        User fakeUser = mock(User.class);
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

    /* ================================================================== */
    /*  Basic behaviour                                                    */
    /* ================================================================== */

    @Test
    @DisplayName("tryCreate returns service when LuckPerms shadow is available")
    void tryCreate_withShadow_returnsService() {
        assertNotNull(LuckPermsPermissionService.tryCreate());
    }

    @Test
    @DisplayName("hasPermission returns true for granted node")
    void hasPermission_granted_returnsTrue() {
        LuckPermsPermissionService service = LuckPermsPermissionService.tryCreate();
        PermissionContext ctx = PermissionContext.of(uuid, false);
        assertTrue(service.hasPermission(ctx, "everlastingskins.command.skin"));
    }

    @Test
    @DisplayName("hasPermission returns false for denied node")
    void hasPermission_denied_returnsFalse() {
        LuckPermsPermissionService service = LuckPermsPermissionService.tryCreate();
        PermissionContext ctx = PermissionContext.of(uuid, false);
        assertFalse(service.hasPermission(ctx, "other.node"));
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

    /* ================================================================== */
    /*  Exception resilience                                               */
    /* ================================================================== */

    @Nested
    @DisplayName("Exception resilience")
    class ExceptionResilience {

        @Test
        @DisplayName("RuntimeException in getUser → returns false (not propagate)")
        void getUser_throwsRuntimeException_returnsFalse() {
            LuckPerms fakeLP = mock(LuckPerms.class);
            UserManager fakeUM = mock(UserManager.class);
            when(fakeLP.getUserManager()).thenReturn(fakeUM);
            when(fakeLP.getAPIVersion()).thenReturn("5.5-test");
            when(fakeUM.isLoaded(uuid)).thenReturn(true);
            when(fakeUM.getUser(uuid)).thenThrow(new RuntimeException("LP unavailable"));
            LuckPermsProvider.unregister();
            LuckPermsProvider.register(fakeLP);

            LuckPermsPermissionService service = LuckPermsPermissionService.tryCreate();
            PermissionContext ctx = PermissionContext.of(uuid, false);

            assertFalse(service.hasPermission(ctx, "everlastingskins.command.skin"));
        }

        @Test
        @DisplayName("ClassCastException in reflection chain → returns false")
        void getCachedData_throwsClassCastException_returnsFalse() {
            LuckPerms fakeLP = mock(LuckPerms.class);
            UserManager fakeUM = mock(UserManager.class);
            User fakeUser = mock(User.class);
            when(fakeLP.getUserManager()).thenReturn(fakeUM);
            when(fakeLP.getAPIVersion()).thenReturn("5.5-test");
            when(fakeUM.isLoaded(uuid)).thenReturn(true);
            when(fakeUM.getUser(uuid)).thenReturn(fakeUser);
            when(fakeUser.getCachedData()).thenThrow(new ClassCastException("bad cast"));
            LuckPermsProvider.unregister();
            LuckPermsProvider.register(fakeLP);

            LuckPermsPermissionService service = LuckPermsPermissionService.tryCreate();
            PermissionContext ctx = PermissionContext.of(uuid, false);

            assertFalse(service.hasPermission(ctx, "everlastingskins.command.skin"));
        }

        @Test
        @DisplayName("Reflection method not found → returns false")
        void reflectionMethodNotFound_returnsFalse() {
            LuckPerms fakeLP = mock(LuckPerms.class);
            UserManager fakeUM = mock(UserManager.class);
            User fakeUser = mock(User.class);
            CachedPermissionData fakeCPD = mock(CachedPermissionData.class);
            when(fakeLP.getUserManager()).thenReturn(fakeUM);
            when(fakeLP.getAPIVersion()).thenReturn("5.5-test");
            when(fakeUM.isLoaded(uuid)).thenReturn(true);
            when(fakeUM.getUser(uuid)).thenReturn(fakeUser);
            when(fakeUser.getCachedData()).thenReturn(fakeCPD);
            // getPermissionData will throw NoSuchMethodException because
            // QueryOptions mock doesn't have the real method
            when(fakeCPD.getPermissionData(any(QueryOptions.class)))
                .thenThrow(new RuntimeException("no such method"));
            LuckPermsProvider.unregister();
            LuckPermsProvider.register(fakeLP);

            LuckPermsPermissionService service = LuckPermsPermissionService.tryCreate();
            PermissionContext ctx = PermissionContext.of(uuid, false);

            assertFalse(service.hasPermission(ctx, "everlastingskins.command.skin"));
        }
    }

    /* ================================================================== */
    /*  User null fallback                                                 */
    /* ================================================================== */

    @Nested
    @DisplayName("User null fallback")
    class UserNullFallback {

        @Test
        @DisplayName("getUser returns null → falls back to context.isOp()")
        void getUserReturnsNull_fallbackToOp() {
            LuckPerms fakeLP = mock(LuckPerms.class);
            UserManager fakeUM = mock(UserManager.class);
            when(fakeLP.getUserManager()).thenReturn(fakeUM);
            when(fakeLP.getAPIVersion()).thenReturn("5.5-test");
            when(fakeUM.isLoaded(uuid)).thenReturn(true);
            when(fakeUM.getUser(uuid)).thenReturn(null);
            LuckPermsProvider.unregister();
            LuckPermsProvider.register(fakeLP);

            LuckPermsPermissionService service = LuckPermsPermissionService.tryCreate();

            PermissionContext opCtx = PermissionContext.of(uuid, true);
            assertTrue(service.hasPermission(opCtx, "everlastingskins.command.skin"));

            PermissionContext nonOpCtx = PermissionContext.of(uuid, false);
            assertFalse(service.hasPermission(nonOpCtx, "everlastingskins.command.skin"));
        }

        @Test
        @DisplayName("User not loaded → returns false (no async load)")
        void userNotLoaded_returnsFalse() {
            LuckPerms fakeLP = mock(LuckPerms.class);
            UserManager fakeUM = mock(UserManager.class);
            when(fakeLP.getUserManager()).thenReturn(fakeUM);
            when(fakeLP.getAPIVersion()).thenReturn("5.5-test");
            when(fakeUM.isLoaded(uuid)).thenReturn(false);
            LuckPermsProvider.unregister();
            LuckPermsProvider.register(fakeLP);

            LuckPermsPermissionService service = LuckPermsPermissionService.tryCreate();
            PermissionContext ctx = PermissionContext.of(uuid, false);

            assertFalse(service.hasPermission(ctx, "everlastingskins.command.skin"));
        }
    }
}
