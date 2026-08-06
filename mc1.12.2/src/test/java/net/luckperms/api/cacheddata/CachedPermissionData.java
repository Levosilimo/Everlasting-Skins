/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package net.luckperms.api.cacheddata;

import net.luckperms.api.PermissionData;
import net.luckperms.api.query.QueryOptions;

public interface CachedPermissionData {
    PermissionData getPermissionData();
    PermissionData getPermissionData(QueryOptions options);
}
