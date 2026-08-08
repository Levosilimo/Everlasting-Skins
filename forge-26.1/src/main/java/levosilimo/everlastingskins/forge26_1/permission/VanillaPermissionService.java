/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.forge26_1.permission;

import levosilimo.everlastingskins.permission.IPermissionService;
import levosilimo.everlastingskins.Config;

import java.util.UUID;

public class VanillaPermissionService implements IPermissionService {

    private static final int BYPASS_COOLDOWN_OP_LEVEL = 2;

    @Override
    public boolean hasPermission(UUID uuid, int opLevel, String permissionNode) {
        if (permissionNode.endsWith(".source")) return true;
        if (permissionNode.endsWith(".bypass.cooldown")) {
            return opLevel >= BYPASS_COOLDOWN_OP_LEVEL;
        }
        return opLevel >= requiredOpLevel(permissionNode);
    }

    private static int requiredOpLevel(String permissionNode) {
        return switch (permissionNode) {
            case "everlastingskins.command.skin" -> Config.PERMISSIONS_OP_LEVEL_MOJANG.get();
            case "everlastingskins.command.skin.url" -> Config.PERMISSIONS_OP_LEVEL_URL.get();
            case "everlastingskins.command.skin.clear" -> Config.PERMISSIONS_OP_LEVEL_CLEAR.get();
            case "everlastingskins.command.skin.random" -> Config.PERMISSIONS_OP_LEVEL_RANDOM.get();
            case "everlastingskins.command.skin.other" -> Config.PERMISSIONS_OP_LEVEL_OTHER.get();
            case "everlastingskins.command.metrics" -> Config.PERMISSIONS_OP_LEVEL_METRICS.get();
            case "everlastingskins.command.metrics.reset" -> Config.PERMISSIONS_OP_LEVEL_METRICS_RESET.get();
            default -> 0;
        };
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
