/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.permission;

public class VanillaPermissionService implements IPermissionService {

    private static final int REQUIRED_OP_LEVEL = 2;

    @Override
    public boolean hasPermission(PermissionContext context, String permissionNode) {
        if (permissionNode.endsWith(".source")) return true;
        return context.isOp();
    }

    @Override
    public String getActiveBackendName() {
        return "Vanilla (op level " + REQUIRED_OP_LEVEL + ")";
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
