/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission;

import java.util.UUID;

/**
 * Vanilla op-level permission service for the 1.7.10 lane.
 *
 * <p>1.7.10 has no Forge PermissionAPI and no per-player permission-node
 * registry (that surface is 1.8+). The ops model is the vanilla
 * Configuration op-permission-level list; this service maps the shared
 * permission-node strings to the required op level by node suffix,
 * mirroring the sibling lanes' defaults (mc1.12.2 Config.permissionsOpLevel*:
 * mojang/clear/random 0, url/other/metrics 2).
 */
public class VanillaPermissionService implements IPermissionService {

    private static final int BYPASS_COOLDOWN_OP_LEVEL = 2;

    /** Required op level for a node (0 = any player, 2 = op). */
    public static int requiredOpLevel(String permissionNode) {
        switch (permissionNode) {
            case "everlastingskins.command.skin":
            case "everlastingskins.command.skin.clear":
            case "everlastingskins.command.skin.random":
                return 0;
            case "everlastingskins.command.skin.url":
            case "everlastingskins.command.skin.other":
            case "everlastingskins.command.metrics":
            case "everlastingskins.command.metrics.reset":
                return 2;
            default:
                return 0;
        }
    }

    @Override
    public boolean hasPermission(UUID uuid, int opLevel, String permissionNode) {
        if (permissionNode.endsWith(".source")) return true;
        if (permissionNode.endsWith(".bypass.cooldown")) {
            return opLevel >= BYPASS_COOLDOWN_OP_LEVEL;
        }
        return opLevel >= requiredOpLevel(permissionNode);
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
