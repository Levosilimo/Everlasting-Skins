/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.permission;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PermissionServiceManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private static IPermissionService activeService = null;
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        List<IPermissionService> candidates = new ArrayList<>();

        candidates.add(new VanillaPermissionService());

        LuckPermsPermissionService lp = LuckPermsPermissionService.tryCreate();
        if (lp != null) {
            candidates.add(lp);
        }

        activeService = candidates.stream()
            .max(Comparator.comparingInt(IPermissionService::getPriority))
            .orElseThrow();

        LOGGER.info("Permission backend active: {}", activeService.getActiveBackendName());
    }

    public static void registerService(IPermissionService service) {
        if (!initialized) {
            throw new IllegalStateException("PermissionServiceManager.init() must be called first");
        }
        if (service.getPriority() > activeService.getPriority()) {
            activeService = service;
            LOGGER.info("Permission backend upgraded to: {}", activeService.getActiveBackendName());
        }
    }

    public static boolean hasPermission(PermissionContext context, String node) {
        if (!initialized) {
            return new VanillaPermissionService().hasPermission(context, node);
        }
        return activeService.hasPermission(context, node);
    }

    static void reset() {
        activeService = null;
        initialized = false;
    }

    public static String getActiveBackendName() {
        return activeService != null ? activeService.getActiveBackendName() : "Not initialized";
    }
}
