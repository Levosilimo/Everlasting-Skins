/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.permission;

import levosilimo.everlastingskins.Config;

public class VanillaPermissionService implements IPermissionService {

    private static final int BYPASS_COOLDOWN_OP_LEVEL = 2;

    @Override
    public boolean hasPermission(PermissionContext context, String permissionNode) {
        if (permissionNode.endsWith(".source")) return true;
        if (permissionNode.endsWith(".bypass.cooldown")) {
            return context.opLevel() >= BYPASS_COOLDOWN_OP_LEVEL;
        }
        return context.opLevel() >= requiredOpLevel(permissionNode);
    }

    private static int requiredOpLevel(String permissionNode) {
        switch (permissionNode) {
            case "everlastingskins.command.skin":
                return Config.PERMISSIONS_OP_LEVEL_MOJANG.get();
            case "everlastingskins.command.skin.url":
                return Config.PERMISSIONS_OP_LEVEL_URL.get();
            case "everlastingskins.command.skin.clear":
                return Config.PERMISSIONS_OP_LEVEL_CLEAR.get();
            case "everlastingskins.command.skin.random":
                return Config.PERMISSIONS_OP_LEVEL_RANDOM.get();
            case "everlastingskins.command.skin.other":
                return Config.PERMISSIONS_OP_LEVEL_OTHER.get();
            case "everlastingskins.command.metrics":
                return Config.PERMISSIONS_OP_LEVEL_METRICS.get();
            case "everlastingskins.command.metrics.reset":
                return Config.PERMISSIONS_OP_LEVEL_METRICS_RESET.get();
            default:
                return 0;
        }
    }

    @Override
    public String getActiveBackendName() {
        return "Vanilla (per-command op levels)";
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
