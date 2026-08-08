/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package net.luckperms.api;

import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.util.Tristate;
import java.util.Map;

/**
 * Public LuckPerms API stub for unit tests (see StubUserManager for why the
 * stub classes must be public top-level types).
 */
public class StubUser implements User {

    private final Map<String, Tristate> permissions;

    public StubUser() {
        this(java.util.Collections.emptyMap());
    }

    public StubUser(Map<String, Tristate> permissions) {
        this.permissions = permissions;
    }

    @Override
    public CachedPermissionData getCachedData() {
        return new StubCachedPermissionData(permissions);
    }
}
