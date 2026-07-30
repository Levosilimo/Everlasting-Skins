package levosilimo.everlastingskins.permission;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

public class LuckPermsPermissionService implements IPermissionService {

    // LP optional dependency: mcmod.info does not support optional dependency
    // declarations. The reflection-only pattern (Class.forName) is the only
    // mechanism for soft-integration on Forge 1.12.2.

    private static final Logger LOGGER = LogManager.getLogger();
    private final Object luckPermsApi;
    private final Object userManager;

    private LuckPermsPermissionService(Object luckPermsApi, Object userManager) {
        this.luckPermsApi = luckPermsApi;
        this.userManager = userManager;
    }

    public static LuckPermsPermissionService tryCreate() {
        try {
            Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPerms");
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            Object luckPermsApi = getMethod.invoke(null);

            Method getUserManagerMethod = luckPermsClass.getMethod("getUserManager");
            Object userManager = getUserManagerMethod.invoke(luckPermsApi);

            LOGGER.info("LuckPerms detected! Enabling detailed permission support.");
            return new LuckPermsPermissionService(luckPermsApi, userManager);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                | java.lang.reflect.InvocationTargetException | NoClassDefFoundError e) {
            return null;
        }
    }

    @Override
    public boolean hasPermission(PermissionContext context, String permissionNode) {
        if (luckPermsApi == null || userManager == null) {
            return false;
        }
        try {
            UUID uuid = context.uuid();
            Method isLoadedMethod = userManager.getClass().getMethod("isLoaded", UUID.class);
            Boolean isLoaded = (Boolean) isLoadedMethod.invoke(userManager, uuid);
            Object user = null;
            if (Boolean.TRUE.equals(isLoaded)) {
                Method getUserMethod = userManager.getClass().getMethod("getUser", UUID.class);
                user = getUserMethod.invoke(userManager, uuid);
            } else {
                LOGGER.debug("LP user {} not pre-loaded, skipping async load", uuid);
                return false;
            }
            if (user == null) {
                LOGGER.warn("LP user {} returned null from getUser, falling back to vanilla", uuid);
                return vanillaFallback(context, permissionNode);
            }

            Method getCachedDataMethod = user.getClass().getMethod("getCachedData");
            Object cachedData = getCachedDataMethod.invoke(user);

            Class<?> cachedDataClass = Class.forName("net.luckperms.api.cacheddata.CachedPermissionData");
            Class<?> tristateClass = Class.forName("net.luckperms.api.util.Tristate");
            Class<?> queryOptionsClass = Class.forName("net.luckperms.api.query.QueryOptions");

            Method defaultOfMethod = queryOptionsClass.getMethod("defaultContextualOptions");
            Object defaultQueryOptions = defaultOfMethod.invoke(null);

            Method getPermissionDataMethod = cachedDataClass.getMethod("getPermissionData", queryOptionsClass);
            Object permissionData = getPermissionDataMethod.invoke(cachedData, defaultQueryOptions);

            Method checkPermissionMethod = permissionData.getClass().getMethod("checkPermission", String.class);
            Object tristate = checkPermissionMethod.invoke(permissionData, permissionNode);

            Method asBooleanMethod = tristateClass.getMethod("asBoolean");
            return (Boolean) asBooleanMethod.invoke(tristate);
        } catch (Exception e) {
            LOGGER.debug("LuckPerms permission check failed for node {}: {}", permissionNode, e.getMessage());
            return false;
        }
    }

    private boolean vanillaFallback(PermissionContext context, String permissionNode) {
        return context.isOp();
    }

    @Override
    public String getActiveBackendName() {
        try {
            Method getAPIVersionMethod = luckPermsApi.getClass().getMethod("getAPIVersion");
            return "LuckPerms (v" + getAPIVersionMethod.invoke(luckPermsApi) + ")";
        } catch (Exception e) {
            return "LuckPerms";
        }
    }

    @Override
    public int getPriority() {
        return 20;
    }
}
