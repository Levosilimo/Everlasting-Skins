/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission;

public interface IPermissionService {

    boolean hasPermission(PermissionContext context, String permissionNode);

    String getActiveBackendName();

    int getPriority();
}
