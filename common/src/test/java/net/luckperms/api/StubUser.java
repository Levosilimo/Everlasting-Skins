/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package net.luckperms.api;

import net.luckperms.api.cacheddata.CachedPermissionData;

/**
 * Public LuckPerms API stub for unit tests (see StubUserManager for why the
 * stub classes must be public top-level types).
 */
public class StubUser implements User {

    @Override
    public CachedPermissionData getCachedData() {
        return null;
    }
}
