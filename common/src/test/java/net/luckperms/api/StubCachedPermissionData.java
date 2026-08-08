/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package net.luckperms.api;

import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import java.util.Map;

/**
 * Public LuckPerms API stub: per-user cached permission data over a node map.
 * getPermissionData(QueryOptions) must stay public and take QueryOptions (not
 * DefaultQueryOptions) so the service's reflection getMethod matches the interface.
 */
public class StubCachedPermissionData implements CachedPermissionData {

    private final Map<String, Tristate> permissions;

    public StubCachedPermissionData(Map<String, Tristate> permissions) {
        this.permissions = permissions;
    }

    @Override
    public PermissionData getPermissionData() {
        return new StubPermissionData(permissions);
    }

    @Override
    public PermissionData getPermissionData(QueryOptions options) {
        return new StubPermissionData(permissions);
    }
}
