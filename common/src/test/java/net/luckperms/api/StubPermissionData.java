/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package net.luckperms.api;

import net.luckperms.api.util.Tristate;
import java.util.Map;

/**
 * Public LuckPerms API stub: permission-node lookup backed by a map.
 * Must be public top-level (the service reflects getMethod("checkPermission", String.class)
 * on the runtime class across the levosilimo package boundary).
 */
public class StubPermissionData implements PermissionData {

    private final Map<String, Tristate> permissions;

    public StubPermissionData(Map<String, Tristate> permissions) {
        this.permissions = permissions;
    }

    @Override
    public Tristate checkPermission(String node) {
        return permissions.getOrDefault(node, Tristate.UNDEFINED);
    }
}
