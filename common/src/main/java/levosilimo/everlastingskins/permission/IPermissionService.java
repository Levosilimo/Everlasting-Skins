/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission;

import java.util.UUID;

/**
 * Permission check seam, decoupled from the MC-bound per-version
 * {@code PermissionContext}. The per-version context is exactly a
 * {@code (UUID, opLevel)} pair plus an MC-only factory, so per-version
 * services adapt {@code hasPermission(ctx.uuid(), ctx.opLevel(), node)}.
 */
public interface IPermissionService {

    boolean hasPermission(UUID uuid, int opLevel, String permissionNode);

    String getActiveBackendName();

    int getPriority();
}
