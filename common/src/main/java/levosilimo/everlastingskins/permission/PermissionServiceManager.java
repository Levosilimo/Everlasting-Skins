/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

/**
 * Registry for permission backends, decoupled from the per-version services
 * (vanilla op-levels, LuckPerms, Forge). Per-version bootstrap registers its
 * own services via {@link #registerService(IPermissionService)}; the
 * highest-priority registration becomes active. The previous hardcoded
 * {@code init()} candidate discovery lived in the per-version layer (it
 * constructed MC-bound services) and is now the per-version bootstrap's job.
 * Without any registered backend, permission checks fail closed.
 */
public final class PermissionServiceManager {

    private static final Logger LOGGER = LogManager.getLogger(PermissionServiceManager.class);

    private static IPermissionService activeService = null;

    private PermissionServiceManager() {}

    /** Registers a candidate backend; the highest-priority registered service becomes active. */
    public static synchronized void registerService(IPermissionService service) {
        if (service == null) {
            return;
        }
        if (activeService == null || service.getPriority() > activeService.getPriority()) {
            activeService = service;
            LOGGER.info("Permission backend active: {}", service.getActiveBackendName());
        }
    }

    /**
     * Fail-closed check: without a registered backend no permission is
     * granted (the previous uninitialized fallback constructed the per-version
     * vanilla service, which this module cannot do).
     */
    public static boolean hasPermission(UUID uuid, int opLevel, String node) {
        IPermissionService service = activeService;
        if (service == null) {
            return false;
        }
        return service.hasPermission(uuid, opLevel, node);
    }

    /* package-private for testing */
    static void reset() {
        activeService = null;
    }

    public static String getActiveBackendName() {
        return activeService != null ? activeService.getActiveBackendName() : "Not initialized";
    }
}
